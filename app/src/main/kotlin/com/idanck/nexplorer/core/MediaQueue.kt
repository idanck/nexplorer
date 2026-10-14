package com.idanck.nexplorer.core

/**
 * רשימת ההשמעה של נגן: כל הקבצים מאותו סוג בתיקייה שהנגן מנגן, באותו מיון כמו בחלון הקבצים, והמיקום
 * של הקובץ שנפתח. קובץ שלא ברשימה (מוסתר, כש"הצג מוסתרים" כבוי) נכנס ראשון.
 */
data class MediaQueue(val items: List<Entry>, val start: Int) {

    val current: Entry get() = items[start]

    companion object {

        /** [sorted] — התיקייה כפי שהיא מוצגת; [opened] — הקובץ שנפתח. */
        fun of(sorted: List<Entry>, opened: Entry): MediaQueue {
            val sameKind = sorted.filter { it.kind == opened.kind && MediaFormats.playable(it) }
            val index = sameKind.indexOfFirst { it.path == opened.path }
            return if (index >= 0) MediaQueue(sameKind, index) else MediaQueue(listOf(opened) + sameKind, 0)
        }
    }
}
