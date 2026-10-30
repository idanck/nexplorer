package com.idanck.nexplorer.data.net

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * QuickConnect של Synology (שלב 7ג): מהמזהה ("my-nas") — לאן להתחבר. `get_server_info` בשרת של Synology
 * ([directory]) מחזיר את הכתובות של ה-NAS: ברשת הביתית — שם ב-`direct.quickconnect.to` שמצביע על הכתובת המקומית
 * ויש לו תעודה תקפה; ומבחוץ — ממסר של Synology (`relay_dn:relay_port`, גם הוא בתעודה תקפה). [connect] מנסה במקביל
 * את הכתובות המקומיות (2 שניות), ורק אם אף אחת לא עונה — את הממסר: בבית מהירות מלאה, ובחוץ — דרך הממסר, לאט.
 *
 * הפרוטוקול לא מתועד רשמית — הוא מה שהאפליקציות של Synology עושות, ונבדק מול NAS אמיתי (DSM 7.4, 30/10/2026).
 */
object QuickConnect {

    /** השרת של Synology. בבדיקות — שרת מדומה ב-http, ואז גם הכתובות שהוא מחזיר ב-http. */
    @Volatile
    var directory: HttpUrl = "https://global.quickconnect.to/Serv.php".toHttpUrl()

    private const val LOCAL_TIMEOUT_MS = 2_000L
    private const val RELAY_TIMEOUT_S = 15L
    private val JSON = "application/json".toMediaType()

    /** איך החיבור עובר. */
    enum class Route { Local, Relay }

    /** כתובת אחת של ה-NAS. [ip] — לשם ב-`direct.quickconnect.to`: הכתובת המקומית שבתוכו (בלי DNS). */
    data class Address(val url: HttpUrl, val route: Route, val ip: InetAddress? = null)

    /** מה ש-`get_server_info` החזיר: הכתובות המקומיות, בסדר, והממסר (אם יש). */
    data class Server(val local: List<Address>, val relay: Address?)

    /** ה-DNS של החיבור: שמות ב-`direct.quickconnect.to` — לכתובת שבתוכם, גם כשה-DNS בבית חוסם כתובות פרטיות. */
    class Resolver(private val pinned: Map<String, InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            pinned[hostname.lowercase()]?.let(::listOf) ?: Dns.SYSTEM.lookup(hostname)
    }

    fun resolver(server: Server): Resolver =
        Resolver(server.local.mapNotNull { a -> a.ip?.let { a.url.host.lowercase() to it } }.toMap())

    /** מזהה תקין: אותיות באנגלית, ספרות ומקף (כמו ב-DSM). */
    fun isValidId(id: String): Boolean = id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '-' }

    /** הכתובות של [id]. [IOException] בעברית — מזהה שלא קיים, NAS שלא מחובר, או שינוי אצל Synology. */
    fun lookup(id: String, client: OkHttpClient): Server {
        val secure = directory.isHttps
        val info = ask(directory, "get_server_info", id, client)
        var server = parse(info, secure)
        if (server.relay == null) {
            // אין עדיין ממסר פתוח ל-NAS: מבקשים אחד מהשרת של האזור (`control_host`).
            val control = info.optJSONObject("env")?.optString("control_host").orEmpty()
            val url = "${directory.scheme}://$control/Serv.php".toHttpUrlOrNull()
            if (control.isNotEmpty() && url != null) {
                runCatching { parse(ask(url, "request_tunnel", id, client), secure) }.getOrNull()?.relay?.let { server = server.copy(relay = it) }
            }
        }
        if (server.local.isEmpty() && server.relay == null) throw IOException("ה-NAS לא מחובר ל-QuickConnect כרגע")
        return server
    }

    /**
     * הכתובת שעונה: המקומיות במקביל ([LOCAL_TIMEOUT_MS]) — הראשונה שעונה ל-`SYNO.API.Info`; אחרת הממסר.
     * [client] — עם [resolver] של [server].
     */
    fun connect(server: Server, client: OkHttpClient): Address {
        if (server.local.isNotEmpty()) {
            val quick = client.newBuilder()
                .connectTimeout(LOCAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(LOCAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            val pool = Executors.newFixedThreadPool(server.local.size)
            try {
                return pool.invokeAny(server.local.map { a -> java.util.concurrent.Callable { if (answers(a, quick)) a else throw IOException("no answer") } }, LOCAL_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS)
            } catch (e: ExecutionException) {
                // אף כתובת מקומית לא ענתה — מחוץ לבית.
            } catch (e: TimeoutException) {
                // כנ"ל.
            } finally {
                pool.shutdownNow()
            }
        }
        val relay = server.relay ?: throw IOException("ה-NAS לא זמין דרך QuickConnect — אין ממסר, ולא נמצא ברשת הזו")
        val slow = client.newBuilder().connectTimeout(RELAY_TIMEOUT_S, TimeUnit.SECONDS).readTimeout(RELAY_TIMEOUT_S, TimeUnit.SECONDS).build()
        if (!runCatching { answers(relay, slow) }.getOrDefault(false)) throw IOException("ה-NAS לא עונה דרך הממסר של QuickConnect")
        return relay
    }

    /** `SYNO.API.Info` — בלי התחברות: האם יש כאן DSM. */
    private fun answers(address: Address, client: OkHttpClient): Boolean {
        val url = address.url.newBuilder().addPathSegment("webapi").addPathSegment("query.cgi")
            .addQueryParameter("api", "SYNO.API.Info")
            .addQueryParameter("version", "1")
            .addQueryParameter("method", "query")
            .addQueryParameter("query", "SYNO.API.Auth")
            .build()
        return client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            response.isSuccessful && runCatching { JSONObject(response.body?.string().orEmpty()).optBoolean("success") }.getOrDefault(false)
        }
    }

    private fun ask(url: HttpUrl, command: String, id: String, client: OkHttpClient): JSONObject {
        val body = JSONObject()
            .put("version", 1)
            .put("command", command)
            .put("stop_when_error", false)
            .put("stop_when_success", false)
            .put("id", if (url.isHttps) "dsm_https" else "dsm")
            .put("serverID", id)
            .put("is_gofile", false)
            .toString()
            .toRequestBody(JSON)
        val json = try {
            client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("QuickConnect לא זמין (${response.code}) — השתמש ברשת ביתית")
                JSONObject(response.body?.string().orEmpty())
            }
        } catch (e: JSONException) {
            throw IOException("תשובה לא מובנת מ-QuickConnect — השתמש ברשת ביתית", e)
        } catch (e: IOException) {
            if (e.message?.contains("QuickConnect") == true) throw e
            throw IOException("אין חיבור לשרת של QuickConnect — בדוק את החיבור לאינטרנט", e)
        }
        error(json)?.let { throw it }
        return json
    }

    /** errno של `Serv.php` ← עברית; null — הצליח. 4/1 — המזהה לא קיים (נבדק). */
    fun error(json: JSONObject): IOException? {
        val errno = json.optInt("errno", -1)
        if (errno == 0) return null
        val info = json.optString("errinfo")
        return IOException(
            when {
                errno == 4 && json.optInt("suberrno", -1) == 1 || info.contains("not found", ignoreCase = true) -> "המזהה לא נמצא ב-QuickConnect"
                errno == 9 -> "מזהה QuickConnect לא תקין"
                else -> "QuickConnect לא זמין ($errno) — השתמש ברשת ביתית"
            },
        )
    }

    /**
     * הכתובות מתוך התשובה: מקומיות — `smartdns.lan` (שמות ב-`direct.quickconnect.to`, עם הפורט של DSM), בסדר שבתשובה;
     * ממסר — `relay_dn` (או `relay_ip`) ו-`relay_port`. [secure] — https (בבדיקות מול שרת מדומה — http).
     */
    fun parse(json: JSONObject, secure: Boolean): Server {
        val scheme = if (secure) "https" else "http"
        val service = json.optJSONObject("service") ?: JSONObject()
        val port = service.optInt("port", if (secure) 5001 else 5000)
        val lan = json.optJSONObject("smartdns")?.optJSONArray("lan")
        val local = (0 until (lan?.length() ?: 0)).mapNotNull { i ->
            val host = lan!!.optString(i).trim().trimEnd('.')
            if (host.isEmpty()) return@mapNotNull null
            val url = runCatching { HttpUrl.Builder().scheme(scheme).host(host).port(port).build() }.getOrNull() ?: return@mapNotNull null
            Address(url, Route.Local, ipOf(host))
        }
        val relayHost = service.optString("relay_dn").ifEmpty { service.optString("relay_ip") }
        val relayPort = service.optInt("relay_port", 0)
        val relay = if (relayHost.isNotEmpty() && relayPort in 1..65535) {
            runCatching { HttpUrl.Builder().scheme(scheme).host(relayHost).port(relayPort).build() }.getOrNull()?.let { Address(it, Route.Relay) }
        } else {
            null
        }
        return Server(local, relay)
    }

    /** "192-168-1-20.my-nas.direct.quickconnect.to" ← 192.168.1.20; null — אין כתובת בשם. */
    fun ipOf(host: String): InetAddress? {
        val parts = host.substringBefore('.').split('-')
        if (parts.size != 4) return null
        val bytes = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return InetAddress.getByAddress(host, ByteArray(4) { bytes[it].toByte() })
    }
}
