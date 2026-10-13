package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchivePaths
import com.idanck.nexplorer.core.FileNames
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.file.Files

/**
 * ZIP בלי אנדרואיד (נבדק ב-JVM). [checkpoint] — ביטול/השהיה של תור ההעברות; [onBytes] — התקדמות.
 * אותם כללים כמו בהעתקה: לא דורסים כלום, כותבים לשם זמני ומשנים שם בסוף, לא עוקבים אחרי קישורים.
 */
object ZipArchives {

    data class Info(val entries: Int, val totalBytes: Long, val encrypted: Boolean)

    data class Extracted(val created: List<File>, val skipped: Int)

    class WrongPassword : IOException("סיסמה שגויה")

    fun inspect(archive: File): Info = ZipFile(archive).use { zip ->
        if (!zip.isValidZipFile) throw IOException("זה לא קובץ ZIP תקין")
        val headers = zip.fileHeaders
        Info(headers.count { !it.isDirectory }, headers.sumOf { it.uncompressedSize }, zip.isEncrypted)
    }

    /**
     * [dest] — השם המבוקש; אם בינתיים נוצר שם קובץ, מקבלים שם פנוי (rename דורס בשקט).
     * סיסמה — AES-256; בלי סיסמה — ZIP רגיל שכל מערכת פותחת. מחזיר את הקובץ שנוצר.
     */
    suspend fun compress(
        sources: List<File>,
        dest: File,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ): File {
        val dir = dest.parentFile ?: throw IOException("אין תיקיית יעד")
        val part = File(dir, ".${dest.name}.nexplorer-part")
        try {
            ZipOutputStream(SyncOnClose(FileOutputStream(part)).buffered(BUFFER), password?.takeIf { it.isNotEmpty() }).use { zip ->
                val encrypt = password != null && password.isNotEmpty()
                for (source in sources) add(zip, source, source.name, encrypt, checkpoint, onBytes)
            }
            val final = freeName(dir, dest.name)
            if (!part.renameTo(final)) throw IOException("לא ניתן לשמור את \"${final.name}\"")
            return final
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    /** ‏ZipOutputStream סוגר את הזרם בעצמו; ה-sync חייב לקרות לפני הסגירה, ולכן כאן. */
    private class SyncOnClose(private val file: FileOutputStream) : FilterOutputStream(file) {
        override fun write(b: ByteArray, off: Int, len: Int) = file.write(b, off, len)
        override fun close() {
            file.flush()
            file.fd.sync()
            file.close()
        }
    }

    private fun freeName(dir: File, name: String): File =
        File(dir, FileNames.unique(name) { File(dir, it).exists() })

    private suspend fun add(
        zip: ZipOutputStream,
        file: File,
        name: String,
        encrypt: Boolean,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ) {
        checkpoint()
        if (isLink(file)) return
        if (file.isDirectory) {
            // רשומת תיקייה: בלי תוכן, ולכן גם בלי הצפנה (zip4j דורש את זה).
            zip.putNextEntry(
                ZipParameters().apply {
                    fileNameInZip = "$name/"
                    compressionMethod = CompressionMethod.STORE
                    lastModifiedFileTime = file.lastModified()
                },
            )
            zip.closeEntry()
            val children = file.listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${file.name}\"")
            for (child in children) add(zip, child, "$name/${child.name}", encrypt, checkpoint, onBytes)
            return
        }
        zip.putNextEntry(
            ZipParameters().apply {
                fileNameInZip = name
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                lastModifiedFileTime = file.lastModified()
                entrySize = file.length()
                if (encrypt) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            },
        )
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                checkpoint()
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
                onBytes(read.toLong())
            }
        }
        zip.closeEntry()
    }

    /**
     * מחלץ לתוך [targetDir] (קיימת). רשומה שהנתיב שלה יוצא מהתיקייה (zip slip) — מדולגת ונספרת.
     * קובץ שכבר קיים לא נדרס: מקבל שם פנוי. בכשל או ביטול נמחק מה שנוצר כאן.
     */
    suspend fun extract(
        archive: File,
        targetDir: File,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ): Extracted {
        val root = targetDir.canonicalFile
        val created = mutableListOf<File>()
        var skipped = 0
        try {
            ZipFile(archive, password?.takeIf { it.isNotEmpty() }).use { zip ->
                if (!zip.isValidZipFile) throw IOException("זה לא קובץ ZIP תקין")
                if (zip.isEncrypted && (password == null || password.isEmpty())) throw WrongPassword()
                for (header in zip.fileHeaders) {
                    checkpoint()
                    val relative = ArchivePaths.safeRelative(header.fileName)
                    val out = relative?.let { File(root, it) }
                    if (out == null || !out.canonicalPath.startsWith(root.path + File.separator)) {
                        skipped++
                        continue
                    }
                    if (header.isDirectory) {
                        if (!out.exists()) {
                            if (!out.mkdirs()) throw IOException("לא ניתן ליצור את \"${out.name}\"")
                            created += out
                        }
                        continue
                    }
                    val parent = out.parentFile!!
                    if (!parent.exists()) {
                        // כל רמה שחסרה נרשמת — רוב ה-ZIP-ים בלי רשומות תיקייה, וביטול צריך לנקות את כולן.
                        val missing = generateSequence(parent) { it.parentFile }.takeWhile { !it.exists() }.toList().asReversed()
                        if (!parent.mkdirs()) throw IOException("לא ניתן ליצור את \"${parent.name}\"")
                        created += missing
                    }
                    val part = File(parent, ".${out.name}.nexplorer-part")
                    try {
                        zip.getInputStream(header).use { input ->
                            FileOutputStream(part).use { output ->
                                val buffer = ByteArray(BUFFER)
                                while (true) {
                                    checkpoint()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    onBytes(read.toLong())
                                }
                                output.fd.sync()
                            }
                        }
                        // השם נבדק רק עכשיו, ממש לפני ה-rename: קובץ שנוצר באמצע הכתיבה לא נדרס.
                        val dest = freeName(parent, out.name)
                        if (!part.renameTo(dest)) throw IOException("לא ניתן לשמור את \"${dest.name}\"")
                        created += dest
                        if (header.lastModifiedTimeEpoch > 0) dest.setLastModified(header.lastModifiedTimeEpoch)
                    } finally {
                        part.delete()
                    }
                    // zip4j שומר כל זרם שנפתח (עם החוצצים שלו) עד close — בארכיון של עשרות אלפי רשומות זה
                    // מאות MB. close משחרר, וגישה לרשומה הבאה פותחת מחדש.
                    zip.close()
                }
            }
        } catch (e: Throwable) {
            // מהעמוק לרדוד: קבצים, ואז התיקיות שיצרנו (רק אם התרוקנו). Throwable — גם OutOfMemoryError.
            created.asReversed().forEach { it.delete() }
            throw when {
                e is ZipException && e.type == ZipException.Type.WRONG_PASSWORD -> WrongPassword()
                else -> e
            }
        }
        return Extracted(created, skipped)
    }

    private fun isLink(file: File): Boolean = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private const val BUFFER = 256 * 1024
}
