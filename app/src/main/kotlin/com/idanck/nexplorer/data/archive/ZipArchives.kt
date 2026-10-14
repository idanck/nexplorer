package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchiveFormat
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
 * ZIP ב-zip4j (דרך [Archives]). [checkpoint] — ביטול/השהיה של תור ההעברות; [onBytes] — התקדמות.
 * אותם כללים כמו בהעתקה: לא דורסים כלום, כותבים לשם זמני ומשנים שם בסוף, לא עוקבים אחרי קישורים.
 */
internal object ZipArchives {

    fun inspect(archive: File): Archives.Info = ZipFile(archive).use { zip ->
        if (!zip.isValidZipFile) throw IOException("זה לא קובץ ZIP תקין")
        val headers = zip.fileHeaders
        Archives.Info(ArchiveFormat.Zip, headers.count { !it.isDirectory }, headers.sumOf { it.uncompressedSize }, zip.isEncrypted)
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

    /** רשומה מוצפנת בלי סיסמה — [Archives.WrongPassword]; הסיסמה עצמה נבדקת בקריאה הראשונה. */
    suspend fun extract(
        archive: File,
        target: ExtractTarget,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onTotal: (Long) -> Unit,
        onBytes: (Long) -> Unit,
    ) {
        try {
            ZipFile(archive, password?.takeIf { it.isNotEmpty() }).use { zip ->
                if (!zip.isValidZipFile) throw IOException("זה לא קובץ ZIP תקין")
                if (zip.isEncrypted && (password == null || password.isEmpty())) throw Archives.WrongPassword()
                onTotal(zip.fileHeaders.sumOf { it.uncompressedSize })
                for (header in zip.fileHeaders) {
                    checkpoint()
                    val out = target.resolve(header.fileName) ?: continue
                    if (header.isDirectory) {
                        target.directory(out)
                        continue
                    }
                    zip.getInputStream(header).use { input ->
                        target.file(out, input, header.lastModifiedTimeEpoch, checkpoint, onBytes)
                    }
                    // zip4j שומר כל זרם שנפתח (עם החוצצים שלו) עד close — בארכיון של עשרות אלפי רשומות זה
                    // מאות MB. close משחרר, וגישה לרשומה הבאה פותחת מחדש.
                    zip.close()
                }
            }
        } catch (e: ZipException) {
            throw if (e.type == ZipException.Type.WRONG_PASSWORD) Archives.WrongPassword() else e
        }
    }

    private fun isLink(file: File): Boolean = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private const val BUFFER = 256 * 1024
}
