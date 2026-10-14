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
    /** SMB: השיתוף שנפתח; ריק — רשימת השיתופים בשרת. */
    val share: String,
    /** ריק — אורח. */
    val user: String,
    val domain: String = "",
    val secret: String? = null,
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

    /** "smb://192.168.50.171/volume2/Photos" — מה ש"העתק נתיב" נותן בתוך החיבור. */
    fun displayUrl(path: String): String {
        val segments = NetPaths.segments(path)
        val authority = host + if (port != null && port != protocol.defaultPort) ":$port" else ""
        return "${protocol.scheme}://$authority" + segments.joinToString("") { "/$it" }
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
                        .put("secret", c.secret ?: JSONObject.NULL),
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
                        secret = if (o.isNull("secret")) null else o.optString("secret").ifEmpty { null },
                    )
                }.getOrNull()
            }.distinctBy { it.id }
        }
    }
}
