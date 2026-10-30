package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.Pipe
import okio.buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * NAS של Synology דרך ה-API של DSM (File Station) — שלב 7. נתיב: `syno://<חיבור>/<שיתוף>/<תיקייה>/...`; בשורש —
 * השיתופים שהמשתמש רואה. ההתחברות: `SYNO.API.Auth` (‏session ‏FileStation), וה-sid בכל בקשה; sid שפג (106, ‏107,
 * ‏119) — התחברות מחדש פעם אחת וניסיון חוזר. https: [PinnedTls] — כמו ב-WebDAV (ברשת הביתית ניגשים בכתובת IP,
 * ו-DSM מגיש תעודה לשם אחר או חתומה עצמית).
 *
 * מה שאין ב-SMB: תמונות ממוזערות מה-NAS ([thumbnail]), ותאריך השינוי נקבע בהעלאה ([openWrite]) — אחריה אי אפשר.
 */
class SynologyFs(private val connection: NetConnection, password: CharArray?) : RemoteFs {

    // OkHttp מקבל רק String — הסיסמה נשארת בזיכרון כל עוד החיבור פתוח (כמו ב-WebDAV).
    private val passwd = String(password ?: CharArray(0))
    private val tls = PinnedTls(connection.hostKey)
    private val base: HttpUrl = HttpUrl.Builder()
        .scheme(if (connection.plainHttp) "http" else "https")
        .host(connection.host)
        .port(connection.effectivePort)
        .addPathSegment("webapi")
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .apply { if (!connection.plainHttp) tls.apply(this) }
        .build()

    @Volatile
    private var sid: String? = null
    private val loginLock = Any()

    override fun list(path: String): List<Entry> {
        if (NetPaths.segments(path).isEmpty()) {
            return shares().map { share ->
                Entry(
                    path = NetPaths.child(path, share.optString("name")),
                    name = share.optString("name"),
                    isDirectory = true,
                    size = 0,
                    childCount = -1,
                    lastModified = mtimeOf(share),
                    isHidden = false,
                    kind = FileKinds.of(share.optString("name"), true),
                )
            }
        }
        val folder = stationPath(path)
        val result = ArrayList<Entry>()
        var offset = 0
        while (true) {
            val data = call(
                "SYNO.FileStation.List", 2, "list",
                "folder_path" to folder,
                "offset" to offset.toString(),
                "limit" to PAGE.toString(),
                "additional" to ADDITIONAL,
            )
            val files = data.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) result += entryOf(path, files.getJSONObject(i))
            offset += files.length()
            if (files.length() == 0 || offset >= data.optInt("total", offset)) break
        }
        return result
    }

    override fun stat(path: String): Entry? {
        val segments = NetPaths.segments(path)
        if (segments.isEmpty()) return Entry(path, connection.label, true, 0, -1, 0, false, FileKinds.of(connection.label, true))
        val data = try {
            call("SYNO.FileStation.List", 2, "getinfo", "path" to JSONArray(listOf(stationPath(path))).toString(), "additional" to ADDITIONAL)
        } catch (e: NotFound) {
            return null
        }
        val file = data.optJSONArray("files")?.optJSONObject(0) ?: return null
        // פריט שלא נמצא חוזר בתוך הרשימה, עם קוד (408), ולא ככשל של הבקשה.
        if (file.has("code")) return null
        return entryOf(NetPaths.parent(path) ?: path, file)
    }

    /** מ-[offset]: `Range` (‏206 — נבדק מול DSM 7.4, גם דרך ממסר QuickConnect). שרת שמחזיר 200 — מדלגים כאן. */
    override fun openRead(path: String, offset: Long): InputStream {
        repeat(2) { attempt ->
            val session = session()
            val url = base.newBuilder().addPathSegment("entry.cgi")
                .addQueryParameter("api", "SYNO.FileStation.Download")
                .addQueryParameter("version", "2")
                .addQueryParameter("method", "download")
                .addQueryParameter("path", stationPath(path))
                .addQueryParameter("mode", "download")
                .addQueryParameter("_sid", session)
                .build()
            val request = Request.Builder().url(url).get().apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            val response = execute(request)
            if (offset > 0 && response.code == 416) {
                response.close()
                return ByteArrayInputStream(ByteArray(0))
            }
            // קובץ שלא נמצא (או לא נקרא): DSM עונה 502 בדף HTML, בלי קוד.
            if (!response.isJson() && response.code in setOf(404, 502)) {
                response.close()
                throw IOException("הקובץ לא נמצא ב-NAS, או שלא ניתן לקרוא אותו")
            }
            // כשל — JSON עם קוד, במקום הקובץ.
            if (response.isJson() || response.code !in setOf(200, 206)) {
                val error = response.use { errorCodeOf(it) }
                if (error in SESSION_CODES && attempt == 0) {
                    expire(session)
                    return@repeat
                }
                throw SynoErrors.file(error)
            }
            val body = response.body ?: response.use { throw IOException("תשובה ריקה מה-NAS") }
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
        throw SynoErrors.session()
    }

    /**
     * העלאה בזרם (multipart, ‏chunked — נבדק מול DSM): מה שנכתב עובר בצינור לבקשה שרצה ב-thread משלה, כמו ב-WebDAV.
     * בלי `overwrite` — DSM מסרב לקובץ קיים (414). [lastModified] — נשמר ב-NAS (`mtime`, באלפיות שנייה).
     */
    override fun openWrite(path: String, size: Long, lastModified: Long): OutputStream {
        val parent = NetPaths.parent(path) ?: throw IOException("אי אפשר לכתוב בשורש של ה-NAS")
        if (NetPaths.isShareList(parent)) throw IOException("פתח שיתוף כדי ליצור בו")
        val session = session()
        val pipe = Pipe(PIPE_BUFFER)
        val file = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                pipe.source.use { sink.writeAll(it) }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("api", "SYNO.FileStation.Upload")
            .addFormDataPart("version", "2")
            .addFormDataPart("method", "upload")
            .addFormDataPart("path", stationPath(parent))
            .addFormDataPart("create_parents", "false")
            .apply { if (lastModified > 0) addFormDataPart("mtime", lastModified.toString()) }
            .addFormDataPart("file", NetPaths.name(path), file)
            .build()
        val url = base.newBuilder().addPathSegment("entry.cgi").addQueryParameter("_sid", session).build()
        val call = client.newCall(Request.Builder().url(url).post(body).build())
        var outcome: Result<IOException?>? = null
        val worker = thread(name = "syno-upload", isDaemon = true) {
            outcome = runCatching {
                call.execute().use { response ->
                    val json = jsonOf(response)
                    when {
                        json.optBoolean("success") -> null
                        errorCodeOf(json) in SESSION_CODES -> {
                            expire(session)
                            SynoErrors.session()
                        }
                        else -> SynoErrors.file(errorCodeOf(json))
                    }
                }
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
                    finish()
                    throw IOException("ההעלאה ל-NAS נקטעה", e)
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
        val parent = NetPaths.parent(path) ?: throw IOException("אי אפשר ליצור בשורש של ה-NAS")
        call(
            "SYNO.FileStation.CreateFolder", 2, "create",
            "folder_path" to JSONArray(listOf(stationPath(parent))).toString(),
            "name" to JSONArray(listOf(NetPaths.name(path))).toString(),
            "force_parent" to "false",
        )
    }

    /** תיקייה — רק ריקה (ה-API מוחק עץ שלם; כאן, כמו בכל מקום, התוכן נמחק קודם, אחד אחד). */
    override fun delete(path: String, isDirectory: Boolean) {
        if (NetPaths.isFixed(path)) throw IOException("אי אפשר למחוק שיתוף")
        if (isDirectory && list(path).isNotEmpty()) throw IOException("התיקייה לא ריקה")
        call("SYNO.FileStation.Delete", 2, "delete", "path" to JSONArray(listOf(stationPath(path))).toString(), "recursive" to "false")
    }

    /**
     * באותה תיקייה — `Rename`. לתיקייה אחרת (גם בשיתוף אחר) — `CopyMove` בצד השרת, ואם גם השם משתנה — אחריו
     * `Rename`. היעד לא קיים ([RemoteFiles] בודק).
     */
    override fun rename(path: String, target: String) {
        val from = NetPaths.parent(path) ?: throw IOException("אי אפשר לשנות את השורש של ה-NAS")
        val to = NetPaths.parent(target) ?: throw IOException("אי אפשר להעביר לשורש של ה-NAS")
        var current = path
        if (from != to) {
            if (NetPaths.isShareList(to)) throw IOException("פתח שיתוף כדי להעביר אליו")
            // שם קיים ביעד בשם המקורי (כשהשם משתנה בדרך): DSM היה מסרב — עוברים דרך שם זמני.
            if (NetPaths.name(path) != NetPaths.name(target) && stat(NetPaths.child(to, NetPaths.name(path))) != null) {
                val temp = NetPaths.child(from, ".${NetPaths.name(target)}$MOVE_SUFFIX")
                renameInPlace(path, NetPaths.name(temp))
                current = temp
            }
            move(current, to)
            current = NetPaths.child(to, NetPaths.name(current))
        }
        if (NetPaths.name(current) != NetPaths.name(target)) renameInPlace(current, NetPaths.name(target))
    }

    /** DSM קובע את תאריך השינוי רק בהעלאה ([openWrite]); אחריה — אין API. */
    override fun setLastModified(path: String, time: Long) = Unit

    /** המקום בכרך של השיתוף (DSM מדווח לכל שיתוף). בשורש — אין. */
    override fun space(path: String): RemoteSpace? {
        val name = NetPaths.segments(path).firstOrNull() ?: return null
        val share = runCatching { shares() }.getOrNull()?.firstOrNull { it.optString("name") == name } ?: return null
        val status = share.optJSONObject("additional")?.optJSONObject("volume_status") ?: return null
        val total = status.optLong("totalspace", -1)
        val free = status.optLong("freespace", -1)
        return if (total > 0 && free >= 0) RemoteSpace(free = free, total = total) else null
    }

    /** `SYNO.FileStation.Thumb` — ‏small (ברשימה) או medium (ברשת ובגלריה). null — אין (סוג שאין לו, או כשל). */
    override fun thumbnail(path: String, large: Boolean): ByteArray? {
        repeat(2) { attempt ->
            val session = session()
            val url = base.newBuilder().addPathSegment("entry.cgi")
                .addQueryParameter("api", "SYNO.FileStation.Thumb")
                .addQueryParameter("version", "2")
                .addQueryParameter("method", "get")
                .addQueryParameter("path", stationPath(path))
                .addQueryParameter("size", if (large) "medium" else "small")
                .addQueryParameter("_sid", session)
                .build()
            execute(Request.Builder().url(url).get().build()).use { response ->
                if (response.code == 200 && response.header("Content-Type").orEmpty().startsWith("image/")) return response.body?.bytes()
                if (response.isJson() && errorCodeOf(response) in SESSION_CODES && attempt == 0) {
                    expire(session)
                    return@repeat
                }
                return null
            }
        }
        return null
    }

    override fun close() {
        val session = sid
        sid = null
        if (session != null) {
            runCatching {
                post("auth.cgi", mapOf("api" to "SYNO.API.Auth", "version" to "6", "method" to "logout", "session" to SESSION, "_sid" to session)).close()
            }
        }
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ---------- API ----------

    private fun shares(): List<JSONObject> {
        val result = ArrayList<JSONObject>()
        var offset = 0
        while (true) {
            val data = call(
                "SYNO.FileStation.List", 2, "list_share",
                "offset" to offset.toString(),
                "limit" to PAGE.toString(),
                "additional" to SHARE_ADDITIONAL,
            )
            val shares = data.optJSONArray("shares") ?: JSONArray()
            for (i in 0 until shares.length()) result += shares.getJSONObject(i)
            offset += shares.length()
            if (shares.length() == 0 || offset >= data.optInt("total", offset)) break
        }
        return result
    }

    private fun renameInPlace(path: String, name: String) {
        call(
            "SYNO.FileStation.Rename", 2, "rename",
            "path" to JSONArray(listOf(stationPath(path))).toString(),
            "name" to JSONArray(listOf(name)).toString(),
        )
    }

    /** העברה בצד השרת: משימה (`CopyMove`), וממתינים לסיום. */
    private fun move(path: String, destFolder: String) {
        copyMove(path, destFolder, remove = true, isCancelled = { false }, onProgress = {})
    }

    /**
     * `CopyMove` — משימה ברקע ב-NAS: התחלה, ושאלה על המצב עד שהיא נגמרת. [onProgress] — החלק שהושלם (0–1).
     * בוטל ([isCancelled]) — המשימה נעצרת ב-NAS, ו-[InterruptedIOException] נזרק.
     */
    private fun copyMove(path: String, destFolder: String, remove: Boolean, isCancelled: () -> Boolean, onProgress: (Double) -> Unit) {
        val task = call(
            "SYNO.FileStation.CopyMove", 3, "start",
            "path" to JSONArray(listOf(stationPath(path))).toString(),
            "dest_folder_path" to stationPath(destFolder),
            "remove_src" to remove.toString(),
        ).optString("taskid").ifEmpty { throw IOException(if (remove) "ה-NAS לא התחיל את ההעברה" else "ה-NAS לא התחיל את ההעתקה") }
        val status = waitTask("SYNO.FileStation.CopyMove", 3, task, isCancelled) { onProgress(it.optDouble("progress", 0.0)) }
        status.optJSONObject("error")?.let { throw SynoErrors.file(errorCodeOf(JSONObject().put("error", it))) }
        onProgress(1.0)
    }

    /**
     * ממתין למשימה ברקע ב-NAS (`CopyMove`, ‏`DirSize`): שאלה על המצב, בהמתנה שגדלה עד [POLL_MAX_MS]. ה-taskid
     * נשלח כמחרוזת JSON (במירכאות): בלי מירכאות DSM מפרש מזהה כמו "1793374970510E9474" כמספר, והמשימה
     * "לא נמצאת" — נמצא מול NAS אמיתי.
     */
    private fun waitTask(api: String, version: Int, task: String, isCancelled: () -> Boolean, onStatus: (JSONObject) -> Unit = {}): JSONObject {
        var wait = POLL_FIRST_MS
        while (true) {
            if (isCancelled()) {
                runCatching { call(api, version, "stop", "taskid" to quoted(task)) }
                throw InterruptedIOException("בוטל")
            }
            val status = call(api, version, "status", "taskid" to quoted(task))
            onStatus(status)
            if (status.optBoolean("finished")) return status
            Thread.sleep(wait)
            wait = (wait * 2).coerceAtMost(POLL_MAX_MS)
        }
    }

    /**
     * העתקה בצד השרת, בלי שהבתים עוברים בטלפון: [path] אל `[destDir]/[name]`. אותו שם — `CopyMove` ישירות; שם
     * אחר (כבר קיים פריט בשם המקורי ביעד) — דרך תיקייה זמנית ביעד: העתקה אליה, שינוי שם, העברה ליעד, מחיקתה.
     * [onProgress] — החלק שהושלם (0–1).
     */
    fun copyOnServer(path: String, destDir: String, name: String, isCancelled: () -> Boolean, onProgress: (Double) -> Unit) {
        if (name == NetPaths.name(path)) {
            copyMove(path, destDir, remove = false, isCancelled, onProgress)
            return
        }
        val temp = NetPaths.child(destDir, ".nexplorer-copy-${System.nanoTime().toString(36)}")
        mkdir(temp)
        try {
            copyMove(path, temp, remove = false, isCancelled, onProgress)
            val copied = NetPaths.child(temp, NetPaths.name(path))
            renameInPlace(copied, name)
            move(NetPaths.child(temp, name), destDir)
        } finally {
            runCatching { call("SYNO.FileStation.Delete", 2, "delete", "path" to JSONArray(listOf(stationPath(temp))).toString(), "recursive" to "true") }
        }
    }

    /** גודל ומספר פריטים של עץ — `DirSize`, ב-NAS עצמו (בלי לעבור על כל תיקייה). התיקיות שנבחרו לא נספרות. */
    fun treeStats(paths: List<String>, isCancelled: () -> Boolean): RemoteFiles.TreeStats {
        val task = call("SYNO.FileStation.DirSize", 2, "start", "path" to JSONArray(paths.map(::stationPath)).toString())
            .optString("taskid").ifEmpty { throw IOException("ה-NAS לא התחיל את החישוב") }
        val status = waitTask("SYNO.FileStation.DirSize", 2, task, isCancelled)
        return RemoteFiles.TreeStats(status.optLong("total_size"), status.optInt("num_file"), status.optInt("num_dir"))
    }

    /**
     * חיפוש עמוק — ה-NAS מחפש (`Search`), בכל אחת מ-[folders] וכל מה שמתחתיה. [pattern] — כמו בתחביר שלנו: בלי
     * תו כללי — הכלה ("img" ⊂ "IMG_101.jpg"), עם — glob; בלי רישיות. [minSize] (בתים) ו-[modifiedAfter] (אלפיות שנייה)
     * — 0 בלי. התוצאות עוברות ל-[onFiles] בדפים, כשהן מגיעות; false ממנו — עצירה.
     */
    fun search(
        folders: List<String>,
        pattern: String,
        minSize: Long,
        modifiedAfter: Long,
        isCancelled: () -> Boolean,
        onFiles: (List<Entry>) -> Boolean,
    ) {
        val root = NetPaths.root(connection.protocol, connection.id)
        for (folder in folders) {
            val task = call(
                "SYNO.FileStation.Search", 2, "start",
                "folder_path" to stationPath(folder),
                "recursive" to "true",
                "pattern" to pattern.ifBlank { "*" },
                *(if (minSize > 0) arrayOf("size_from" to minSize.toString()) else emptyArray()),
                *(if (modifiedAfter > 0) arrayOf("mtime_from" to (modifiedAfter / 1000).toString()) else emptyArray()),
            ).optString("taskid").ifEmpty { throw IOException("ה-NAS לא התחיל את החיפוש") }
            try {
                var offset = 0
                var wait = POLL_FIRST_MS
                while (true) {
                    if (isCancelled()) return
                    val page = call(
                        "SYNO.FileStation.Search", 2, "list",
                        "taskid" to quoted(task),
                        "offset" to offset.toString(),
                        "limit" to PAGE.toString(),
                        "additional" to ADDITIONAL,
                    )
                    val files = page.optJSONArray("files") ?: JSONArray()
                    if (files.length() > 0) {
                        val entries = (0 until files.length()).map { i ->
                            val file = files.getJSONObject(i)
                            val path = root + file.optString("path")
                            entryOf(NetPaths.parent(path) ?: root, file)
                        }
                        offset += files.length()
                        if (!onFiles(entries)) return
                        wait = POLL_FIRST_MS
                        continue
                    }
                    if (page.optBoolean("finished") && offset >= page.optInt("total", offset)) break
                    Thread.sleep(wait)
                    wait = (wait * 2).coerceAtMost(POLL_MAX_MS)
                }
            } finally {
                runCatching { call("SYNO.FileStation.Search", 2, "stop", "taskid" to quoted(task)) }
                runCatching { call("SYNO.FileStation.Search", 2, "clean", "taskid" to quoted(task)) }
            }
        }
    }

    private fun quoted(task: String): String = JSONObject.quote(task)

    /**
     * בקשה ל-`entry.cgi`. כשל עם קוד של sid (פג, נותק) — התחברות מחדש וניסיון אחד נוסף. [NotFound] — 408.
     */
    private fun call(api: String, version: Int, method: String, vararg params: Pair<String, String>): JSONObject {
        repeat(2) { attempt ->
            val session = session()
            val json = post("entry.cgi", mapOf("api" to api, "version" to version.toString(), "method" to method, "_sid" to session) + params).use { jsonOf(it) }
            if (json.optBoolean("success")) return json.optJSONObject("data") ?: JSONObject()
            val code = errorCodeOf(json)
            if (code in SESSION_CODES && attempt == 0) {
                expire(session)
                return@repeat
            }
            if (code == SynoErrors.NO_SUCH_FILE) throw NotFound(SynoErrors.file(code).message.orEmpty())
            throw SynoErrors.file(code)
        }
        throw SynoErrors.session()
    }

    private fun session(): String = sid ?: synchronized(loginLock) { sid ?: login().also { sid = it } }

    /** sid שפג — רק אם עוד לא הוחלף (בקשה מקבילה כבר התחברה מחדש). */
    private fun expire(session: String) {
        synchronized(loginLock) { if (sid == session) sid = null }
    }

    private fun login(): String {
        val json = post(
            "auth.cgi",
            mapOf(
                "api" to "SYNO.API.Auth",
                "version" to "6",
                "method" to "login",
                "account" to connection.user,
                "passwd" to passwd,
                "session" to SESSION,
                "format" to "sid",
            ),
        ).use { jsonOf(it) }
        if (!json.optBoolean("success")) throw SynoErrors.auth(errorCodeOf(json))
        return json.optJSONObject("data")?.optString("sid")?.ifEmpty { null } ?: throw IOException("תשובה לא צפויה מה-NAS")
    }

    private fun post(cgi: String, params: Map<String, String>): Response {
        val form = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(base.newBuilder().addPathSegment(cgi).build()).post(form).build())
    }

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        throw map(e)
    }

    private fun jsonOf(response: Response): JSONObject {
        if (response.code == 404) throw IOException("זה לא NAS של Synology — בדוק את הכתובת והפורט (${connection.effectivePort})")
        val text = response.body?.string().orEmpty()
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException(if (response.code in 200..299) "תשובה לא מובנת — זו הכתובת של DSM?" else "ה-NAS החזיר ${response.code}", e)
        }
    }

    private fun errorCodeOf(response: Response): Int = runCatching { errorCodeOf(JSONObject(response.body?.string().orEmpty())) }.getOrDefault(-1)

    private fun Response.isJson(): Boolean = header("Content-Type").orEmpty().contains("json")

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw map(e)
    }

    private fun map(e: Throwable): IOException {
        if (e is HostKeyUntrusted || e is NotFound) return e as IOException
        tls.untrusted(e)?.let { return it }
        val chain = generateSequence(e) { it.cause }.toList()
        if (chain.any { it is javax.net.ssl.SSLHandshakeException || it is javax.net.ssl.SSLPeerUnverifiedException }) {
            return IOException("החיבור המאובטח ל-NAS נכשל", e)
        }
        NetErrors.network(e, connection)?.let { return it }
        if (chain.any { it is javax.net.ssl.SSLException || it is java.net.SocketException }) return IOException("החיבור ל-NAS נותק", e)
        return e as? IOException ?: IOException(e.message ?: "שגיאה מה-NAS", e)
    }

    // ---------- נתיבים ----------

    /** הנתיב של File Station: "/<שיתוף>/<תיקייה>/<שם>". */
    private fun stationPath(path: String): String = "/" + NetPaths.segments(path).joinToString("/")

    private fun entryOf(parent: String, file: JSONObject): Entry {
        val name = file.optString("name")
        val isDir = file.optBoolean("isdir")
        val extra = file.optJSONObject("additional")
        return Entry(
            path = NetPaths.child(parent, name),
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0 else extra?.optLong("size", 0) ?: 0,
            childCount = -1,
            lastModified = mtimeOf(file),
            isHidden = name.startsWith("."),
            kind = FileKinds.of(name, isDir),
        )
    }

    private fun mtimeOf(file: JSONObject): Long = (file.optJSONObject("additional")?.optJSONObject("time")?.optLong("mtime", 0) ?: 0) * 1000

    /** "לא נמצא" — [stat] מחזיר null במקום לזרוק. */
    class NotFound(message: String) : IOException(message)

    companion object {
        private const val SESSION = "FileStation"
        private const val PAGE = 1000
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L
        private const val PIPE_BUFFER = 256 * 1024L
        private const val POLL_FIRST_MS = 200L
        private const val POLL_MAX_MS = 2_000L
        private const val MOVE_SUFFIX = ".nxmove"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val ADDITIONAL = JSONArray(listOf("size", "time")).toString()
        private val SHARE_ADDITIONAL = JSONArray(listOf("time", "volume_status")).toString()

        /** sid שפג (106), נותק בהתחברות אחרת (107), או לא מוכר (119). */
        private val SESSION_CODES = setOf(106, 107, 119)

        /** הקוד של כשל: של הפריט הראשון שנכשל (1100 ← 414 "קיים"), ואם אין — של הבקשה. -1 — אין קוד. */
        internal fun errorCodeOf(json: JSONObject): Int {
            val error = json.optJSONObject("error") ?: return -1
            val nested = error.optJSONArray("errors")?.optJSONObject(0)?.optInt("code", -1) ?: -1
            return if (nested > 0) nested else error.optInt("code", -1)
        }
    }
}

/** קודי השגיאה של DSM ← עברית. מתוך המדריך של File Station API, ומה שנבדק מול NAS אמיתי. */
internal object SynoErrors {
    const val NO_SUCH_FILE = 408

    fun session() = IOException("ההתחברות ל-NAS פגה — נסה שוב")

    /** כשל בהתחברות (`SYNO.API.Auth`) — הקודים כאן שונים מאלה של הקבצים. */
    fun auth(code: Int) = IOException(
        when (code) {
            400 -> "שם המשתמש או הסיסמה שגויים"
            401 -> "החשבון מושבת ב-DSM"
            402 -> "לחשבון אין הרשאה ל-File Station"
            403, 404, 406 -> "החשבון מוגדר עם אימות דו-שלבי — בגרסה הזו אפשר להתחבר רק עם חשבון בלי קוד אימות"
            407 -> "הכתובת של המכשיר חסומה ב-DSM (יותר מדי ניסיונות כושלים)"
            408, 409, 410 -> "הסיסמה פגה — החלף אותה ב-DSM ונסה שוב"
            else -> "ההתחברות ל-NAS נכשלה (${code.coerceAtLeast(0)})"
        },
    )

    fun file(code: Int): IOException = IOException(
        when (code) {
            105, 407, 403 -> "אין הרשאה לפעולה הזו ב-NAS"
            NO_SUCH_FILE -> "הפריט לא נמצא ב-NAS"
            411 -> "השיתוף הזה לקריאה בלבד"
            412, 413 -> "השם ארוך מדי"
            414 -> "כבר קיים פריט בשם הזה"
            415 -> "חרגת מהמכסה שלך ב-NAS"
            416 -> "אין מקום פנוי ב-NAS"
            418, 419, 420 -> "השם לא חוקי ב-NAS"
            421 -> "הפריט תפוס ב-NAS — נסה שוב"
            402 -> "ה-NAS עמוס — נסה שוב"
            in SESSION -> "ההתחברות ל-NAS פגה — נסה שוב"
            else -> "הפעולה נכשלה ב-NAS (${code.coerceAtLeast(0)})"
        },
    )

    private val SESSION = setOf(106, 107, 119)
}
