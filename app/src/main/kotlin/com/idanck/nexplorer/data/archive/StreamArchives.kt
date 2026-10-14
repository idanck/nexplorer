package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchiveFormat
import com.idanck.nexplorer.core.ArchiveFormats
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

/**
 * TAR (גם דחוס) ודחיסה של קובץ יחיד — ב-Commons Compress (דרך [Archives]). אלה זרמים בלי אינדקס:
 * הגודל אחרי חילוץ לא ידוע מראש, ולכן ההתקדמות נמדדת בבתים של הקובץ הדחוס שנקראו.
 */
internal object StreamArchives {

    /** TAR לא דחוס נסרק מהר (רק כותרות; הקפיצה על התוכן היא seek). דחוס — רק הפורמט. */
    fun inspect(archive: File, format: ArchiveFormat): Archives.Info {
        if (format != ArchiveFormat.Tar) {
            // קריאה של תחילת הזרם: קובץ פגום או בפורמט אחר נכשל כאן, לא באמצע העברה.
            open(archive, format) { }.use { it.read() }
            return Archives.Info(format, entries = if (format.single) 1 else null, totalBytes = null, encrypted = false)
        }
        TarArchiveInputStream(BufferedInputStream(archive.inputStream())).use { tar ->
            var files = 0
            var bytes = 0L
            while (true) {
                val entry = tar.nextEntry ?: break
                if (isRegular(entry)) {
                    files++
                    bytes += entry.size
                }
            }
            return Archives.Info(format, files, bytes, encrypted = false)
        }
    }

    suspend fun extract(
        archive: File,
        format: ArchiveFormat,
        target: ExtractTarget,
        checkpoint: suspend () -> Unit,
        onTotal: (Long) -> Unit,
        onBytes: (Long) -> Unit,
    ) {
        onTotal(archive.length())
        open(archive, format, onBytes).use { input ->
            if (format.single) {
                // שם הקובץ — הארכיון בלי הסיומת (notes.txt.gz ← notes.txt).
                val out = target.resolve(ArchiveFormats.baseName(archive.name).ifBlank { "file" }) ?: return
                target.file(out, input, archive.lastModified(), checkpoint, onBytes = null)
                return
            }
            val tar = TarArchiveInputStream(input)
            while (true) {
                checkpoint()
                val entry = tar.nextEntry ?: break
                val out = target.resolve(entry.name) ?: continue
                when {
                    entry.isDirectory -> target.directory(out)
                    // קישורים, התקנים, FIFO, וקובץ דליל בגרסה שאי אפשר לקרוא.
                    !isRegular(entry) || !tar.canReadEntryData(entry) -> target.skipSpecial()
                    else -> target.file(out, tar, entry.modTime.time, checkpoint, onBytes = null)
                }
            }
        }
    }

    /** isFile לבדו נכון גם לקישור: הוא בודק רק שזו לא תיקייה. */
    private fun isRegular(entry: TarArchiveEntry): Boolean =
        entry.isFile && !entry.isSymbolicLink && !entry.isLink && !entry.isCharacterDevice && !entry.isBlockDevice && !entry.isFIFO

    /** הזרם המפוענח; [onBytes] סופר את מה שנקרא מהקובץ עצמו. */
    private fun open(archive: File, format: ArchiveFormat, onBytes: (Long) -> Unit): InputStream {
        val raw = BufferedInputStream(Counting(archive.inputStream(), onBytes), BUFFER)
        return try {
            when (format) {
                ArchiveFormat.Tar -> raw
                ArchiveFormat.TarGz, ArchiveFormat.Gz -> GzipCompressorInputStream(raw, true)
                ArchiveFormat.TarBz2, ArchiveFormat.Bz2 -> BZip2CompressorInputStream(raw, true)
                ArchiveFormat.TarXz, ArchiveFormat.Xz -> XZCompressorInputStream(raw, true, Archives.memoryLimitKb())
                ArchiveFormat.Zip, ArchiveFormat.SevenZ -> throw IllegalArgumentException("$format is not a stream format")
            }
        } catch (e: Throwable) {
            raw.close()
            throw e
        }
    }

    private class Counting(input: InputStream, private val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) onBytes(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) onBytes(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) onBytes(it) }
    }

    private const val BUFFER = 256 * 1024
}
