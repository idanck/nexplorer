package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchiveFormat
import com.idanck.nexplorer.core.FileNames
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.tukaani.xz.LZMA2Options
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** 7z ב-Commons Compress (דרך [Archives]): קריאה כולל AES-256, ויצירה עם LZMA2 ואותה הצפנה. */
internal object SevenZipArchives {

    fun inspect(archive: File): Archives.Info = try {
        open(archive, null).use { zip ->
            val files = zip.entries.filter { !it.isDirectory && !it.isAntiItem }
            // שיטות הדחיסה (וההצפנה) של רשומה נטענות רק כשמגיעים אליה; מעבר בלי קריאה לא מפענח כלום.
            var encrypted = false
            while (!encrypted) {
                val entry = zip.nextEntry ?: break
                encrypted = isEncrypted(entry)
            }
            Archives.Info(ArchiveFormat.SevenZ, files.size, files.sumOf { it.size }, encrypted)
        }
    } catch (e: PasswordRequiredException) {
        // גם רשימת הקבצים מוצפנת: בלי סיסמה לא יודעים מה יש בפנים.
        Archives.Info(ArchiveFormat.SevenZ, null, null, encrypted = true)
    }

    suspend fun extract(
        archive: File,
        target: ExtractTarget,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onTotal: (Long) -> Unit,
        onBytes: (Long) -> Unit,
    ) {
        val pass = password?.takeIf { it.isNotEmpty() }
        // עד שהכותרת נפתחת: כשגם רשימת הקבצים מוצפנת, סיסמה שגויה נכשלת כבר בפתיחה.
        var encrypted = pass != null
        try {
            open(archive, pass).use { zip ->
                encrypted = false
                onTotal(zip.entries.filter { !it.isDirectory && !it.isAntiItem }.sumOf { it.size })
                val current = object : InputStream() {
                    override fun read(): Int = zip.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int = zip.read(b, off, len)
                }
                while (true) {
                    checkpoint()
                    val entry = zip.nextEntry ?: break
                    if (isEncrypted(entry)) {
                        encrypted = true
                        if (pass == null) throw Archives.WrongPassword()
                    }
                    // anti-item = "הקובץ נמחק" בארכיון מצטבר — אין מה לחלץ.
                    if (entry.isAntiItem) continue
                    val out = target.resolve(entry.name) ?: continue
                    when {
                        entry.isDirectory -> target.directory(out)
                        isLink(entry) -> target.skipSpecial()
                        else -> target.file(out, current, if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0, checkpoint, onBytes)
                    }
                }
            }
        } catch (e: PasswordRequiredException) {
            throw Archives.WrongPassword()
        } catch (e: IOException) {
            // סיסמה שגויה לא מזוהה בפתיחה: הפענוח מחזיר זבל, ו-LZMA או ה-CRC נכשלים. בארכיון לא מוצפן
            // אותה שגיאה היא קובץ פגום, ומדווחת כמו שהיא.
            if (encrypted && e !is Archives.WrongPassword && isDecodeFailure(e)) throw Archives.WrongPassword()
            throw e
        }
    }

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
            val channel = FileChannel.open(part.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            val pass = password?.takeIf { it.isNotEmpty() }
            val output = try {
                if (pass != null) SevenZOutputFile(channel, pass) else SevenZOutputFile(channel)
            } catch (e: Throwable) {
                channel.close()
                throw e
            }
            output.use { zip ->
                zip.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(PRESET))))
                for (source in sources) add(zip, source, source.name, checkpoint, onBytes)
                zip.finish()
                channel.force(true)
            }
            val final = File(dir, FileNames.unique(dest.name) { File(dir, it).exists() })
            if (!part.renameTo(final)) throw IOException("לא ניתן לשמור את \"${final.name}\"")
            return final
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    private suspend fun add(zip: SevenZOutputFile, file: File, name: String, checkpoint: suspend () -> Unit, onBytes: (Long) -> Unit) {
        checkpoint()
        if (runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)) return
        val entry = zip.createArchiveEntry(file, name)
        zip.putArchiveEntry(entry)
        if (file.isDirectory) {
            zip.closeArchiveEntry()
            val children = file.listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${file.name}\"")
            for (child in children) add(zip, child, "$name/${child.name}", checkpoint, onBytes)
            return
        }
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
        zip.closeArchiveEntry()
    }

    private fun open(archive: File, password: CharArray?): SevenZFile =
        SevenZFile.builder()
            .setFile(archive)
            .apply { if (password != null) setPassword(password) }
            .setMaxMemoryLimitKiB(Archives.memoryLimitKb())
            // 7z של קובץ יחיד (למשל מצינור) בלי שם — מקבל את שם הארכיון.
            .setUseDefaultNameForUnnamedEntries(true)
            .get()

    /** רק אחרי nextEntry — ב-entries השדה הזה ריק. */
    private fun isEncrypted(entry: SevenZArchiveEntry): Boolean =
        entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true

    /** 7z שנוצר ב-Unix שומר קישור כקובץ שהתוכן שלו הוא היעד, ומסמן אותו בביטים העליונים של התכונות. */
    private fun isLink(entry: SevenZArchiveEntry): Boolean {
        if (!entry.hasWindowsAttributes) return false
        val attributes = entry.windowsAttributes
        return attributes and UNIX_EXTENSION != 0 && (attributes ushr 16) and S_IFMT == S_IFLNK
    }

    private fun isDecodeFailure(e: IOException): Boolean =
        e is org.tukaani.xz.CorruptedInputException ||
            e is org.tukaani.xz.UnsupportedOptionsException ||
            e.message?.let { m -> DECODE_MESSAGES.any { m.contains(it, ignoreCase = true) } } == true

    private val DECODE_MESSAGES = listOf("checksum", "crc", "corrupt", "broken", "bad 7z", "password")

    /** פשרה בין גודל לזיכרון: מילון 4 MB, כ-32 MB בדחיסה. preset 6 של 7-Zip דורש פי שלושה. */
    private const val PRESET = 3
    private const val BUFFER = 256 * 1024
    private const val UNIX_EXTENSION = 0x8000
    private const val S_IFMT = 0xF000
    private const val S_IFLNK = 0xA000
}
