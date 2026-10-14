package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * מקום ברשת שנשמר במגירה ("הוסף מקום"). הסיסמה לא כאן כמו שהיא: [secret] הוא הצופן שלה במפתח של
 * Android Keystore ([ConnectionSecrets]) — המפתח לא יוצא מהמכשיר, ונמחק עם הסרת האפליקציה.
 */
data class NetConnection(
    val id: String,
    val protocol: NetProtocol,
    /** השם במגירה; ריק — השרת. */
    val name: String,
    val host: String,
    /** null — ברירת המחדל של הפרוטוקול. */
    val port: Int?,
    /**
     * SMB: השיתוף שנפתח; ריק — רשימת השיתופים בשרת. SFTP: התיקייה שנפתחת, בלי / בקצוות ("home/idan");
     * "בדוק והתחבר" ממלא את תיקיית הבית כשהשדה ריק.
     */
    val share: String,
    /** ריק — אורח (ב-SMB בלבד). */
    val user: String,
    val domain: String = "",
    /** הצופן של הסיסמה — או, כשיש [keySecret], של הסיסמה של המפתח. */
    val secret: String? = null,
    /** SFTP: המפתח של השרת שאושר ("ssh-ed25519 AAAA…") — חיבור לשרת עם מפתח אחר נדחה. */
    val hostKey: String? = null,
    /** SFTP: הצופן של קובץ המפתח הפרטי (OpenSSH / PEM / PuTTY), באותו מפתח Keystore כמו הסיסמה. */
    val keySecret: String? = null,
) {
    val label: String get() = name.ifBlank { host }

    val effectivePort: Int get() = port ?: protocol.defaultPort

    /** הנתיב שנפתח מהמגירה, וממנו "למעלה" חוזר למכשיר. */
    val root: String
        get() = NetPaths.root(protocol, id).let { if (share.isBlank()) it else NetPaths.child(it, share) }

    /** "idanc@192.168.50.171" — בשורה שמתחת לשם במגירה (כמו "idan@192.168.50.83:41725" בלוח). */
    val subtitle: String
        get() = buildString {
            if (user.isNotBlank()) append(user).append('@')
            append(host)
            if (port != null && port != protocol.defaultPort) append(':').append(port)
        }

    /** "smb://192.168.50.171/volume2/Photos" או "sftp://192.168.50.83/home/idan" — מה ש"העתק נתיב" נותן בתוך החיבור. */
    fun displayUrl(path: String): String {
        val segments = NetPaths.segments(path)
        val authority = host + if (port != null && port != protocol.defaultPort) ":$port" else ""
        // SFTP: השורש הוא "/" של השרת.
        val tail = segments.joinToString("") { "/$it" }.ifEmpty { if (protocol == NetProtocol.Sftp) "/" else "" }
        return "${protocol.scheme}://$authority$tail"
    }

    /** "NAS › volume2 › Photos" — במקומות שבהם הנתיב מוצג למשתמש (מועדפים, מאפיינים). */
    fun displayPath(path: String): String = (listOf(label) + NetPaths.segments(path)).joinToString(" › ")

    companion object {
        fun newId(): String {
            val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
        }

        fun encode(list: List<NetConnection>): String = JSONArray().apply {
            list.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id)
                        .put("protocol", c.protocol.name)
                        .put("name", c.name)
                        .put("host", c.host)
                        .put("port", c.port ?: JSONObject.NULL)
                        .put("share", c.share)
                        .put("user", c.user)
                        .put("domain", c.domain)
                        .put("secret", c.secret ?: JSONObject.NULL)
                        .put("hostKey", c.hostKey ?: JSONObject.NULL)
                        .put("keySecret", c.keySecret ?: JSONObject.NULL),
                )
            }
        }.toString()

        /** רשומה פגומה או מפרוטוקול שגרסה ישנה לא מכירה — מדלגים עליה, ולא מאבדים את השאר. */
        fun decode(raw: String?): List<NetConnection> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val o = array.getJSONObject(i)
                    val protocol = NetProtocol.entries.firstOrNull { it.name == o.getString("protocol") } ?: return@runCatching null
                    NetConnection(
                        id = o.getString("id"),
                        protocol = protocol,
                        name = o.optString("name"),
                        host = o.getString("host"),
                        port = if (o.isNull("port")) null else o.getInt("port"),
                        share = o.optString("share"),
                        user = o.optString("user"),
                        domain = o.optString("domain"),
                        secret = o.nullableString("secret"),
                        hostKey = o.nullableString("hostKey"),
                        keySecret = o.nullableString("keySecret"),
                    )
                }.getOrNull()
            }.distinctBy { it.id }
        }

        /** חסר, null או ריק — null (רשומה מגרסה שלא הכירה את השדה). */
        private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
    }
}
