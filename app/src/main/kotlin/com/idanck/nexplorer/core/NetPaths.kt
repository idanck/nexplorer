package com.idanck.nexplorer.core

import java.io.File

/**
 * פרוטוקול של מקום ברשת (D3): SMB (5א), SFTP (5ב), WebDAV (5ג; הפורט — של https, ו-80 ל-http). ענן (D4, שלב 6):
 * Google Drive ו-OneDrive — [cloud]: בלי שרת לבחור, ההתחברות בחשבון של השירות.
 */
enum class NetProtocol(val scheme: String, val label: String, val defaultPort: Int, val cloud: Boolean = false) {
    Smb("smb", "SMB", 445),
    Sftp("sftp", "SFTP", 22),
    WebDav("dav", "WebDAV", 443),
    GoogleDrive("gdrive", "Google Drive", 443, cloud = true),
    OneDrive("onedrive", "OneDrive", 443, cloud = true),
}

/**
 * נתיב של מקום ברשת: `smb://<חיבור>/<שיתוף>/<תיקייה>/<קובץ>`, `sftp://<חיבור>/<נתיב מלא בשרת>`
 * (`sftp://<חיבור>` הוא `/` של השרת), `dav://<חיבור>/<נתיב מתחת לכתובת של החיבור>`, או `gdrive://<חיבור>/...` ו-`onedrive://<חיבור>/...`
 * (השורש — "האחסון שלי"). המזהה של החיבור — ולא השרת — בנתיב, כדי
 * שמועדף או תצוגה "רק בתיקייה זו" ישרדו שינוי של כתובת השרת או של המשתמש. נתיב מקומי מתחיל ב-/,
 * ולכן אין התנגשות בין השניים. בלי תלות באנדרואיד — נבדק ב-JVM.
 */
object NetPaths {

    fun isRemote(path: String): Boolean = protocolOf(path) != null

    fun protocolOf(path: String): NetProtocol? = NetProtocol.entries.firstOrNull { path.startsWith("${it.scheme}://") }

    /** השורש של החיבור: `smb://<id>`. ב-SMB, הרשימה שם היא השיתופים; ב-SFTP — `/` של השרת. */
    fun root(protocol: NetProtocol, connectionId: String): String = "${protocol.scheme}://$connectionId"

    fun connectionId(path: String): String? {
        val protocol = protocolOf(path) ?: return null
        return path.removePrefix("${protocol.scheme}://").substringBefore('/').ifEmpty { null }
    }

    /** מה שאחרי המזהה: [שיתוף, תיקייה, ..., שם] (ב-SFTP — רכיבי הנתיב בשרת). ריק בשורש של החיבור. */
    fun segments(path: String): List<String> {
        val protocol = protocolOf(path) ?: return emptyList()
        val rest = path.removePrefix("${protocol.scheme}://").substringAfter('/', "")
        return rest.split('/').filter { it.isNotEmpty() }
    }

    fun child(dir: String, name: String): String = "${dir.trimEnd('/')}/$name"

    /** null בשורש של החיבור. */
    fun parent(path: String): String? {
        val protocol = protocolOf(path) ?: return null
        val id = connectionId(path) ?: return null
        val segments = segments(path)
        if (segments.isEmpty()) return null
        return (listOf(root(protocol, id)) + segments.dropLast(1)).joinToString("/")
    }

    /** השם האחרון; ריק בשורש של החיבור. */
    fun name(path: String): String = segments(path).lastOrNull().orEmpty()

    /** תיקיית האב של נתיב מקומי או ברשת (null בשורש). */
    fun parentOf(path: String): String? = if (isRemote(path)) parent(path) else File(path).parent

    /** שם הפריט האחרון בנתיב מקומי או ברשת. */
    fun nameOf(path: String): String = if (isRemote(path)) name(path) else File(path).name

    /** [path] בתוך [dir] או זהה לו — בנתיבים באותה צורה (שניהם ברשת או שניהם מקומיים). */
    fun isInside(path: String, dir: String): Boolean = FileNames.isInside(path, dir)

    /** SMB: השורש של החיבור — רשימת השיתופים, לא תיקייה (אין בה קבצים, ואי אפשר ליצור או להדביק בה). */
    fun isShareList(path: String): Boolean = protocolOf(path) == NetProtocol.Smb && segments(path).isEmpty()

    /** SMB: שיתוף שלם — לא נמחק, לא משנה שם ולא מועתק כיחידה. */
    fun isShare(path: String): Boolean = protocolOf(path) == NetProtocol.Smb && segments(path).size == 1

    /** מה שאי אפשר לבחור כפריט: רשימת השיתופים, שיתוף, ו-`/` של שרת SFTP. */
    fun isFixed(path: String): Boolean = isShareList(path) || isShare(path) || (isRemote(path) && segments(path).isEmpty())

    /**
     * אפשר להעביר בשינוי שם, בלי להעתיק בתים: אותו חיבור, וב-SMB גם אותו שיתוף. ב-SFTP השרת עצמו מסרב
     * כשהיעד בכונן אחר — ואז זו שגיאה, כמו `mv` בין כוננים בלי העתקה.
     */
    fun sameShare(a: String, b: String): Boolean {
        if (connectionId(a) == null || connectionId(a) != connectionId(b) || protocolOf(a) != protocolOf(b)) return false
        if (protocolOf(a) != NetProtocol.Smb) return true
        val sa = segments(a).firstOrNull() ?: return false
        return sa == segments(b).firstOrNull()
    }

    /**
     * בדיקת שם לפריט ברשת, מעבר ל-[FileNames.validate]: SMB (Windows) ו-OneDrive לא מקבלים `\ : * ? " < > |`,
     * ושם שנגמר בנקודה או ברווח נשמר בלעדיהם.
     */
    fun validateName(protocol: NetProtocol, name: String): String? {
        FileNames.validate(name)?.let { return it }
        return when (protocol) {
            NetProtocol.Smb -> when {
                name.any { it in SMB_FORBIDDEN } -> "בשרת הזה השם לא יכול להכיל \\ : * ? \" < > |"
                name.endsWith('.') || name.endsWith(' ') -> "בשרת הזה השם לא יכול להיגמר בנקודה או ברווח"
                else -> null
            }
            // שרת SSH (Linux, macOS): כל שם שמקומי מקבל.
            NetProtocol.Sftp -> null
            // השם עובר מקודד בכתובת; רק מה שבלתי אפשרי בה ממילא.
            NetProtocol.WebDav -> if (name.any { it == '\\' }) "בשרת הזה השם לא יכול להכיל \\" else null
            // Drive מקבל כל שם (גם / — הוא מוצג כ-∕, ראה DriveNames).
            NetProtocol.GoogleDrive -> null
            NetProtocol.OneDrive -> when {
                name.any { it in SMB_FORBIDDEN } -> "ב-OneDrive השם לא יכול להכיל \\ : * ? \" < > |"
                name.endsWith('.') || name.endsWith(' ') || name.startsWith(' ') -> "ב-OneDrive השם לא יכול להתחיל ברווח או להיגמר בנקודה או ברווח"
                name.substringBefore('.').uppercase() in WINDOWS_RESERVED || name.equals("desktop.ini", ignoreCase = true) ->
                    "OneDrive לא מקבל את השם הזה"
                else -> null
            }
        }
    }

    private const val SMB_FORBIDDEN = "\\:*?\"<>|"
    private val WINDOWS_RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (0..9).flatMap { listOf("COM$it", "LPT$it") }
}
