package com.idanck.nexplorer.data.net

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * אסימון גישה לשירות ענן, לבקשה אחת. [token] יכול לחסום (Play services, רענון מול Microsoft) — רץ ב-IO.
 * [invalidate]: השירות דחה את האסימון (401) — הבא יהיה חדש.
 */
interface CloudAuth {
    fun token(): String

    fun invalidate(token: String)
}

/** אסימון ידוע מראש: ההתחברות שעכשיו ב"הוסף מקום", ובדיקות מול שרת מדומה. */
class FixedToken(private val value: String) : CloudAuth {
    override fun token(): String = value

    override fun invalidate(token: String) = Unit
}

/** ההתחברות לחשבון פגה או בוטלה — רק התחברות מחדש ("ערוך" במגירה) פותרת. */
class CloudSignInRequired(message: String, cause: Throwable? = null) : IOException(message, cause)

/** "לא נמצא" בשירות — [RemoteFs.stat] מחזיר null במקום לזרוק. */
class CloudNotFound(message: String) : IOException(message)

/**
 * HTTP משותף ל-Google Drive ול-OneDrive: אסימון בכל בקשה, ו-401 — אסימון חדש ועוד ניסיון אחד. מגבלת קצב
 * (429, ‏`rateLimitExceeded` של Drive) ו-5xx — ניסיון חוזר אחרי 1, 2, 4 ו-8 שניות, או לפי `Retry-After`.
 * [service] — השם בהודעות ("Google Drive"). בלי תלות באנדרואיד.
 */
internal class CloudHttp(
    private val service: String,
    private val auth: CloudAuth,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    /**
     * [build] נקרא מחדש בכל ניסיון (גוף הבקשה — מערך בתים, נשלח שוב). [authorized] = false — כתובת שכבר
     * מכילה הרשאה (upload session של Graph: אסימון שם מכשיל את הבקשה).
     */
    fun execute(authorized: Boolean = true, build: () -> Request.Builder): Response {
        var renewed = false
        var attempt = 0
        while (true) {
            val token = if (authorized) auth.token() else null
            val request = build().apply { token?.let { header("Authorization", "Bearer $it") } }.build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw network(e)
            }
            if (response.code == 401 && token != null && !renewed) {
                response.close()
                auth.invalidate(token)
                renewed = true
                continue
            }
            if (attempt < RETRIES && request.body?.isOneShot() != true && retryable(response)) {
                val wait = retryAfter(response) ?: (BACKOFF_MS shl attempt)
                response.close()
                sleep(wait.coerceAtMost(MAX_WAIT_MS))
                attempt++
                continue
            }
            return response
        }
    }

    /** JSON מבקשה שחייבת להצליח (2xx). */
    fun json(authorized: Boolean = true, build: () -> Request.Builder): JSONObject = execute(authorized, build).use { response ->
        if (response.code !in 200..299) throw failure(response)
        val text = response.body?.string().orEmpty()
        try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: org.json.JSONException) {
            throw IOException("תשובה לא מובנת מ-$service", e)
        }
    }

    /**
     * הורדה מ-[offset] (דילוג בנגן, אפליקציה אחרת שקוראת מהשרת): `Range` — 206 מהמקום. [ranged] = false (ייצוא של
     * Google Docs, שנוצר בכל בקשה) או שירות שמחזיר את הכול (200) — מדלגים בזרם; מעבר לסוף (416) — זרם ריק.
     */
    fun download(offset: Long, ranged: Boolean = true, build: () -> Request.Builder): InputStream {
        val range = offset > 0 && ranged
        val response = execute { build().apply { if (range) header("Range", "bytes=$offset-") } }
        if (range && response.code == 416) {
            response.close()
            return ByteArrayInputStream(ByteArray(0))
        }
        if (response.code != 200 && !(range && response.code == 206)) response.use { throw failure(it) }
        val body = response.body ?: response.use { throw IOException("תשובה ריקה מ-$service") }
        if (offset > 0 && response.code == 200) {
            try {
                body.source().skip(offset)
            } catch (e: EOFException) {
                response.close()
                return ByteArrayInputStream(ByteArray(0))
            } catch (e: IOException) {
                response.close()
                throw network(e)
            }
        }
        return object : FilterInputStream(body.byteStream()) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() = response.close()
        }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw network(e)
    }

    /** תשובה שאינה 2xx ← הודעה בעברית. 404 — [CloudNotFound]. */
    fun failure(response: Response): IOException {
        val error = errorOf(response)
        val code = response.code
        val reason = error.reason
        val message = when {
            code == 401 -> return CloudSignInRequired("ההתחברות ל-$service פגה — ערוך את החיבור והתחבר מחדש")
            code == 404 || reason == "itemNotFound" -> return CloudNotFound("הפריט לא נמצא ב-$service")
            reason in QUOTA || code == 507 -> "אין מקום פנוי ב-$service"
            code == 429 || reason in RATE -> "יותר מדי בקשות ל-$service — נסה שוב בעוד דקה"
            code == 409 || code == 412 || reason == "nameAlreadyExists" -> "כבר קיים פריט בשם הזה"
            code == 403 -> "אין הרשאה לפעולה הזו ב-$service"
            code == 413 -> "הקובץ גדול מדי בשביל $service"
            code == 423 -> "הפריט נעול ב-$service"
            code in 500..599 -> "שגיאה ב-$service ($code)"
            else -> "$service החזיר $code" + (error.message?.let { ": $it" } ?: "")
        }
        return IOException(message)
    }

    /** כשל בדרך (לא תשובה מהשירות) ← עברית. */
    fun network(e: Throwable): IOException = networkError(service, e)

    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private data class ErrorBody(val reason: String?, val message: String?)

    /**
     * Drive: `{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"message":…}}`;
     * Graph: `{"error":{"code":"nameAlreadyExists","message":…}}`. בלי לצרוך את הגוף — [failure] אחרי [retryable].
     */
    private fun errorOf(response: Response): ErrorBody = runCatching {
        val error = JSONObject(response.peekBody(ERROR_PEEK).string()).getJSONObject("error")
        val reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.ifEmpty { null }
            ?: error.optString("code").takeIf { it.isNotEmpty() && it.toIntOrNull() == null }
        ErrorBody(reason, error.optString("message").ifEmpty { null })
    }.getOrDefault(ErrorBody(null, null))

    private fun retryable(response: Response): Boolean = when (response.code) {
        429, 500, 502, 503, 504 -> true
        403 -> errorOf(response).reason in RATE
        else -> false
    }

    private fun retryAfter(response: Response): Long? = response.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1000)

    companion object {
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L
        private const val RETRIES = 4
        private const val BACKOFF_MS = 1000L
        private const val MAX_WAIT_MS = 30_000L
        private const val ERROR_PEEK = 64 * 1024L
        private val QUOTA = setOf("storageQuotaExceeded", "quotaExceeded", "quotaLimitReached", "insufficientStorage")
        private val RATE = setOf("rateLimitExceeded", "userRateLimitExceeded", "activityLimitReached")

        fun networkError(service: String, e: Throwable): IOException {
            if (e is CloudSignInRequired || e is CloudNotFound) return e as IOException
            val chain = generateSequence(e) { it.cause }.toList()
            val message = when {
                chain.any { it is UnknownHostException } -> "אין חיבור לאינטרנט ($service לא נמצא)"
                chain.any { it is SocketTimeoutException } -> "$service לא עונה"
                chain.any { it is ConnectException || it is NoRouteToHostException } -> "אין חיבור ל-$service"
                chain.any { it is SSLException } -> "החיבור המאובטח ל-$service נכשל"
                else -> null
            }
            return message?.let { IOException(it, e) } ?: (e as? IOException ?: IOException(e.message ?: "שגיאה ב-$service", e))
        }

        val JSON = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()

        /**
         * הכתובת של ה-API מהחיבור: [NetConnection.host], הפורט ו-http ([NetConnection.plainHttp]). בחיבור שנוצר
         * ב"הוסף מקום" — השרת של השירות; בבדיקות — שרת מדומה.
         */
        fun baseUrl(connection: NetConnection): HttpUrl = HttpUrl.Builder()
            .scheme(if (connection.plainHttp) "http" else "https")
            .host(connection.host)
            .port(connection.port ?: if (connection.plainHttp) 80 else 443)
            .build()
    }
}
