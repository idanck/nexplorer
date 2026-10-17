package com.idanck.nexplorer.core

/**
 * השמות של פריטים ב-Google Drive כמו שהם מוצגים ובנתיב (`gdrive://<חיבור>/...`). ב-Drive פריט מזוהה לפי ID:
 * באותה תיקייה יכולים להיות שני קבצים באותו שם, ושם יכול להכיל /. כאן כל פריט מקבל שם אחד, קבוע וייחודי
 * בתיקייה שלו: / מוצג כ-∕ (U+2215), ו-Google Docs — עם הסיומת של הקובץ שנוצר מהם בייצוא. כפולים — לפי סדר
 * היצירה: הראשון בשמו, והבאים כמו [FileNames.unique] ("דוח (1).pdf"). בלי תלות באנדרואיד — נבדק ב-JVM.
 */
object DriveNames {

    const val FOLDER = "application/vnd.google-apps.folder"
    const val SHORTCUT = "application/vnd.google-apps.shortcut"
    private const val GOOGLE_PREFIX = "application/vnd.google-apps."

    /** קובץ של Google Docs: הסוג שמבקשים בייצוא, והסיומת שלו. */
    data class Export(val mimeType: String, val extension: String)

    /** מסמכים, גיליונות, מצגות ושרטוטים. השאר (Forms, Sites, Maps, Apps Script) — אין קובץ לייצא. */
    private val EXPORTS = mapOf(
        "application/vnd.google-apps.document" to Export("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
        "application/vnd.google-apps.spreadsheet" to Export("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        "application/vnd.google-apps.presentation" to Export("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
        "application/vnd.google-apps.drawing" to Export("image/png", "png"),
    )

    /** null — קובץ רגיל (או תיקייה). */
    fun exportOf(mimeType: String): Export? = EXPORTS[mimeType]

    /** מה שמוצג ברשימה: קבצים, תיקיות, קיצורי דרך ו-Google Docs שאפשר לייצא. */
    fun visible(mimeType: String): Boolean = !mimeType.startsWith(GOOGLE_PREFIX) || mimeType == FOLDER || mimeType == SHORTCUT || mimeType in EXPORTS

    /** השם לפני הטיפול בכפולים. */
    fun baseName(driveName: String, mimeType: String): String =
        driveName.replace('/', SLASH) + (exportOf(mimeType)?.let { ".${it.extension}" } ?: "")

    /**
     * השם ב-Drive לשם שהמשתמש בחר (שינוי שם): ב-Google Docs בלי הסיומת של הייצוא — "דוח.docx" ← "דוח",
     * כדי שהשם ב-Drive לא יקבל ".docx" כפול.
     */
    fun driveNameFor(name: String, mimeType: String): String {
        val export = exportOf(mimeType) ?: return name
        return name.removeSuffix(".${export.extension}").ifEmpty { name }
    }

    /**
     * השמות של כל הפריטים בתיקייה אחת, באותו סדר כמו [items]. [created] — זמן היצירה; שוויון — לפי [id].
     * שם שכבר תפוס (גם ע"י פריט אחר ששמו כבר "דוח (1).pdf") לא ניתן פעמיים.
     */
    fun <T> assign(items: List<T>, driveName: (T) -> String, mimeType: (T) -> String, created: (T) -> Long, id: (T) -> String): List<String> {
        val bases = items.map { baseName(driveName(it), mimeType(it)) }
        val taken = bases.toHashSet()
        val names = arrayOfNulls<String>(items.size)
        items.indices.groupBy { bases[it] }.forEach { (base, indices) ->
            val ordered = indices.sortedWith(compareBy<Int> { created(items[it]) }.thenBy { id(items[it]) })
            names[ordered.first()] = base
            ordered.drop(1).forEach { index ->
                val name = FileNames.unique(base) { it in taken }
                taken += name
                names[index] = name
            }
        }
        return names.map { it!! }
    }

    /** ∕ — נראה כמו / ואינו מפריד בנתיב. */
    const val SLASH = '\u2215'
}
