package com.idanck.nexplorer.core

object FileNames {
    /** null = תקין; אחרת הודעת השגיאה שמוצגת מתחת לשדה. */
    fun validate(name: String): String? = when {
        name.isBlank() -> "צריך שם"
        name == "." || name == ".." -> "שם שמור במערכת"
        name.any { it == '/' || it == '\u0000' } -> "השם לא יכול להכיל /"
        // ירידת שורה או טאב בשם שוברים את רשימת המועדפים בהגדרות, ואין להם ייצוג בשורה.
        name.any { it.isISOControl() } -> "השם לא יכול להכיל תווי בקרה"
        name.toByteArray(Charsets.UTF_8).size > 255 -> "השם ארוך מדי"
        else -> null
    }

    /**
     * שם פנוי בתיקייה: "report.pdf" → "report (1).pdf" → "report (2).pdf".
     * העתקה לא דורסת אף פעם — זה ההבטחה של "אדום = בלתי הפיך": שום דבר לא נמחק בלי שאלה.
     */
    fun unique(desired: String, taken: (String) -> Boolean): String {
        if (!taken(desired)) return desired
        val dot = desired.lastIndexOf('.')
        val (base, ext) = if (dot > 0) desired.substring(0, dot) to desired.substring(dot) else desired to ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (!taken(candidate)) return candidate
            n++
        }
    }

    /** האם [child] נמצא בתוך [parent] (או זהה לו) — חוסם העתקת תיקייה לתוך עצמה. */
    fun isInside(child: String, parent: String): Boolean {
        val p = parent.trimEnd('/')
        return child == p || child.startsWith("$p/")
    }
}
