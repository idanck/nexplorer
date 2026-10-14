package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchiveFormat
import com.idanck.nexplorer.core.ArchiveFormats
import java.io.File
import java.io.IOException

/**
 * כל הארכיונים מאחורי ממשק אחד (נבדק ב-JVM): ZIP ב-zip4j, ו-7z, TAR ודחיסת קובץ יחיד ב-Commons Compress.
 * הפורמט נקבע לפי השם. [checkpoint] — ביטול/השהיה של תור ההעברות; [onBytes] — התקדמות.
 */
object Archives {

    /**
     * [entries] ו-[totalBytes] — null כשאי אפשר לדעת בלי לפתוח את כל הדחיסה (TAR.GZ ודומיו), או בלי
     * סיסמה (7z שגם רשימת הקבצים בו מוצפנת).
     */
    data class Info(val format: ArchiveFormat, val entries: Int?, val totalBytes: Long?, val encrypted: Boolean)

    /** [unsafe] — נתיב שיצא מתיקיית החילוץ; [special] — קישורים והתקנים, שלא נוצרים. */
    data class Extracted(val created: List<File>, val unsafe: Int, val special: Int)

    /** חסרה סיסמה, או שהיא שגויה. */
    class WrongPassword : IOException("סיסמה שגויה")

    fun formatOf(file: File): ArchiveFormat =
        ArchiveFormats.of(file.name) ?: throw IOException("\"${file.name}\" הוא לא ארכיון שאפשר לפתוח כאן")

    fun inspect(archive: File): Info = when (val format = formatOf(archive)) {
        ArchiveFormat.Zip -> ZipArchives.inspect(archive)
        ArchiveFormat.SevenZ -> SevenZipArchives.inspect(archive)
        else -> StreamArchives.inspect(archive, format)
    }

    /**
     * מחלץ לתוך [targetDir] (קיימת). [onTotal] — כמה בתים [onBytes] יספור בסך הכל: בגודל אחרי חילוץ
     * ב-ZIP ו-7z, ובגודל הקובץ הדחוס בשאר (שם הגודל המקורי לא ידוע מראש).
     */
    suspend fun extract(
        archive: File,
        targetDir: File,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onTotal: (Long) -> Unit,
        onBytes: (Long) -> Unit,
    ): Extracted {
        val target = ExtractTarget(targetDir)
        try {
            when (val format = formatOf(archive)) {
                ArchiveFormat.Zip -> ZipArchives.extract(archive, target, password, checkpoint, onTotal, onBytes)
                ArchiveFormat.SevenZ -> SevenZipArchives.extract(archive, target, password, checkpoint, onTotal, onBytes)
                else -> StreamArchives.extract(archive, format, target, checkpoint, onTotal, onBytes)
            }
        } catch (e: Throwable) {
            // Throwable — גם OutOfMemoryError ו-CancellationException: לא משאירים חצי חילוץ.
            target.rollback()
            throw e
        }
        return target.result()
    }

    /**
     * [dest] — השם המבוקש, והסיומת שלו קובעת את הפורמט; אם בינתיים נוצר שם קובץ, מקבלים שם פנוי.
     * סיסמה — AES-256 (ZIP ו-7z). מחזיר את הקובץ שנוצר.
     */
    suspend fun compress(
        sources: List<File>,
        dest: File,
        password: CharArray?,
        checkpoint: suspend () -> Unit,
        onBytes: (Long) -> Unit,
    ): File = when (val format = formatOf(dest)) {
        ArchiveFormat.Zip -> ZipArchives.compress(sources, dest, password, checkpoint, onBytes)
        ArchiveFormat.SevenZ -> SevenZipArchives.compress(sources, dest, password, checkpoint, onBytes)
        else -> throw IOException("אי אפשר ליצור ארכיון ${format.label}")
    }

    /** תקרת זיכרון לפענוח (מילון LZMA, XZ): ארכיון שדורש יותר — שגיאה במקום קריסה של כל האפליקציה. */
    internal fun memoryLimitKb(): Int = (Runtime.getRuntime().maxMemory() / 2 / 1024).coerceIn(16_384L, Int.MAX_VALUE.toLong()).toInt()
}
