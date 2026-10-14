package com.idanck.nexplorer.core

enum class ViewMode(val label: String) {
    List("רשימה"),
    Grid("רשת"),
    Gallery("גלריה"),
    Compact("קומפקטי"),
}

enum class SortKey(val label: String, val defaultDescending: Boolean) {
    Name("שם", false),
    Date("תאריך", true),
    Size("גודל", true),
    Type("סוג", false),
}

data class SortOrder(val key: SortKey, val descending: Boolean) {
    companion object {
        fun defaultFor(key: SortKey) = SortOrder(key, key.defaultDescending)
    }
}

/**
 * אפשרויות התצוגה מחלון "אפשרויות תצוגה". נשמרות גלובלית, או לתיקייה אחת ("רק בתיקייה זו").
 * [density]: 0 מרווח · 1 רגיל · 2 צפוף.
 */
data class ViewOptions(
    val mode: ViewMode = ViewMode.List,
    val sort: SortOrder = SortOrder.defaultFor(SortKey.Date),
    val density: Int = 1,
    val showSections: Boolean = true,
    val showHidden: Boolean = false,
    /** "גודל תיקיות": חישוב הגודל של כל תיקייה ברשימה (ברקע — מאט תיקיות גדולות). */
    val showFolderSizes: Boolean = false,
) {
    /** מחרוזת קומפקטית לשמירה בהעדפות. סדר השדות קבוע; שדה חדש נוסף רק בסוף. */
    fun encode(): String = listOf(
        mode.name,
        sort.key.name,
        if (sort.descending) "1" else "0",
        density.toString(),
        if (showSections) "1" else "0",
        if (showHidden) "1" else "0",
        if (showFolderSizes) "1" else "0",
    ).joinToString("|")

    companion object {
        const val MAX_DENSITY = 2

        /** ערך פגום או ישן לא מפיל את האפליקציה — כל שדה שלא נקרא חוזר לברירת המחדל. */
        fun decode(raw: String?): ViewOptions {
            if (raw.isNullOrBlank()) return ViewOptions()
            val parts = raw.split('|')
            val defaults = ViewOptions()
            fun flag(i: Int, fallback: Boolean) = parts.getOrNull(i)?.let { it == "1" } ?: fallback
            val key = parts.getOrNull(1)?.let { name -> SortKey.entries.firstOrNull { it.name == name } }
                ?: defaults.sort.key
            return ViewOptions(
                mode = parts.getOrNull(0)?.let { name -> ViewMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.mode,
                sort = SortOrder(key, flag(2, key.defaultDescending)),
                density = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(0, MAX_DENSITY) ?: defaults.density,
                showSections = flag(4, defaults.showSections),
                showHidden = flag(5, defaults.showHidden),
                showFolderSizes = flag(6, defaults.showFolderSizes),
            )
        }
    }
}
