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

    /** שם ה-ZIP כברירת מחדל: הפריט היחיד, או שם התיקייה שבה הם נמצאים. */
    fun defaultZipName(names: List<String>, folderName: String): String {
        val base = names.singleOrNull()?.let { name ->
            val dot = name.lastIndexOf('.')
            if (dot > 0) name.substring(0, dot) else name
        } ?: folderName.ifBlank { "archive" }
        return "$base.zip"
    }

    /** תיקיית החילוץ: שם הארכיון בלי `.zip`. */
    fun extractFolderName(archiveName: String): String {
        val dot = archiveName.lastIndexOf('.')
        return (if (dot > 0) archiveName.substring(0, dot) else archiveName).ifBlank { "archive" }
    }

    private val DRIVE = Regex("^[A-Za-z]:$")
}
