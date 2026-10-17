package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier
import okio.BufferedSink
import okio.Pipe
import okio.buffer
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread

/**
 * חיבור WebDAV אחד (Nextcloud, ‏ownCloud, ‏Synology, Apache) מעל OkHttp: PROPFIND לרשימה, GET ו-PUT
 * לקבצים, MKCOL, ‏MOVE ו-DELETE. הנתיבים יחסיים לכתובת של החיבור ([NetConnection.share]).
 *
 * https: תעודה שהמערכת מכירה — כמו בדפדפן. תעודה עצמית (שרת בבית) — רק אם היא זו שאושרה
 * ([NetConnection.hostKey], "x509-sha256 …"); אחרת [HostKeyUntrusted] עם התעודה שהוצגה, ו"בדוק והתחבר"
 * שואל את המשתמש. http — בלי הצפנה, לבחירת המשתמש.
 */
class WebDavFs(private val connection: NetConnection, password: CharArray?) : RemoteFs {

    private val authorization: String? = connection.user.takeIf { it.isNotBlank() }?.let { user ->
        // OkHttp מקבל רק String — הסיסמה נשארת בזיכרון כל עוד החיבור פתוח.
        Credentials.basic(user, String(password ?: CharArray(0)), Charsets.UTF_8)
    }
    private val base: HttpUrl = HttpUrl.Builder()
        .scheme(if (connection.plainHttp) "http" else "https")
        .host(connection.host)
        .port(connection.effectivePort)
        .apply { connection.share.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
        .build()
    private val trust = Trust()

    @Volatile
    private var offered: String? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        // OkHttp הופך הפניה ל-GET בכל שיטה חוץ מ-PROPFIND (MOVE לתיקייה בלי / ב-Apache), ומפיל את
        // ההתחברות בהפניה ל-https. הפניות בתוך אותו שרת — ב-[execute], עם אותה שיטה.
        .followRedirects(false)
        .followSslRedirects(false)
        .apply {
            if (!connection.plainHttp) {
                val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                sslSocketFactory(context.socketFactory, trust)
                // תעודה שאושרה — גם כשהשם שבה אינו הכתובת (תעודה עצמית ל-"nas.local", גישה ב-IP).
                hostnameVerifier { host, session -> trust.pinned(session) || verifyName(host, session) }
            }
        }
        .build()

    override fun list(path: String): List<Entry> {
        val url = urlOf(path, directory = true)
        val responses = propfind(url, depth = 1, PROPS)
        val own = NetPaths.segments(path)
        return responses.mapNotNull { r ->
            val segments = relativeSegments(url, r.href) ?: return@mapNotNull null
            if (segments == own || segments.isEmpty()) null else entryOf(NetPaths.child(path, segments.last()), r)
        }
    }

    override fun stat(path: String): Entry? {
        val responses = try {
            propfind(urlOf(path, directory = false), depth = 0, PROPS)
        } catch (e: NotFound) {
            return null
        }
        val own = responses.firstOrNull() ?: return null
        return entryOf(path, own, NetPaths.name(path).ifEmpty { connection.label })
    }

    /**
     * מ-[offset]: ‏`Range` (206). שרת שמתעלם ממנו מחזיר את כל הקובץ (200) — ואז מדלגים כאן; מעבר לסוף
     * הקובץ השרת עונה 416.
     */
    override fun openRead(path: String, offset: Long): InputStream {
        val request = request(urlOf(path, directory = false)).get()
        if (offset > 0) request.header("Range", "bytes=$offset-")
        val response = execute(request.build())
        if (offset > 0 && response.code == 416) {
            response.close()
            return ByteArrayInputStream(ByteArray(0))
        }
        if (response.code != 200 && !(offset > 0 && response.code == 206)) response.use { throw failure(it) }
        val body = response.body ?: response.use { throw failure(it) }
        if (offset > 0 && response.code == 200) {
            try {
                body.source().skip(offset)
            } catch (e: EOFException) {
                response.close()
                return ByteArrayInputStream(ByteArray(0))
            } catch (e: IOException) {
                response.close()
                throw map(e)
            }
        }
        return object : FilterInputStream(body.byteStream()) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() = response.close()
        }
    }

    /**
     * PUT בזרם: מה שנכתב עובר בצינור (okio — בלי קשר ל-thread שכותב; העתקה מושהית ממשיכה מ-thread
     * אחר) לבקשה שרצה ב-thread משלה. `If-None-Match: *` — השרת מסרב לדרוס קובץ קיים. הכשל של הבקשה
     * (שרת מלא, אין הרשאה) נראה ב-[OutputStream.close], או בכתיבה שאחריו.
     */
    override fun openWrite(path: String): OutputStream {
        val pipe = Pipe(PIPE_BUFFER)
        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                pipe.source.use { sink.writeAll(it) }
            }
        }
        val call = client.newCall(request(urlOf(path, directory = false)).put(body).header("If-None-Match", "*").build())
        var outcome: Result<IOException?>? = null
        val worker = thread(name = "webdav-put", isDaemon = true) {
            outcome = runCatching {
                call.execute().use { response -> if (response.code in 200..299) null else failure(response) }
            }
            // הבקשה נגמרה (גם בכשל) — כתיבה נוספת נכשלת מייד, ולא מחכה לנצח לקורא.
            pipe.cancel()
        }
        val output = pipe.sink.buffer()

        fun finish() {
            worker.join()
            outcome?.exceptionOrNull()?.let { throw map(it) }
            outcome?.getOrNull()?.let { throw it }
        }

        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                try {
                    output.write(b, off, len)
                } catch (e: IOException) {
                    // הצד השני של הצינור נגמר — הבקשה נכשלה; הסיבה שלה, לא "canceled".
                    finish()
                    throw IOException("ההעלאה לשרת נקטעה", e)
                }
            }

            override fun close() {
                try {
                    output.close()
                } catch (e: IOException) {
                    // הבקשה כבר נגמרה; התוצאה שלה ב-finish.
                }
                finish()
            }
        }
    }

    override fun mkdir(path: String) {
        execute(request(urlOf(path, directory = true)).method("MKCOL", null).build()).use { response ->
            when (response.code) {
                201 -> Unit
                // 405 — כבר קיים (RFC 4918, 9.3.1).
                405 -> throw IOException("כבר קיים פריט בשם הזה")
                else -> throw failure(response)
            }
        }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        // DELETE על תיקייה ב-WebDAV מוחק את כל מה שבתוכה — כאן רק ריקה, כמו בשאר הפרוטוקולים.
        if (isDirectory && list(path).isNotEmpty()) throw IOException("התיקייה לא ריקה")
        execute(request(urlOf(path, directory = isDirectory)).delete().build()).use { response ->
            if (response.code !in 200..299) throw failure(response)
        }
    }

    override fun rename(path: String, target: String) {
        if (!NetPaths.sameShare(path, target)) throw IOException("אפשר להעביר בשינוי שם רק בתוך אותו שרת")
        // תיקייה — עם / בסוף בשני הצדדים: Apache מפנה (301) תיקייה בלי /.
        val directory = stat(path)?.isDirectory ?: throw NotFound("הפריט לא נמצא בשרת")
        val request = request(urlOf(path, directory))
            .method("MOVE", null)
            .header("Destination", urlOf(target, directory).toString())
            .header("Overwrite", "F")
            .build()
        execute(request).use { response ->
            when (response.code) {
                201, 204 -> Unit
                412 -> throw IOException("כבר קיים פריט בשם הזה")
                // שרת שלא מעביר בין "כוננים" (Nextcloud: בין אחסונים חיצוניים) — מעתיקים ומוחקים.
                502 -> throw RemoteCrossDevice("השרת לא יכול להעביר לשם בשינוי שם")
                else -> throw failure(response)
            }
        }
    }

    /** Nextcloud ו-ownCloud מקבלים `{DAV:}lastmodified` ב-PROPPATCH; שרת אחר מסרב — וזה בסדר. */
    override fun setLastModified(path: String, time: Long) {
        runCatching {
            val body = """<?xml version="1.0" encoding="utf-8"?><d:propertyupdate xmlns:d="DAV:"><d:set><d:prop>""" +
                """<d:lastmodified>${time / 1000}</d:lastmodified></d:prop></d:set></d:propertyupdate>"""
            execute(request(urlOf(path, directory = false)).method("PROPPATCH", body.toRequestBody(XML)).build()).close()
        }
    }

    /** RFC 4331 (Nextcloud, ‏Synology); שרת שלא מדווח — בלי פס תפוסה. */
    override fun space(path: String): RemoteSpace? = runCatching {
        val own = propfind(urlOf(NetPaths.root(connection.protocol, connection.id), directory = true), depth = 0, QUOTA).firstOrNull()
        val available = own?.props?.get("quota-available-bytes")?.toLongOrNull()?.takeIf { it >= 0 } ?: return@runCatching null
        val used = own.props["quota-used-bytes"]?.toLongOrNull()?.takeIf { it >= 0 } ?: return@runCatching null
        RemoteSpace(free = available, total = available + used)
    }.getOrNull()?.takeIf { it.total > 0 }

    override fun close() {
        // גם בקשות שרצות (העלאה שנזנחה) — אחרת ה-thread שלהן מחכה לנצח.
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ---------- HTTP ----------

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        authorization?.let { header("Authorization", it) }
    }

    /**
     * הפניה (301/302/307/308) בתוך אותו שרת — עוקבים עם אותה שיטה, אותו גוף ואותה התחברות (תיקייה
     * בלי / בסוף). לשרת אחר או ל-scheme אחר — לא: [failure] מבקש להזין את הכתובת החדשה.
     */
    private fun execute(request: Request): Response {
        var current = request
        repeat(MAX_REDIRECTS) {
            val response = try {
                client.newCall(current).execute()
            } catch (e: IOException) {
                throw map(e)
            }
            if (response.code !in REDIRECTS || current.body?.isOneShot() == true) return response
            val location = response.header("Location")?.let(current.url::resolve)
            if (location == null || location.scheme != current.url.scheme || location.host != current.url.host || location.port != current.url.port) {
                return response
            }
            response.close()
            current = current.newBuilder().url(location).build()
        }
        return try {
            client.newCall(current).execute()
        } catch (e: IOException) {
            throw map(e)
        }
    }

    private fun propfind(url: HttpUrl, depth: Int, props: String): List<DavResponse> {
        val request = request(url)
            .method("PROPFIND", props.toRequestBody(XML))
            .header("Depth", depth.toString())
            .build()
        execute(request).use { response ->
            if (response.code != 207) throw failure(response)
            val xml = response.body?.byteStream() ?: throw IOException("תשובה ריקה מהשרת")
            return DavXml.parse(xml)
        }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw map(e)
    }

    private fun map(e: Throwable): IOException {
        if (e is HostKeyUntrusted || e is NotFound || e is RemoteCrossDevice) return e as IOException
        val chain = generateSequence(e) { it.cause }.toList()
        // רק כשל בלחיצת היד (התעודה, או השם שבה) — לא ניתוק באמצע העברה, שגם הוא SSLException.
        if (chain.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) {
            offered?.let { if (it != connection.hostKey) return HostKeyUntrusted(it, connection.hostKey != null) }
            return IOException("החיבור המאובטח לשרת נכשל — אם השרת ב-http, כתוב http:// בכתובת", e)
        }
        // מדיניות האבטחה של אנדרואיד חוסמת http — לא אמור לקרות (network_security_config מתיר).
        if (chain.any { it is java.net.UnknownServiceException }) return IOException("המכשיר חוסם http לשרת הזה", e)
        NetErrors.network(e, connection)?.let { return it }
        // https לשרת שמדבר http: "Unsupported or unrecognized SSL message" / "WRONG_VERSION_NUMBER".
        val plaintext = chain.any { t -> t is javax.net.ssl.SSLException && listOf("unrecognized", "plaintext", "wrong_version").any { t.message.orEmpty().contains(it, ignoreCase = true) } }
        if (plaintext) return IOException("החיבור המאובטח לשרת נכשל — אם השרת ב-http, כתוב http:// בכתובת", e)
        if (chain.any { it is javax.net.ssl.SSLException || it is java.net.SocketException }) return IOException("החיבור לשרת נותק", e)
        return e as? IOException ?: IOException(e.message ?: "שגיאת WebDAV", e)
    }


    private fun failure(response: Response): IOException {
        val message = when (response.code) {
            401 -> "שם המשתמש או הסיסמה שגויים"
            403 -> "אין הרשאה לפעולה הזו בשרת"
            404 -> return NotFound("הפריט לא נמצא בשרת")
            405, 501 -> "הכתובת לא מקבלת WebDAV — בדוק את הנתיב (ב-Nextcloud: /remote.php/dav/files/<משתמש>)"
            409 -> "תיקיית היעד לא קיימת בשרת"
            412 -> "כבר קיים פריט בשם הזה"
            413 -> "השרת לא מקבל קובץ בגודל הזה"
            423 -> "הפריט נעול בשרת"
            507 -> "אין מקום פנוי בשרת"
            in 300..399 -> "השרת מפנה לכתובת אחרת (${response.header("Location").orEmpty()}) — הזן אותה"
            in 500..599 -> "שגיאה בשרת (${response.code})"
            200 -> "הכתובת לא מקבלת WebDAV — בדוק את הנתיב"
            else -> "השרת החזיר ${response.code}"
        }
        return IOException(message)
    }

    // ---------- נתיבים ----------

    private fun urlOf(path: String, directory: Boolean): HttpUrl = base.newBuilder().apply {
        NetPaths.segments(path).forEach { addPathSegment(it) }
        // תיקייה עם / בסוף: אחרת יש שרתים שמפנים (301) לכתובת עם /.
        if (directory) addPathSegment("")
    }.build()

    /** ה-href של תשובה (מקודד; נתיב או כתובת מלאה) ← הרכיבים מתחת לכתובת של החיבור. null — לא מתחתיה. */
    private fun relativeSegments(requested: HttpUrl, href: String): List<String>? {
        val url = requested.resolve(href) ?: return null
        val all = url.pathSegments.filter { it.isNotEmpty() }
        val baseSegments = base.pathSegments.filter { it.isNotEmpty() }
        if (all.size < baseSegments.size || all.subList(0, baseSegments.size) != baseSegments) return null
        return all.drop(baseSegments.size)
    }

    private fun entryOf(path: String, r: DavResponse, name: String = NetPaths.name(path)): Entry {
        val isDir = r.isCollection
        return Entry(
            path = path,
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0 else r.props["getcontentlength"]?.toLongOrNull() ?: 0,
            childCount = -1,
            lastModified = r.props["getlastmodified"]?.let(::parseDate) ?: 0,
            isHidden = name.startsWith("."),
            kind = FileKinds.of(name, isDir),
        )
    }

    private fun verifyName(host: String, session: SSLSession): Boolean {
        if (OkHostnameVerifier.verify(host, session)) return true
        // תעודה של רשות מוכרת, אבל לשם אחר — גם היא ניתנת לאישור (כתובת IP לשרת עם תעודה לדומיין).
        offered = (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::certKey)
        return false
    }

    /**
     * תעודה שהמערכת מכירה, או זו שאושרה בחיבור. את מה שהוצג שומר — לשאלה "לסמוך על השרת?". Extended:
     * באנדרואיד, עם network_security_config שיש בו domain-config, מנהל האמון של המערכת דוחה כל בדיקה
     * בלי שם השרת — וכל תעודה של רשות מוכרת הייתה נראית "עצמית".
     */
    private inner class Trust : X509ExtendedTrustManager() {
        private val system: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

        fun pinned(session: SSLSession): Boolean =
            connection.hostKey != null && (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::certKey) == connection.hostKey

        private fun check(chain: Array<X509Certificate>, systemCheck: () -> Unit) {
            val key = certKey(chain.first())
            if (key == connection.hostKey) return
            try {
                systemCheck()
            } catch (e: CertificateException) {
                offered = key
                throw e
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = check(chain) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: system.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = check(chain) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: system.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = check(chain) {
            system.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = system.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = system.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /** "לא נמצא" — [stat] מחזיר null במקום לזרוק. */
    class NotFound(message: String) : IOException(message)

    companion object {
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L
        private const val PIPE_BUFFER = 256 * 1024L
        private const val MAX_REDIRECTS = 3
        private val REDIRECTS = setOf(301, 302, 307, 308)
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private const val PROPS = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop>""" +
            """<d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        private const val QUOTA = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop>""" +
            """<d:quota-available-bytes/><d:quota-used-bytes/></d:prop></d:propfind>"""

        /** "x509-sha256 AB:CD:…" — טביעת ה-SHA-256 של התעודה, כמו שדפדפן מציג אותה. */
        fun certKey(certificate: X509Certificate): String =
            "x509-sha256 " + MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

        private fun parseDate(value: String): Long? = runCatching {
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }
}

/** תשובה אחת מתוך multistatus: ה-href, האם תיקייה, והמאפיינים שחזרו ב-200 (לפי השם המקומי). */
internal data class DavResponse(val href: String, val isCollection: Boolean, val props: Map<String, String>)

/** פענוח של תשובת PROPFIND (207 Multi-Status, RFC 4918) — בלי DTD ובלי ישויות חיצוניות. */
internal object DavXml {

    fun parse(input: InputStream): List<DavResponse> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val bytes = input.readBytes()
        // ה-DOM של אנדרואיד לא מכיר disallow-doctype-decl: תשובה עם DTD (ישויות — "billion laughs")
        // נדחית כאן. ב-multistatus אמיתי אין DTD.
        if (String(bytes, 0, minOf(bytes.size, DOCTYPE_WINDOW), Charsets.ISO_8859_1).contains("<!DOCTYPE", ignoreCase = true)) {
            throw IOException("תשובה לא מובנת מהשרת — זו כתובת של WebDAV?")
        }
        val document = try {
            factory.newDocumentBuilder().parse(bytes.inputStream())
        } catch (e: Exception) {
            throw IOException("תשובה לא מובנת מהשרת — זו כתובת של WebDAV?", e)
        }
        val responses = document.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { i ->
            val response = responses.item(i) as Element
            val href = response.child("href")?.textContent?.trim() ?: return@mapNotNull null
            val props = HashMap<String, String>()
            var collection = false
            response.children("propstat").forEach { propstat ->
                // רק מה שחזר ב-200: מאפיין שהשרת לא מכיר חוזר ב-404 ריק.
                val status = propstat.child("status")?.textContent.orEmpty()
                if (" 200" !in status) return@forEach
                val prop = propstat.child("prop") ?: return@forEach
                prop.childElements().forEach { p ->
                    if (p.localName == "resourcetype") {
                        collection = p.childElements().any { it.localName == "collection" }
                    } else {
                        props[p.localName] = p.textContent.trim()
                    }
                }
            }
            DavResponse(href, collection, props)
        }
    }

    private const val DAV = "DAV:"
    private const val DOCTYPE_WINDOW = 4096

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.localName == localName && it.namespaceURI == DAV }

    private fun Element.child(localName: String): Element? = children(localName).firstOrNull()
}
