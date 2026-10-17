package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.DriveNames
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Google Drive ("האחסון שלי") מעל Drive API v3. הנתיב לפי שמות ([DriveNames]); מטמון של רשימות התיקיות
 * מתרגם אותו ל-ID. פריט שלא נמצא — הרשימה של התיקייה נטענת מחדש פעם אחת (נוסף ממכשיר אחר). מחיקה — לסל.
 * Google Docs נקראים בייצוא (`files/<id>/export`), וקיצור דרך — דרך היעד שלו. בלי תלות באנדרואיד.
 */
class DriveFs(private val connection: NetConnection, auth: CloudAuth) : RemoteFs {

    private val http = CloudHttp(SERVICE, auth)
    private val api: HttpUrl = CloudHttp.baseUrl(connection)

    /** פריט ב-Drive. [name] — כמו שמוצג ובנתיב; [driveName] — כמו ב-Drive. קיצור דרך: [target] ו-[targetMime]. */
    private data class Item(
        val id: String,
        val name: String,
        val driveName: String,
        val mimeType: String,
        val size: Long,
        val modified: Long,
        val target: String? = null,
        val targetMime: String? = null,
    ) {
        val isShortcut: Boolean get() = mimeType == DriveNames.SHORTCUT
        val isDirectory: Boolean get() = (targetMime ?: mimeType) == DriveNames.FOLDER

        /** התיקייה שמדפדפים בה: קיצור — היעד. */
        val folderId: String get() = target ?: id
    }

    private val lock = Any()
    private val listings = HashMap<String, List<Item>>()
    private var rootId: String? = null

    override fun list(path: String): List<Entry> {
        val item = resolve(path)?.item ?: throw CloudNotFound("התיקייה לא נמצאה ב-$SERVICE")
        if (!item.isDirectory) throw IOException("\"${item.name}\" אינו תיקייה")
        return load(item.folderId).map { entryOf(NetPaths.child(path, it.name), it) }
    }

    override fun stat(path: String): Entry? {
        val item = resolve(path)?.item ?: return null
        return entryOf(path, item, NetPaths.name(path).ifEmpty { connection.label })
    }

    override fun openRead(path: String, offset: Long): InputStream {
        val item = resolve(path)?.item ?: throw CloudNotFound("הפריט לא נמצא ב-$SERVICE")
        if (item.isDirectory) throw IOException("\"${item.name}\" הוא תיקייה")
        val id = item.target ?: item.id
        val export = DriveNames.exportOf(item.targetMime ?: item.mimeType)
        val url = if (export != null) {
            files(id, "export").newBuilder().addQueryParameter("mimeType", export.mimeType).build()
        } else {
            files(id).newBuilder().addQueryParameter("alt", "media").build()
        }
        return http.download(offset, ranged = export == null) { Request.Builder().url(url).get() }
    }

    /**
     * הגוש הראשון נאסף בזיכרון: קובץ קטן — בקשת multipart אחת; גדול — resumable upload בגושים של [CHUNK]
     * (כפולה של 256KB). Drive לא מסרב לשם קיים — הבדיקה כאן, לפני היצירה.
     */
    override fun openWrite(path: String, size: Long): OutputStream {
        val parent = folderOf(path)
        val name = NetPaths.name(path)
        if (findIn(parent, name) != null) throw IOException("כבר קיים פריט בשם הזה")
        return Upload(parent, name)
    }

    override fun mkdir(path: String) {
        val parent = folderOf(path)
        val name = NetPaths.name(path)
        if (findIn(parent, name) != null) throw IOException("כבר קיים פריט בשם הזה")
        val metadata = JSONObject().put("name", name).put("mimeType", DriveNames.FOLDER).put("parents", JSONArray().put(parent))
        http.json { Request.Builder().url(files(null)).post(metadata.toString().toRequestBody(CloudHttp.JSON)) }
        forget(parent)
    }

    override fun delete(path: String, isDirectory: Boolean) {
        val (item, parent) = resolve(path)?.takeIf { it.parent != null } ?: throw CloudNotFound("הפריט לא נמצא ב-$SERVICE")
        // לסל — עם כל מה שבתוך התיקייה; כאן רק ריקה, כמו בשאר המקומות. קיצור — רק הקיצור.
        if (item.isDirectory && !item.isShortcut && load(item.id).isNotEmpty()) throw IOException("התיקייה לא ריקה")
        patch(item.id, JSONObject().put("trashed", true))
        forget(parent!!)
        forget(item.id)
    }

    override fun rename(path: String, target: String) {
        if (!NetPaths.sameShare(path, target)) throw IOException("אפשר להעביר בשינוי שם רק בתוך אותו חשבון")
        val (item, from) = resolve(path)?.takeIf { it.parent != null } ?: throw CloudNotFound("הפריט לא נמצא ב-$SERVICE")
        val to = folderOf(target)
        val url = files(item.id).newBuilder().apply {
            if (to != from) {
                addQueryParameter("addParents", to)
                addQueryParameter("removeParents", from)
            }
        }.build()
        val body = JSONObject().put("name", DriveNames.driveNameFor(NetPaths.name(target), item.targetMime ?: item.mimeType))
        http.json { Request.Builder().url(url).patch(body.toString().toRequestBody(CloudHttp.JSON)) }
        forget(from!!)
        forget(to)
    }

    override fun setLastModified(path: String, time: Long) {
        runCatching {
            val (item, parent) = resolve(path) ?: return
            patch(item.id, JSONObject().put("modifiedTime", Instant.ofEpochMilli(time).toString()))
            parent?.let(::forget)
        }
    }

    /** המכסה של החשבון. בלי `limit` — בלי הגבלה, ובלי פס תפוסה. */
    override fun space(path: String): RemoteSpace? = runCatching {
        val url = api.newBuilder().addPathSegments("drive/v3/about").addQueryParameter("fields", "storageQuota(limit,usage)").build()
        val quota = http.json { Request.Builder().url(url).get() }.getJSONObject("storageQuota")
        val limit = quota.optString("limit").toLongOrNull()?.takeIf { it > 0 } ?: return@runCatching null
        val usage = quota.optString("usage").toLongOrNull() ?: return@runCatching null
        RemoteSpace(free = (limit - usage).coerceAtLeast(0), total = limit)
    }.getOrNull()

    /** הכתובת של החשבון — לשם במגירה, אחרי ההתחברות ב"הוסף מקום". */
    fun account(): String {
        val url = api.newBuilder().addPathSegments("drive/v3/about").addQueryParameter("fields", "user(emailAddress)").build()
        return http.json { Request.Builder().url(url).get() }.optJSONObject("user")?.optString("emailAddress")?.ifEmpty { null }
            ?: throw IOException("$SERVICE לא החזיר את החשבון")
    }

    override fun close() = http.close()

    // ---------- נתיב ← ID ----------

    /** [parent] — ה-ID של התיקייה שהפריט נמצא בה; null בשורש של החיבור. */
    private data class Resolved(val item: Item, val parent: String?)

    private fun resolve(path: String): Resolved? {
        val root = Item(rootId(), connection.label, "", DriveNames.FOLDER, 0, 0)
        var current = Resolved(root, null)
        for (segment in NetPaths.segments(path)) {
            if (!current.item.isDirectory) return null
            val folder = current.item.folderId
            val child = findIn(folder, segment) ?: return null
            current = Resolved(child, folder)
        }
        return current
    }

    /** מהמטמון; לא נמצא — הרשימה נטענת מחדש פעם אחת. */
    private fun findIn(folder: String, name: String): Item? {
        val cached = synchronized(lock) { listings[folder] }
        cached?.firstOrNull { it.name == name }?.let { return it }
        return load(folder).firstOrNull { it.name == name }
    }

    /** התיקייה של [path] (ה-ID). לא קיימת — "תיקיית היעד לא קיימת". */
    private fun folderOf(path: String): String {
        val parentPath = NetPaths.parent(path) ?: throw IOException("אי אפשר ליצור פריט מעל השורש")
        val parent = resolve(parentPath)?.item?.takeIf { it.isDirectory } ?: throw IOException("תיקיית היעד לא קיימת ב-$SERVICE")
        return parent.folderId
    }

    private fun rootId(): String {
        synchronized(lock) { rootId }?.let { return it }
        val id = http.json { Request.Builder().url(files("root").newBuilder().addQueryParameter("fields", "id").build()).get() }.getString("id")
        synchronized(lock) { rootId = id }
        return id
    }

    /** כל הפריטים בתיקייה (כל הדפים), עם השמות של [DriveNames], ונשמרים במטמון. */
    private fun load(folder: String): List<Item> {
        val raw = ArrayList<JSONObject>()
        var page: String? = null
        do {
            val url = files(null).newBuilder()
                .addQueryParameter("q", "'$folder' in parents and trashed = false")
                .addQueryParameter("fields", "nextPageToken,files($FIELDS)")
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .addQueryParameter("spaces", "drive")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val json = http.json { Request.Builder().url(url).get() }
            val array = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until array.length()) raw += array.getJSONObject(i)
            page = json.optString("nextPageToken").ifEmpty { null }
        } while (page != null)
        val visible = raw.filter { o ->
            val mime = o.optString("mimeType")
            DriveNames.visible(mime) && (mime != DriveNames.SHORTCUT || DriveNames.visible(o.optJSONObject("shortcutDetails")?.optString("targetMimeType").orEmpty()))
        }
        val names = DriveNames.assign(
            visible,
            driveName = { it.optString("name") },
            mimeType = { o -> o.optJSONObject("shortcutDetails")?.optString("targetMimeType")?.takeIf { o.optString("mimeType") == DriveNames.SHORTCUT } ?: o.optString("mimeType") },
            created = { time(it.optString("createdTime")) },
            id = { it.optString("id") },
        )
        val items = visible.mapIndexed { i, o ->
            val shortcut = o.optJSONObject("shortcutDetails")?.takeIf { o.optString("mimeType") == DriveNames.SHORTCUT }
            Item(
                id = o.getString("id"),
                name = names[i],
                driveName = o.optString("name"),
                mimeType = o.optString("mimeType"),
                size = o.optString("size").toLongOrNull() ?: 0,
                modified = time(o.optString("modifiedTime")),
                target = shortcut?.optString("targetId")?.ifEmpty { null },
                targetMime = shortcut?.optString("targetMimeType")?.ifEmpty { null },
            )
        }
        synchronized(lock) { listings[folder] = items }
        return items
    }

    private fun forget(folder: String) {
        synchronized(lock) { listings.remove(folder) }
    }

    private fun patch(id: String, body: JSONObject) {
        http.json { Request.Builder().url(files(id)).patch(body.toString().toRequestBody(CloudHttp.JSON)) }
    }

    /** `drive/v3/files[/<id>[/<suffix>]]`. */
    private fun files(id: String?, suffix: String? = null): HttpUrl = api.newBuilder().addPathSegments("drive/v3/files").apply {
        id?.let { addPathSegment(it) }
        suffix?.let { addPathSegment(it) }
    }.build()

    private fun entryOf(path: String, item: Item, name: String = item.name): Entry = Entry(
        path = path,
        name = name,
        isDirectory = item.isDirectory,
        size = if (item.isDirectory) 0 else item.size,
        childCount = -1,
        lastModified = item.modified,
        isHidden = name.startsWith("."),
        kind = FileKinds.of(name, item.isDirectory),
        isLink = item.isShortcut,
    )

    // ---------- העלאה ----------

    private inner class Upload(private val parent: String, private val name: String) : OutputStream() {
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        /** כמה בתים Drive כבר שמר (resumable). */
        private var offset = 0L
        private var session: HttpUrl? = null
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var from = off
            var left = len
            while (left > 0) {
                if (filled == buffer.size) send(last = false)
                val n = minOf(left, buffer.size - filled)
                System.arraycopy(b, from, buffer, filled, n)
                filled += n
                from += n
                left -= n
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            if (session == null) multipart() else send(last = true)
            forget(parent)
        }

        private fun metadata(): JSONObject = JSONObject().put("name", name).put("parents", JSONArray().put(parent))

        private fun multipart() {
            val bytes = buffer.copyOf(filled)
            val url = api.newBuilder().addPathSegments("upload/drive/v3/files")
                .addQueryParameter("uploadType", "multipart").addQueryParameter("fields", "id").build()
            http.json {
                val body = MultipartBody.Builder().setType(MULTIPART_RELATED)
                    .addPart(metadata().toString().toRequestBody(CloudHttp.JSON))
                    .addPart(bytes.toRequestBody(CloudHttp.OCTET_STREAM))
                    .build()
                Request.Builder().url(url).post(body)
            }
        }

        /** גוש אחד. לא אחרון: הגודל הכולל עוד לא ידוע ("*"); Drive עונה 308 עם מה שנשמר. */
        private fun send(last: Boolean) {
            val target = session ?: start().also { session = it }
            val bytes = buffer.copyOf(filled)
            val end = offset + filled - 1
            val range = when {
                filled == 0 -> "bytes */$offset"
                last -> "bytes $offset-$end/${offset + filled}"
                else -> "bytes $offset-$end/*"
            }
            http.execute {
                Request.Builder().url(target).put(bytes.toRequestBody(CloudHttp.OCTET_STREAM)).header("Content-Range", range)
            }.use { response ->
                when {
                    last && response.code in 200..201 -> {
                        offset += filled
                        filled = 0
                    }
                    !last && response.code == 308 -> {
                        // "bytes=0-524287": מה שנשמר עד עכשיו. מה שלא נשמר נשאר בגוש ונשלח שוב.
                        val saved = response.header("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L
                        val kept = (saved - offset).toInt().coerceIn(0, filled)
                        System.arraycopy(buffer, kept, buffer, 0, filled - kept)
                        filled -= kept
                        offset += kept
                        if (kept == 0) throw IOException("ההעלאה ל-$SERVICE לא מתקדמת")
                    }
                    else -> throw http.failure(response)
                }
            }
        }

        private fun start(): HttpUrl {
            val url = api.newBuilder().addPathSegments("upload/drive/v3/files")
                .addQueryParameter("uploadType", "resumable").addQueryParameter("fields", "id").build()
            return http.execute {
                Request.Builder().url(url)
                    .post(metadata().toString().toRequestBody(CloudHttp.JSON))
                    .header("X-Upload-Content-Type", CloudHttp.OCTET_STREAM.toString())
            }.use { response ->
                if (response.code !in 200..299) throw http.failure(response)
                response.header("Location")?.let(url::resolve) ?: throw IOException("$SERVICE לא החזיר כתובת להעלאה")
            }
        }
    }

    companion object {
        const val SERVICE = "Google Drive"
        /** השרת של ה-API — בחיבור שנוצר ב"הוסף מקום". */
        const val HOST = "www.googleapis.com"
        /** כפולה של 256KB, כמו ש-resumable upload דורש. */
        internal const val CHUNK = 8 * 1024 * 1024
        private const val PAGE_SIZE = 1000
        private const val FIELDS = "id,name,mimeType,size,modifiedTime,createdTime,shortcutDetails(targetId,targetMimeType)"
        private val MULTIPART_RELATED = "multipart/related".toMediaType()

        private fun time(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }
}
