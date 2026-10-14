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
import okio.source
import org.w3c.dom.Element
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
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

    override fun openRead(path: String): InputStream {
        val response = execute(request(urlOf(path, directory = false)).get().build())
        if (response.code != 200) throw failure(response)
        val body = response.body ?: throw failure(response)
        return object : FilterInputStream(body.byteStream()) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() = response.close()
        }
    }

    /**
     * PUT בזרם: מה שנכתב עובר בצינור לבקשה שרצה ב-thread משלה. `If-None-Match: *` — השרת מסרב לדרוס
     * קובץ קיים. הכשל של הבקשה (שרת מלא, אין הרשאה) נראה ב-[OutputStream.close], או בכתיבה שאחריו.
     */
    override fun openWrite(path: String): OutputStream {
        val input = PipedInputStream(PIPE_BUFFER)
        val output = PipedOutputStream(input)
        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                input.source().use { sink.writeAll(it) }
            }
        }
        val request = request(urlOf(path, directory = false)).put(body).header("If-None-Match", "*").build()
        var outcome: Result<Int>? = null
        var error: IOException? = null
        val worker = thread(name = "webdav-put", isDaemon = true) {
            outcome = runCatching {
                execute(request).use { response ->
                    if (response.code !in 200..299) error = failure(response)
                    response.code
                }
            }
            runCatching { input.close() }
        }

        fun finish() {
            worker.join()
            outcome?.exceptionOrNull()?.let { throw map(it) }
            error?.let { throw it }
        }

        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                try {
                    output.write(b, off, len)
                } catch (e: IOException) {
                    // הצד השני של הצינור נגמר — הבקשה נכשלה; הסיבה שלה, לא "Pipe broken".
                    finish()
                    throw e
                }
            }

            override fun close() {
                runCatching { output.close() }
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
        val request = request(urlOf(path, directory = false))
            .method("MOVE", null)
            .header("Destination", urlOf(target, directory = false).toString())
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
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ---------- HTTP ----------

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        authorization?.let { header("Authorization", it) }
    }

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        throw map(e)
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
        if (chain.any { it is javax.net.ssl.SSLException || it is CertificateException }) {
            offered?.let { if (it != connection.hostKey) return HostKeyUntrusted(it, connection.hostKey != null) }
            return IOException("החיבור המאובטח לשרת נכשל — אם השרת ב-http, כתוב http:// בכתובת", e)
        }
        // מדיניות האבטחה של אנדרואיד חוסמת http — לא אמור לקרות (network_security_config מתיר).
        if (chain.any { it is java.net.UnknownServiceException }) return IOException("המכשיר חוסם http לשרת הזה", e)
        NetErrors.network(e, connection)?.let { return it }
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

    /** תעודה שהמערכת מכירה, או זו שאושרה בחיבור. את מה שהוצג שומר — לשאלה "לסמוך על השרת?". */
    private inner class Trust : X509TrustManager {
        private val system: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

        fun pinned(session: SSLSession): Boolean =
            connection.hostKey != null && (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::certKey) == connection.hostKey

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val key = certKey(chain.first())
            if (key == connection.hostKey) return
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                offered = key
                throw e
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    /** "לא נמצא" — [stat] מחזיר null במקום לזרוק. */
    class NotFound(message: String) : IOException(message)

    companion object {
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L
        private const val PIPE_BUFFER = 256 * 1024
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
        val document = try {
            factory.newDocumentBuilder().parse(input)
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

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.localName == localName && it.namespaceURI == DAV }

    private fun Element.child(localName: String): Element? = children(localName).firstOrNull()
}
