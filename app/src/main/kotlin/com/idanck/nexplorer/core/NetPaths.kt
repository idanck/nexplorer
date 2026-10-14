package com.idanck.nexplorer.core

import java.io.File

/** פרוטוקול של מקום ברשת (D3). SFTP ו-WebDAV יצטרפו בשלבים 5ב ו-5ג. */
enum class NetProtocol(val scheme: String, val label: String, val defaultPort: Int) {
    Smb("smb", "SMB", 445),
}

/**
 * נתיב של מקום ברשת: `smb://<חיבור>/<שיתוף>/<תיקייה>/<קובץ>`. המזהה של החיבור — ולא השרת — בנתיב, כדי
 * שמועדף או תצוגה "רק בתיקייה זו" ישרדו שינוי של כתובת השרת או של המשתמש. נתיב מקומי מתחיל ב-/,
 * ולכן אין התנגשות בין השניים. בלי תלות באנדרואיד — נבדק ב-JVM.
 */
object NetPaths {

    fun isRemote(path: String): Boolean = protocolOf(path) != null

    fun protocolOf(path: String): NetProtocol? = NetProtocol.entries.firstOrNull { path.startsWith("${it.scheme}://") }

    /** השורש של החיבור: `smb://<id>`. ב-SMB, הרשימה שם היא השיתופים. */
    fun root(protocol: NetProtocol, connectionId: String): String = "${protocol.scheme}://$connectionId"

    fun connectionId(path: String): String? {
        val protocol = protocolOf(path) ?: return null
        return path.removePrefix("${protocol.scheme}://").substringBefore('/').ifEmpty { null }
    }

    /** מה שאחרי המזהה: [שיתוף, תיקייה, ..., שם]. ריק בשורש של החיבור. */
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

    /** אותו חיבור ואותו שיתוף — אפשר להעביר בשינוי שם, בלי להעתיק בתים. */
    fun sameShare(a: String, b: String): Boolean {
        if (connectionId(a) == null || connectionId(a) != connectionId(b)) return false
        val sa = segments(a).firstOrNull() ?: return false
        return sa == segments(b).firstOrNull()
    }

    /**
     * בדיקת שם לפריט ברשת, מעבר ל-[FileNames.validate]: SMB (Windows) לא מקבל `\ : * ? " < > |`,
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
        }
    }

    private const val SMB_FORBIDDEN = "\\:*?\"<>|"
}
