package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** מקום פנוי וגודל, כשהשרת מדווח (ב-SMB — לכל שיתוף). */
data class RemoteSpace(val free: Long, val total: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val usedFraction: Float get() = if (total <= 0) 0f else used.toFloat() / total
}

/**
 * מערכת קבצים של חיבור אחד ברשת. הנתיבים מלאים (`smb://<id>/...`, ראה [com.idanck.nexplorer.core.NetPaths]).
 * כל הקריאות חוסמות — רצות ב-Dispatchers.IO. כל כשל הוא IOException עם הודעה בעברית.
 * כאן אין תלות באנדרואיד: המימוש נבדק מול שרת אמיתי מחוץ לאמולטור.
 */
interface RemoteFs : Closeable {
    /** בלי "." ו-"..". בשורש של חיבור SMB — השיתופים, כתיקיות. */
    fun list(path: String): List<Entry>

    /** null — לא קיים. */
    fun stat(path: String): Entry?

    fun openRead(path: String): InputStream

    /** קובץ חדש. קיים כבר — כשל ("כבר קיים פריט בשם הזה"): העתקה אף פעם לא דורסת. */
    fun openWrite(path: String): OutputStream

    fun mkdir(path: String)

    /** תיקייה — רק ריקה. */
    fun delete(path: String, isDirectory: Boolean)

    /** שינוי שם או העברה בתוך אותו שיתוף. היעד לא קיים. */
    fun rename(path: String, target: String)

    /** תאריך שינוי — אחרי העתקה, כמו בהעתקה מקומית. מה שהשרת לא מקבל לא מכשיל את ההעתקה. */
    fun setLastModified(path: String, time: Long)

    fun space(path: String): RemoteSpace?
}
