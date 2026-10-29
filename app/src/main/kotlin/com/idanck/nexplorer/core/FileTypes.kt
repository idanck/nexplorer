package com.idanck.nexplorer.core

/**
 * הסוג שקובע את אריח האייקון (שפת העיצוב 1.10, §2 "אריחי סוגי קבצים"): לכל סוג גוון משלו וסמל משלו, כדי
 * שסוג לא יזוהה לפי צבע בלבד. מפורט יותר מ-[FileKind] (שמשמש למיון ולקיבוץ): מסמך מתפצל ל-PDF, מסמך, גיליון,
 * מצגת וספר, וטקסט — לטקסט ולקוד. בלי תלות באנדרואיד — נבדק ב-JVM.
 */
enum class FileType { Folder, Image, Video, Audio, Pdf, Doc, Ebook, Sheet, Slides, Text, Code, Archive, Apk, Encrypted, Other }

/** תיקייה מוכרת: סמל קטן בתוך אייקון התיקייה ("DCIM" — מצלמה, "Download" — חץ הורדה). */
enum class SpecialFolder { Camera, Pictures, Download, Music, Movies, Documents, Android, Recordings, Vault }

object FileTypes {

    private val SHEETS = setOf("xls", "xlsx", "xlsm", "ods", "csv", "tsv", "numbers")
    private val SLIDES = setOf("ppt", "pptx", "odp", "key")
    private val EBOOKS = setOf("epub", "mobi", "azw3", "fb2")
    private val CODE = setOf(
        "kt", "kts", "java", "js", "ts", "py", "sh", "html", "htm", "css", "gradle", "json", "xml", "yml", "yaml",
        "toml", "c", "cpp", "h", "cs", "go", "rs", "rb", "php", "swift", "sql", "bat", "ps1",
    )

    /** [encrypted] — שם של קובץ מוצפן של האפליקציה (`.enc`); נבדק בחוץ, כי הכלל שייך להצפנה. */
    fun of(entry: Entry, encrypted: Boolean = false): FileType {
        val ext = entry.extension
        return when (entry.kind) {
            FileKind.Folder -> FileType.Folder
            FileKind.Image -> FileType.Image
            FileKind.Video -> FileType.Video
            FileKind.Audio -> FileType.Audio
            FileKind.Document -> when (ext) {
                "pdf" -> FileType.Pdf
                in SHEETS -> FileType.Sheet
                in SLIDES -> FileType.Slides
                in EBOOKS -> FileType.Ebook
                else -> FileType.Doc
            }
            // "ts" ממופה לווידאו ב-[FileKinds] — לכן לא מגיע לכאן.
            FileKind.Text -> when (ext) {
                in SHEETS -> FileType.Sheet
                in CODE -> FileType.Code
                else -> FileType.Text
            }
            FileKind.Archive -> FileType.Archive
            FileKind.Apk -> FileType.Apk
            FileKind.Other -> when {
                encrypted -> FileType.Encrypted
                ext in SHEETS -> FileType.Sheet
                ext in SLIDES -> FileType.Slides
                ext in EBOOKS -> FileType.Ebook
                ext in CODE -> FileType.Code
                else -> FileType.Other
            }
        }
    }

    /** תיקייה מוצפנת — מנעול; אחרת לפי השם (בלי תלות באותיות גדולות), בכל עומק. */
    fun specialFolder(entry: Entry): SpecialFolder? {
        if (!entry.isDirectory) return null
        if (entry.isVault) return SpecialFolder.Vault
        return when (entry.name.lowercase()) {
            "dcim", "camera" -> SpecialFolder.Camera
            "pictures", "screenshots", "images" -> SpecialFolder.Pictures
            "download", "downloads" -> SpecialFolder.Download
            "music", "podcasts", "ringtones", "notifications", "alarms", "audiobooks" -> SpecialFolder.Music
            "movies", "videos" -> SpecialFolder.Movies
            "documents" -> SpecialFolder.Documents
            "android" -> SpecialFolder.Android
            "recordings" -> SpecialFolder.Recordings
            else -> null
        }
    }

    /**
     * הטקסט שבאריח של קובץ מסוג [FileType.Other]: הסיומת באותיות גדולות ("ISO", "BIN") — עד 4 תווים, אותיות
     * וספרות בלבד. אחרת null, והאריח מציג את סמל הקובץ הכללי.
     */
    fun badge(entry: Entry): String? = entry.extension.takeIf { it.length in 1..4 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }?.uppercase()
}
