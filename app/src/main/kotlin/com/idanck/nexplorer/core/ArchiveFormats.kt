package com.idanck.nexplorer.core

/**
 * ארכיונים שהאפליקציה פותחת. [single] — דחיסה של קובץ אחד (GZ/BZ2/XZ בלי TAR): החילוץ יוצר קובץ
 * אחד בשם הארכיון בלי הסיומת. [creatable] — אפשר גם ליצור ("הוסף לארכיון").
 * RAR לא כאן: אין ספריית Java פתוחה שקוראת RAR5 (ראו docs/plan/03-phase3.md).
 */
enum class ArchiveFormat(
    val label: String,
    val extensions: List<String>,
    val single: Boolean = false,
    val creatable: Boolean = false,
) {
    Zip("ZIP", listOf("zip"), creatable = true),
    SevenZ("7z", listOf("7z"), creatable = true),
    Tar("TAR", listOf("tar")),
    TarGz("TAR.GZ", listOf("tar.gz", "tgz")),
    TarBz2("TAR.BZ2", listOf("tar.bz2", "tbz2", "tbz")),
    TarXz("TAR.XZ", listOf("tar.xz", "txz")),
    Gz("GZ", listOf("gz"), single = true),
    Bz2("BZ2", listOf("bz2"), single = true),
    Xz("XZ", listOf("xz"), single = true),
    ;

    /** הסיומת שנותנים לארכיון חדש. */
    val extension: String get() = extensions.first()
}

object ArchiveFormats {

    /** הסיומת הארוכה קודם: `a.tar.gz` הוא TAR.GZ ולא GZ. */
    private val bySuffix: List<Pair<String, ArchiveFormat>> =
        ArchiveFormat.entries.flatMap { format -> format.extensions.map { ".$it" to format } }.sortedByDescending { it.first.length }

    /** לפי השם בלבד; null = לא ארכיון שנפתח כאן (גם `.zip` לבד, בלי שם לפני הנקודה). */
    fun of(name: String): ArchiveFormat? = match(name)?.second

    /** השם בלי סיומת הארכיון: `a.tar.gz` ← `a`, `notes.txt.gz` ← `notes.txt`. שם שאינו ארכיון — בלי הסיומת האחרונה. */
    fun baseName(name: String): String {
        match(name)?.let { (suffix, _) -> return name.dropLast(suffix.length) }
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** [name] עם הסיומת של [format] במקום סיומת ארכיון אחרת שכבר יש לו (`a.zip` ← `a.7z`). */
    fun withExtension(name: String, format: ArchiveFormat): String {
        val trimmed = name.trim()
        if (of(trimmed) == format) return trimmed
        val base = if (of(trimmed) != null) baseName(trimmed) else trimmed
        return "$base.${format.extension}"
    }

    private fun match(name: String): Pair<String, ArchiveFormat>? {
        val lower = name.lowercase()
        return bySuffix.firstOrNull { (suffix, _) -> lower.length > suffix.length && lower.endsWith(suffix) }
    }
}
