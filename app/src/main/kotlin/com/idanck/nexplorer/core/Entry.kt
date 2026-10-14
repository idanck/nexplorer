package com.idanck.nexplorer.core

/** פריט אחד ברשימה — קובץ או תיקייה. בלי תלות באנדרואיד, כדי שהמיון והחיפוש ייבדקו ב-JVM. */
data class Entry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    /** בתים. לתיקייה: 0 (הגודל לא מחושב ברישום). */
    val size: Long,
    /** מספר הפריטים בתיקייה, או -1 כשלא ידוע / קובץ. */
    val childCount: Int,
    val lastModified: Long,
    val isHidden: Boolean,
    val kind: FileKind,
    /** "תיקייה מוצפנת" (יש בה קובץ הסימון) — אייקון מנעול, ופתיחה בסיסמה. */
    val isVault: Boolean = false,
    /**
     * קישור סימבולי בשרת SFTP: [isDirectory] ו-[size] — של מה שהוא מצביע עליו (לדפדוף). העברות ומחיקה
     * לא נכנסות דרכו — מחיקה מוחקת את הקישור בלבד. במכשיר הקישורים מזוהים אחרת (`LocalFiles.isLink`).
     */
    val isLink: Boolean = false,
) {
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

/** סוג תצוגתי — קובע אריח, צבע וקיבוץ במיון לפי סוג. */
enum class FileKind(val label: String) {
    Folder("תיקיות"),
    Image("תמונות"),
    Video("וידאו"),
    Audio("מוזיקה ושמע"),
    Document("מסמכים"),
    Text("טקסט וקוד"),
    Archive("ארכיונים"),
    Apk("אפליקציות"),
    Other("אחר"),
}

object FileKinds {
    private val byExtension: Map<String, FileKind> = buildMap {
        listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif", "dng", "raw")
            .forEach { put(it, FileKind.Image) }
        listOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "wmv", "flv")
            .forEach { put(it, FileKind.Video) }
        listOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr", "mid", "midi")
            .forEach { put(it, FileKind.Audio) }
        listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub")
            .forEach { put(it, FileKind.Document) }
        listOf(
            "txt", "md", "csv", "json", "xml", "yml", "yaml", "log", "cfg", "conf", "ini", "toml",
            "kt", "kts", "java", "js", "py", "sh", "html", "css", "properties", "gradle",
        ).forEach { put(it, FileKind.Text) }
        listOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "tbz", "xz", "txz", "zst")
            .forEach { put(it, FileKind.Archive) }
        put("apk", FileKind.Apk)
        put("apks", FileKind.Apk)
        put("xapk", FileKind.Apk)
    }

    fun of(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.Folder
        // "ts" ממופה לווידאו (MPEG-TS) ולא ל-TypeScript: בטלפון זה כמעט תמיד הקלטה.
        return byExtension[name.substringAfterLast('.', "").lowercase()] ?: FileKind.Other
    }
}
