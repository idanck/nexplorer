package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchivePaths
import com.idanck.nexplorer.core.FileNames
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * היעד של חילוץ — אותם כללים לכל הפורמטים: רשומה שהנתיב שלה יוצא מהתיקייה (zip slip) מדולגת
 * ונספרת, קובץ קיים לא נדרס (שם פנוי), כותבים לשם זמני ומשנים שם בסוף. [rollback] מוחק את מה
 * שנוצר כאן — בכשל או בביטול.
 */
internal class ExtractTarget(targetDir: File) {

    private val root = targetDir.canonicalFile
    private val created = mutableListOf<File>()
    private var unsafe = 0
    private var special = 0

    /** הקובץ שהרשומה תיכתב אליו; null = הנתיב יוצא מהתיקייה, והרשומה מדולגת. */
    fun resolve(entryName: String): File? {
        val out = ArchivePaths.safeRelative(entryName)?.let { File(root, it) }
        if (out == null || !out.canonicalPath.startsWith(root.path + File.separator)) {
            unsafe++
            return null
        }
        return out
    }

    /**
     * קישור (סימבולי או קשיח), התקן או FIFO בארכיון: לא נוצר — כמו בהעתקה, לא עוקבים אחרי קישורים,
     * ולשאר אין מקום באחסון של הטלפון.
     */
    fun skipSpecial() {
        special++
    }

    fun directory(out: File) {
        if (out.isDirectory) return
        val missing = missingLevels(out)
        if (!out.mkdirs()) throw IOException("לא ניתן ליצור את \"${out.name}\"")
        created += missing
    }

    suspend fun file(
        out: File,
        input: InputStream,
        modified: Long,
        checkpoint: suspend () -> Unit,
        onBytes: ((Long) -> Unit)?,
    ): File {
        val parent = out.parentFile ?: throw IOException("אין תיקיית יעד")
        if (!parent.isDirectory) {
            // כל רמה שחסרה נרשמת — ברוב הארכיונים אין רשומות תיקייה, וביטול צריך לנקות את כולן.
            val missing = missingLevels(parent)
            if (!parent.mkdirs()) throw IOException("לא ניתן ליצור את \"${parent.name}\"")
            created += missing
        }
        val part = File(parent, ".${out.name}.nexplorer-part")
        try {
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    checkpoint()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    onBytes?.invoke(read.toLong())
                }
                output.fd.sync()
            }
            // השם נבדק רק עכשיו, ממש לפני ה-rename: קובץ שנוצר באמצע הכתיבה לא נדרס.
            val dest = File(parent, FileNames.unique(out.name) { File(parent, it).exists() })
            if (!part.renameTo(dest)) throw IOException("לא ניתן לשמור את \"${dest.name}\"")
            created += dest
            if (modified > 0) dest.setLastModified(modified)
            return dest
        } finally {
            part.delete()
        }
    }

    fun result(): Archives.Extracted = Archives.Extracted(created.toList(), unsafe, special)

    /** מהעמוק לרדוד: קבצים, ואז התיקיות שיצרנו (נמחקות רק אם התרוקנו). */
    fun rollback() {
        created.asReversed().forEach { it.delete() }
    }

    private fun missingLevels(dir: File): List<File> =
        generateSequence(dir) { it.parentFile }.takeWhile { !it.exists() }.toList().asReversed()

    private companion object {
        const val BUFFER = 256 * 1024
    }
}
