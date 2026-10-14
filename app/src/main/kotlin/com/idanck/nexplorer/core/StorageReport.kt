package com.idanck.nexplorer.core

/** פילוח התפוסה במנתח האחסון (לוח Analyzer). הסדר הוא סדר הצבעים בפס. */
enum class UsageCategory(val label: String) {
    Video("וידאו"),
    Images("תמונות"),
    /** כולל את מה שתפוס ולא נסרק (מערכת, נתוני אפליקציות). התווית כמו בלוח. */
    Apps("אפליקציות"),
    Documents("מסמכים"),
    Audio("אודיו"),
    Other("אחר"),
}

object StorageReport {

    fun categoryOf(kind: FileKind): UsageCategory = when (kind) {
        FileKind.Video -> UsageCategory.Video
        FileKind.Image -> UsageCategory.Images
        FileKind.Audio -> UsageCategory.Audio
        FileKind.Document, FileKind.Text -> UsageCategory.Documents
        FileKind.Apk -> UsageCategory.Apps
        FileKind.Archive, FileKind.Other, FileKind.Folder -> UsageCategory.Other
    }

    /**
     * "זבל ומטמון": קבצים זמניים והורדות שלא הושלמו, ותיקיות מטמון/תמונות ממוזערות שהמערכת
     * והאפליקציות בונות מחדש. לא נמחק כלום אוטומטית — זו רשימה שהמשתמש בוחר ממנה.
     */
    fun isJunkFile(name: String): Boolean = JUNK_SUFFIXES.any { name.endsWith(it, ignoreCase = true) }

    /**
     * [relative] — נתיב יחסי לשורש הנפח. `.thumbnails` בכל מקום (המערכת בונה אותו מחדש);
     * `cache` רק במקום שבו הוא בוודאות של אפליקציה (`Android/media/<חבילה>/cache`) — תיקייה בשם הזה
     * בתוך פרויקט של המשתמש היא לא זבל.
     */
    fun isJunkFolder(relative: String): Boolean {
        val parts = relative.trim('/').split('/')
        val name = parts.lastOrNull() ?: return false
        if (name.equals(".thumbnails", ignoreCase = true)) return true
        return parts.size == 4 && parts[0] == "Android" && parts[1] == "media" &&
            (name.equals("cache", ignoreCase = true) || name.equals(".cache", ignoreCase = true))
    }

    /** מועמדים לכפילות: אותו גודל בדיוק, מעל [minBytes]. רק להם מחשבים hash. */
    fun <T> sameSizeGroups(items: List<T>, sizeOf: (T) -> Long, minBytes: Long = DUPLICATE_MIN_BYTES): List<List<T>> =
        items.asSequence()
            .filter { sizeOf(it) >= minBytes }
            .groupBy(sizeOf)
            .values
            .filter { it.size > 1 }
            .sortedByDescending { group -> sizeOf(group.first()) * (group.size - 1) }

    /** כמה מתפנה אם משאירים עותק אחד מכל קבוצה. */
    fun <T> reclaimable(groups: List<List<T>>, sizeOf: (T) -> Long): Long =
        groups.sumOf { group -> sizeOf(group.first()) * (group.size - 1) }

    const val DUPLICATE_MIN_BYTES: Long = 64 * 1024
    const val LARGE_FILES = 20
    const val BIG_FOLDERS = 5

    private val JUNK_SUFFIXES = listOf(".tmp", ".temp", ".crdownload", ".part", ".partial", ".nexplorer-part")
}
