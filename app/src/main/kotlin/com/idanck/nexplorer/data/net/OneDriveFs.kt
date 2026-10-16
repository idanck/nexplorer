package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.time.Instant

/**
 * OneDrive מעל Microsoft Graph. ב-Graph הנתיב הוא גם הכתובת (`/me/drive/root:/תיקייה/קובץ`), ולכן אין מטמון.
 * אף פעם לא דורסים (`@microsoft.graph.conflictBehavior=fail`); מחיקה — לסל המחזור. OneNote ופריטים
 * ששותפו מחשבון אחר לא מוצגים. בלי תלות באנדרואיד.
 */
class OneDriveFs(private val connection: NetConnection, auth: CloudAuth) : RemoteFs {

    private val http = CloudHttp(SERVICE, auth)
    private val api: HttpUrl = CloudHttp.baseUrl(connection)

    override fun list(path: String): List<Entry> {
        val entries = ArrayList<Entry>()
        var next: HttpUrl? = item(path, "children").newBuilder()
            .addQueryParameter("\$select", SELECT)
            .addQueryParameter("\$top", PAGE_SIZE.toString())
            .build()
        while (next != null) {
            val url: HttpUrl = next
            val json = http.json { Request.Builder().url(url).get() }
            val values = json.optJSONArray("value")
            if (values != null) {
                for (i in 0 until values.length()) {
                    val o = values.getJSONObject(i)
                    if (!visible(o)) continue
                    entries += entryOf(NetPaths.child(path, o.getString("name")), o)
                }
            }
            next = json.optString("@odata.nextLink").ifEmpty { null }?.let(url::resolve)
        }
        return entries
    }

    override fun stat(path: String): Entry? {
        val o = try {
            http.json { Request.Builder().url(item(path).newBuilder().addQueryParameter("\$select", SELECT).build()).get() }
        } catch (e: CloudNotFound) {
            return null
        }
        if (!visible(o)) return null
        return entryOf(path, o, NetPaths.name(path).ifEmpty { connection.label })
    }

    /** `/content` מפנה (302) לכתובת הורדה זמנית; OkHttp עוקב, ולא שולח את האסימון לשרת אחר. */
    override fun openRead(path: String): InputStream {
        val response = http.execute { Request.Builder().url(item(path, "content")).get() }
        if (response.code != 200) response.use { throw http.failure(it) }
        val body = response.body ?: response.use { throw IOException("תשובה ריקה מ-$SERVICE") }
        return object : FilterInputStream(body.byteStream()) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() = response.close()
        }
    }

    /**
     * עד [SIMPLE_MAX] — PUT אחד. גדול — upload session בגושים של [CHUNK], עם [size] בכל גוש (Graph דורש את הגודל
     * הכולל מראש). גודל לא ידוע (ייצוא של Google Docs) — מה שמעבר לגוש הראשון נאסף בקובץ זמני, ואז נשלח.
     */
    override fun openWrite(path: String, size: Long): OutputStream {
        val parent = NetPaths.parent(path) ?: throw IOException("אי אפשר ליצור פריט מעל השורש")
        // Graph יוצר תיקיות חסרות בדרך; כאן — כמו בשאר המקומות, רק בתוך תיקייה קיימת.
        if (stat(parent)?.isDirectory != true) throw IOException("תיקיית היעד לא קיימת ב-$SERVICE")
        if (stat(path) != null) throw IOException("כבר קיים פריט בשם הזה")
        return Upload(path, size.takeIf { it > SIMPLE_MAX })
    }

    override fun mkdir(path: String) {
        val parent = NetPaths.parent(path) ?: throw IOException("אי אפשר ליצור תיקייה מעל השורש")
        val body = JSONObject()
            .put("name", NetPaths.name(path))
            .put("folder", JSONObject())
            .put(CONFLICT, "fail")
        http.json { Request.Builder().url(item(parent, "children")).post(body.toString().toRequestBody(CloudHttp.JSON)) }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        if (NetPaths.segments(path).isEmpty()) throw IOException("אי אפשר למחוק את השורש של $SERVICE")
        // לסל — עם כל מה שבתוך התיקייה; כאן רק ריקה, כמו בשאר המקומות.
        if (isDirectory && list(path).isNotEmpty()) throw IOException("התיקייה לא ריקה")
        http.execute { Request.Builder().url(item(path)).delete() }.use { response ->
            if (response.code !in 200..299) throw http.failure(response)
        }
    }

    override fun rename(path: String, target: String) {
        if (!NetPaths.sameShare(path, target)) throw IOException("אפשר להעביר בשינוי שם רק בתוך אותו חשבון")
        val body = JSONObject().put("name", NetPaths.name(target))
        val to = NetPaths.parent(target) ?: throw IOException("אי אפשר להעביר מעל השורש")
        if (to != NetPaths.parent(path)) {
            val id = http.json { Request.Builder().url(item(to).newBuilder().addQueryParameter("\$select", "id,folder").build()).get() }
                .takeIf { it.has("folder") }?.getString("id") ?: throw IOException("תיקיית היעד לא קיימת ב-$SERVICE")
            body.put("parentReference", JSONObject().put("id", id))
        }
        http.json { Request.Builder().url(item(path)).patch(body.toString().toRequestBody(CloudHttp.JSON)) }
    }

    override fun setLastModified(path: String, time: Long) {
        runCatching {
            val body = JSONObject().put("fileSystemInfo", JSONObject().put("lastModifiedDateTime", Instant.ofEpochMilli(time).toString()))
            http.json { Request.Builder().url(item(path)).patch(body.toString().toRequestBody(CloudHttp.JSON)) }
        }
    }

    override fun space(path: String): RemoteSpace? = runCatching {
        val url = drive().newBuilder().addQueryParameter("\$select", "quota").build()
        val quota = http.json { Request.Builder().url(url).get() }.getJSONObject("quota")
        val total = quota.optLong("total", -1).takeIf { it > 0 } ?: return@runCatching null
        val free = quota.optLong("remaining", -1).takeIf { it >= 0 } ?: (total - quota.optLong("used", 0)).coerceAtLeast(0)
        RemoteSpace(free = free, total = total)
    }.getOrNull()

    /** החשבון — לשם במגירה, אחרי ההתחברות ב"הוסף מקום". */
    fun account(): String {
        val url = api.newBuilder().addPathSegments("v1.0/me").addQueryParameter("\$select", "userPrincipalName,mail").build()
        val me = http.json { Request.Builder().url(url).get() }
        return me.optString("userPrincipalName").ifEmpty { me.optString("mail") }.ifEmpty { null }
            ?: throw IOException("$SERVICE לא החזיר את החשבון")
    }

    override fun close() = http.close()

    private fun drive(): HttpUrl = api.newBuilder().addPathSegments("v1.0/me/drive").build()

    /** הכתובת של פריט לפי הנתיב, ו-[suffix] אחריו (`children`, ‏`content`...). */
    private fun item(path: String, suffix: String? = null): HttpUrl = itemUrl(drive(), NetPaths.segments(path), suffix)

    /** חבילה (OneNote) ופריט מחשבון אחר — לא נתמכים בשלב הזה. */
    private fun visible(o: JSONObject): Boolean = !o.has("package") && !o.has("remoteItem")

    private fun entryOf(path: String, o: JSONObject, name: String = o.optString("name")): Entry {
        val folder = o.optJSONObject("folder")
        val modified = o.optJSONObject("fileSystemInfo")?.optString("lastModifiedDateTime")?.ifEmpty { null } ?: o.optString("lastModifiedDateTime")
        return Entry(
            path = path,
            name = name,
            isDirectory = folder != null,
            size = if (folder != null) 0 else o.optLong("size", 0),
            childCount = folder?.optInt("childCount", -1) ?: -1,
            lastModified = runCatching { Instant.parse(modified).toEpochMilli() }.getOrDefault(0L),
            isHidden = name.startsWith("."),
            kind = FileKinds.of(name, folder != null),
        )
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw http.network(e)
    }

    // ---------- העלאה ----------

    /** [declared] — הגודל שתור ההעברות הבטיח (מעל [SIMPLE_MAX]); null — לא ידוע. */
    private inner class Upload(private val path: String, private val declared: Long?) : OutputStream() {
        /** גוש של upload session; בגודל לא ידוע — עד [SIMPLE_MAX] בזיכרון. */
        private val buffer = ByteArray(CHUNK)
        private val limit = if (declared != null) CHUNK else SIMPLE_MAX
        private var filled = 0
        private var sent = 0L
        private var session: HttpUrl? = null
        /** גודל לא ידוע ומעבר ל-[SIMPLE_MAX]: הכול עובר לקובץ זמני, ונשלח בסגירה. */
        private var spool: File? = null
        private var spoolOut: OutputStream? = null
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            spoolOut?.let { return it.write(b, off, len) }
            if (declared != null && sent + filled + len > declared) {
                abort()
                throw IOException("הקובץ גדל בזמן ההעתקה — נסה שוב")
            }
            var from = off
            var left = len
            while (left > 0) {
                if (filled == limit) {
                    if (declared == null) {
                        startSpool()
                        spoolOut!!.write(b, from, left)
                        return
                    }
                    sendChunk(declared)
                }
                val n = minOf(left, limit - filled)
                System.arraycopy(b, from, buffer, filled, n)
                filled += n
                from += n
                left -= n
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                when {
                    spool != null -> uploadSpool()
                    declared != null -> {
                        if (sent + filled != declared) {
                            abort()
                            throw IOException("הקובץ השתנה בזמן ההעתקה — נסה שוב")
                        }
                        sendChunk(declared)
                    }
                    else -> simple()
                }
            } finally {
                spoolOut?.let { runCatching { it.close() } }
                spool?.delete()
            }
        }

        private fun simple() {
            val bytes = buffer.copyOf(filled)
            val url = item(path, "content").newBuilder().addQueryParameter(CONFLICT, "fail").build()
            http.json { Request.Builder().url(url).put(bytes.toRequestBody(CloudHttp.OCTET_STREAM)) }
        }

        private fun startSpool() {
            val file = File.createTempFile("onedrive-", ".part")
            spool = file
            spoolOut = file.outputStream().buffered().also { it.write(buffer, 0, filled) }
            filled = 0
        }

        private fun uploadSpool() {
            val file = spool!!
            spoolOut!!.close()
            spoolOut = null
            val total = file.length()
            RandomAccessFile(file, "r").use { input ->
                while (sent < total) {
                    val n = minOf(CHUNK.toLong(), total - sent).toInt()
                    input.readFully(buffer, 0, n)
                    filled = n
                    sendChunk(total)
                }
            }
        }

        /** גוש אחד ל-upload session: 202 — ממשיכים; 200/201 — האחרון נשמר. */
        private fun sendChunk(total: Long) {
            val target = session ?: start().also { session = it }
            val bytes = buffer.copyOf(filled)
            val range = "bytes $sent-${sent + filled - 1}/$total"
            http.execute(authorized = false) {
                Request.Builder().url(target).put(bytes.toRequestBody(CloudHttp.OCTET_STREAM)).header("Content-Range", range)
            }.use { response ->
                if (response.code !in 200..202) {
                    val failure = http.failure(response)
                    abort()
                    throw failure
                }
            }
            sent += filled
            filled = 0
        }

        private fun start(): HttpUrl {
            val body = JSONObject().put("item", JSONObject().put(CONFLICT, "fail"))
            val json = http.json { Request.Builder().url(item(path, "createUploadSession")).post(body.toString().toRequestBody(CloudHttp.JSON)) }
            return json.optString("uploadUrl").ifEmpty { null }?.let(api::resolve) ?: throw IOException("$SERVICE לא החזיר כתובת להעלאה")
        }

        /** סשן שנזנח — Graph מוחק אותו אחרי זמן, וכאן מייד. */
        private fun abort() {
            val target = session ?: return
            session = null
            runCatching { http.execute(authorized = false) { Request.Builder().url(target).delete() }.close() }
        }
    }

    companion object {
        const val SERVICE = "OneDrive"
        /** השרת של ה-API — בחיבור שנוצר ב"הוסף מקום". */
        const val HOST = "graph.microsoft.com"
        /** עד כאן — PUT אחד. */
        internal const val SIMPLE_MAX = 4 * 1024 * 1024
        /** כפולה של 320KB, כמו ש-upload session דורש. */
        internal const val CHUNK = 16 * 320 * 1024
        private const val PAGE_SIZE = 1000
        private const val SELECT = "id,name,size,folder,file,package,remoteItem,lastModifiedDateTime,fileSystemInfo"
        private const val CONFLICT = "@microsoft.graph.conflictBehavior"

        /**
         * `<drive>/root` בשורש, ואחרת `<drive>/root:/a/b` — ועם [suffix]: ‏`<drive>/root:/a/b:/children`. כל רכיב מקודד
         * (עברית, # ו-% בשם); ":" לא מקודד בנתיב, ו-OneDrive לא מקבל אותו בשם.
         */
        internal fun itemUrl(drive: HttpUrl, segments: List<String>, suffix: String?): HttpUrl = drive.newBuilder().apply {
            if (segments.isEmpty()) {
                addPathSegment("root")
                suffix?.let { addPathSegment(it) }
            } else {
                addPathSegment("root:")
                segments.dropLast(1).forEach { addPathSegment(it) }
                addPathSegment(segments.last() + if (suffix != null) ":" else "")
                suffix?.let { addPathSegment(it) }
            }
        }.build()
    }
}
