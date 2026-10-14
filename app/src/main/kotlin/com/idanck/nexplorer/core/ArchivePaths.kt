package com.idanck.nexplorer.core

/**
 * שמות בתוך ארכיון הם קלט זר: `../../Android/data/x` או `/sdcard/DCIM/a.jpg` היו כותבים מחוץ
 * לתיקיית החילוץ ("zip slip"). כאן הם הופכים לנתיב יחסי נקי, או נדחים.
 */
object ArchivePaths {

    /** null = הרשומה נדחית. תיקייה מסתיימת ב-`/` בארכיון; כאן היא בלי. */
    fun safeRelative(entryName: String): String? {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/") || DRIVE.matches(normalized.substringBefore('/'))) return null
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." || it.any { c -> c == '\u0000' } }) return null
        return parts.joinToString("/")
    }

    /** שם הארכיון כברירת מחדל: הפריט היחיד, או שם התיקייה שבה הם נמצאים. */
    fun defaultArchiveName(names: List<String>, folderName: String, format: ArchiveFormat): String {
        val base = names.singleOrNull()?.let { name ->
            val dot = name.lastIndexOf('.')
            if (dot > 0) name.substring(0, dot) else name
        } ?: folderName.ifBlank { "archive" }
        return "$base.${format.extension}"
    }

    /** תיקיית החילוץ: שם הארכיון בלי הסיומת שלו (`a.tar.gz` ← `a`). */
    fun extractFolderName(archiveName: String): String = ArchiveFormats.baseName(archiveName).ifBlank { "archive" }

    private val DRIVE = Regex("^[A-Za-z]:$")
}
