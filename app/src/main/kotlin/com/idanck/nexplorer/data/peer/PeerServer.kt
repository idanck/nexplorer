package com.idanck.nexplorer.data.peer

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** המכשיר הזה, כפי שמכשירים אחרים רואים אותו. */
data class PeerIdentity(val id: String, val name: String)

/** קובץ בהצעה ("שולח לך 3 קבצים"). */
data class OfferedFile(val name: String, val size: Long, val isDirectory: Boolean = false)

/** חיבור שמחכה לקוד: מה שהשרת מציג. [cancel] — "ביטול" במסך. */
class PairingRequest(val peerId: String, val peerName: String, val code: String, val cancel: () -> Unit)

/** מה שהשרת צריך מהאפליקציה — ההעדפות, המסך והקבצים. כל הקריאות מתהליכון של השרת (לא מהממשק). */
interface PeerHost {
    val identity: PeerIdentity

    /** השורש שמכשירים מחוברים רואים — האחסון הראשי. */
    val root: File

    /** התיקייה שאליה מתקבלים קבצים, יחסית ל-[root]. */
    val receiveDir: String get() = RECEIVE_DIR

    /** מפתח החיבור של [peerId]; null — לא מחובר. */
    fun keyFor(peerId: String): ByteArray?

    fun autoAccept(peerId: String): Boolean

    /** חיבור חדש מתחיל: להציג את הקוד. */
    fun onPairing(request: PairingRequest)

    /** החיבור נגמר (הצליח, נכשל או בוטל) — להסתיר את הקוד. */
    fun onPairingEnded(request: PairingRequest)

    /** המשתמש בצד השני הקליד את הקוד הנכון: לשמור. */
    fun onPaired(peerId: String, peerName: String, key: ByteArray)

    /** הצעת קבצים: חוסם עד שהמשתמש עונה (או [timeoutMillis]). true — קבל. */
    fun onOffer(peerId: String, files: List<OfferedFile>, total: Long, timeoutMillis: Long): Boolean

    companion object {
        const val RECEIVE_DIR = "Download/nexplorer"
    }
}

/**
 * השרת של "העברה בין מכשירים": פועל רק כשהאפליקציה פתוחה (ההחלטה של עידן), על פורט פנוי שמתפרסם ב-NSD.
 * חיבור לכל לקוח בתהליכון משלו (עד [MAX_CLIENTS]). כל נתיב מהרשת נפתר מתחת ל-[PeerHost.root] בלבד.
 */
class PeerServer(private val host: PeerHost, private val bind: InetAddress? = null) : Closeable {

    private val server = ServerSocket(0, 16, bind)
    private val workers: ExecutorService = ThreadPoolExecutor(1, MAX_CLIENTS, 30, TimeUnit.SECONDS, SynchronousQueue())
    private val acceptor = Thread(::acceptLoop, "peer-accept").apply { isDaemon = true }
    @Volatile private var closed = false
    @Volatile private var pairing: Socket? = null

    val port: Int get() = server.localPort

    fun start(): PeerServer {
        acceptor.start()
        return this
    }

    override fun close() {
        closed = true
        runCatching { server.close() }
        workers.shutdownNow()
    }

    private fun acceptLoop() {
        while (!closed) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                if (closed) return else continue
            }
            try {
                workers.execute { serve(socket) }
            } catch (e: RejectedExecutionException) {
                runCatching { socket.close() }
            }
        }
    }

    private fun serve(socket: Socket) {
        PeerChannel(socket).use { channel ->
            try {
                channel.timeout(HELLO_TIMEOUT)
                val hello = channel.readLine()
                if (hello.optInt("v") != VERSION) {
                    channel.writeLine(JSONObject().put("error", "גרסה אחרת של nexplorer — עדכן את שני המכשירים"))
                    return
                }
                when (hello.optString("type")) {
                    "pair" -> pair(channel, socket, hello)
                    "session" -> session(channel, hello)
                    else -> channel.writeLine(JSONObject().put("error", "בקשה לא מוכרת"))
                }
            } catch (_: IOException) {
                // הצד השני התנתק או שלח משהו לא תקין — החיבור נסגר, השרת ממשיך.
            } catch (_: RuntimeException) {
            }
        }
    }

    // --- חיבור ---

    private fun pair(channel: PeerChannel, socket: Socket, hello: JSONObject) {
        val peerId = hello.optString("id").takeIf { it.isNotBlank() && it.length <= 64 } ?: return
        val peerName = hello.optString("name").take(64).ifBlank { "מכשיר" }
        val commit = PeerChannel.unb64(hello.optString("commit"))
        // חיבור אחד בכל רגע: השני מחכה שהראשון ייגמר (אחרת שני קודים על המסך).
        synchronized(this) {
            if (pairing != null) {
                channel.writeLine(JSONObject().put("error", "חיבור אחר כבר ממתין לקוד במכשיר הזה"))
                return
            }
            pairing = socket
        }
        try {
            val keys = PeerCrypto.keyPair()
            val serverPublic = PeerCrypto.encode(keys.public)
            val serverNonce = PeerCrypto.random(PeerCrypto.NONCE_BYTES)
            channel.writeLine(
                JSONObject()
                    .put("id", host.identity.id)
                    .put("name", host.identity.name)
                    .put("pub", PeerChannel.b64(serverPublic))
                    .put("nonce", PeerChannel.b64(serverNonce)),
            )
            val reveal = channel.readLine()
            val clientPublic = PeerChannel.unb64(reveal.optString("pub"))
            val clientNonce = PeerChannel.unb64(reveal.optString("nonce"))
            if (!PeerCrypto.same(PeerCrypto.commit(clientPublic, clientNonce), commit)) return
            val shared = PeerCrypto.agree(keys.private, PeerCrypto.decodePublic(clientPublic))
            val key = PeerCrypto.pairKey(shared, clientNonce, serverNonce)
            val code = PeerCrypto.code(clientPublic, serverPublic, clientNonce, serverNonce)
            val request = PairingRequest(peerId, peerName, code) { runCatching { socket.close() } }
            host.onPairing(request)
            try {
                channel.timeout(PAIR_TIMEOUT)
                val answer = channel.readLine()
                val confirm = answer.optString("confirm").takeIf { it.isNotEmpty() } ?: return
                if (!PeerCrypto.same(PeerChannel.unb64(confirm), PeerCrypto.clientConfirm(key))) {
                    channel.writeLine(JSONObject().put("ok", false))
                    return
                }
                host.onPaired(peerId, peerName, key)
                channel.writeLine(JSONObject().put("ok", true).put("confirm", PeerChannel.b64(PeerCrypto.serverConfirm(key))))
            } finally {
                host.onPairingEnded(request)
            }
        } finally {
            synchronized(this) { pairing = null }
        }
    }

    // --- שיחה ---

    private fun session(channel: PeerChannel, hello: JSONObject) {
        val peerId = hello.optString("id")
        val key = host.keyFor(peerId)
        if (key == null) {
            channel.writeLine(JSONObject().put("error", UNPAIRED))
            return
        }
        val clientNonce = PeerChannel.unb64(hello.optString("nonce"))
        if (clientNonce.size != PeerCrypto.NONCE_BYTES) return
        val serverNonce = PeerCrypto.random(PeerCrypto.NONCE_BYTES)
        channel.writeLine(JSONObject().put("nonce", PeerChannel.b64(serverNonce)))
        val (c2s, s2c) = PeerCrypto.sessionKeys(key, clientNonce, serverNonce)
        key.fill(0)
        channel.secure(send = s2c, receive = c2s)
        channel.timeout(IDLE_TIMEOUT)
        val files = PeerFiles(host.root)
        while (true) {
            val request = channel.receiveJson()
            try {
                handle(channel, files, peerId, request)
            } catch (e: IOException) {
                // כשל בפעולה (לא בחיבור): הודעה, והחיבור ממשיך. באמצע זרם — הלקוח מקבל את השגיאה במקום מסגרת נתונים.
                if (e is SocketException || e is Broken) throw e
                channel.sendJson(JSONObject().put("error", e.message ?: "הפעולה נכשלה"))
            }
        }
    }

    private fun handle(channel: PeerChannel, files: PeerFiles, peerId: String, request: JSONObject) {
        val path = request.optString("path")
        when (request.optString("op")) {
            "list" -> {
                val array = JSONArray()
                files.list(path).forEach { array.put(it) }
                channel.sendJson(JSONObject().put("entries", array))
            }
            "stat" -> channel.sendJson(JSONObject().put("entry", files.stat(path) ?: JSONObject.NULL))
            "read" -> {
                val file = files.resolve(path)
                if (!file.isFile) throw IOException("הקובץ לא קיים")
                val offset = request.optLong("offset").coerceAtLeast(0)
                channel.sendJson(JSONObject().put("ok", true).put("size", file.length()))
                // מכאן הזרם כבר התחיל: כשל באמצע סוגר את החיבור (הלקוח לא יקבל הודעה במקום נתונים).
                broken { FileInputStream(file).use { input ->
                    var skip = offset
                    while (skip > 0) {
                        val n = input.skip(skip)
                        if (n <= 0) break
                        skip -= n
                    }
                    val buffer = ByteArray(PeerChannel.MAX_FRAME)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) channel.send(buffer, 0, n)
                    }
                } }
                channel.send(ByteArray(0))
                channel.flush()
            }
            "write" -> {
                val file = files.resolve(path)
                if (file.exists()) throw IOException("כבר קיים פריט בשם הזה")
                val parent = file.parentFile
                if (parent == null || !parent.isDirectory) throw IOException("תיקיית היעד לא קיימת")
                val output = try {
                    FileOutputStream(file)
                } catch (e: IOException) {
                    throw IOException("אין הרשאת כתיבה בתיקייה הזו", e)
                }
                channel.sendJson(JSONObject().put("ok", true))
                var complete = false
                try {
                    broken { output.use {
                        while (true) {
                            val chunk = channel.receive()
                            if (chunk.isEmpty()) break
                            it.write(chunk)
                        }
                    } }
                    complete = true
                } finally {
                    // העברה שנקטעה לא משאירה חצי קובץ.
                    if (!complete) file.delete()
                }
                request.optLong("mtime", -1).takeIf { it > 0 }?.let { file.setLastModified(it) }
                channel.sendJson(JSONObject().put("ok", true))
            }
            "mkdir" -> {
                val file = files.resolve(path)
                if (file.exists()) throw IOException("כבר קיים פריט בשם הזה")
                if (!file.mkdir()) throw IOException("יצירת התיקייה נכשלה")
                channel.sendJson(JSONObject().put("ok", true))
            }
            "delete" -> {
                val file = files.resolve(path)
                if (file == files.rootDir) throw IOException("אי אפשר למחוק את השורש")
                if (!file.exists()) throw IOException("הפריט כבר לא קיים")
                if (file.isDirectory && !file.list().isNullOrEmpty()) throw IOException("התיקייה לא ריקה")
                if (!file.delete()) throw IOException("המחיקה נכשלה")
                channel.sendJson(JSONObject().put("ok", true))
            }
            "rename" -> {
                val from = files.resolve(path)
                val to = files.resolve(request.optString("target"))
                if (from == files.rootDir) throw IOException("אי אפשר לשנות את השם של השורש")
                if (!from.exists()) throw IOException("הפריט כבר לא קיים")
                if (to.exists() && !to.path.equals(from.path, ignoreCase = true)) throw IOException("כבר קיים פריט בשם הזה")
                if (!from.renameTo(to)) throw IOException("שינוי השם נכשל")
                channel.sendJson(JSONObject().put("ok", true))
            }
            "setmtime" -> {
                files.resolve(path).setLastModified(request.optLong("time"))
                channel.sendJson(JSONObject().put("ok", true))
            }
            "space" -> {
                val root = files.rootDir
                channel.sendJson(JSONObject().put("free", root.usableSpace).put("total", root.totalSpace))
            }
            "offer" -> {
                val array = request.optJSONArray("files") ?: JSONArray()
                val offered = (0 until minOf(array.length(), MAX_OFFER)).map { i ->
                    val o = array.getJSONObject(i)
                    OfferedFile(o.optString("name").take(255), o.optLong("size"), o.optBoolean("dir"))
                }
                val total = request.optLong("total")
                val accepted = host.autoAccept(peerId) || host.onOffer(peerId, offered, total, OFFER_TIMEOUT)
                if (!accepted) {
                    channel.sendJson(JSONObject().put("accept", false))
                } else {
                    val dir = files.resolve(host.receiveDir)
                    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("אין אפשרות ליצור את תיקיית הקבלה")
                    channel.sendJson(JSONObject().put("accept", true).put("dir", host.receiveDir))
                }
            }
            else -> throw IOException("פעולה לא מוכרת")
        }
    }

    companion object {
        const val VERSION = 1
        const val SERVICE_TYPE = "_nexplorer._tcp"
        const val UNPAIRED = "unpaired"
        const val MAX_CLIENTS = 8
        private const val MAX_OFFER = 10_000
        private const val HELLO_TIMEOUT = 30_000
        const val PAIR_TIMEOUT = 120_000
        private const val IDLE_TIMEOUT = 5 * 60_000
        const val OFFER_TIMEOUT = 120_000L
    }
}

/** כשל באמצע זרם — החיבור נסגר. */
private class Broken(cause: IOException) : IOException(cause.message, cause)

private inline fun <T> broken(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw if (e is Broken) e else Broken(e)
}

/** הקבצים שהשרת חושף: נתיב יחסי ("Movies/Kids") ← קובץ מתחת לשורש בלבד. */
internal class PeerFiles(root: File) {

    val rootDir: File = root.canonicalFile

    /** [IOException] על `..`, נתיב מוחלט, או קישור שמוביל אל מחוץ לשורש. */
    fun resolve(relative: String): File {
        val parts = relative.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." || '\\' in it || '\u0000' in it }) throw IOException("נתיב לא תקין")
        val file = parts.fold(rootDir) { dir, name -> File(dir, name) }
        val canonical = file.canonicalFile
        if (canonical != rootDir && !canonical.path.startsWith(rootDir.path + File.separator)) throw IOException("נתיב מחוץ לאחסון")
        return file
    }

    fun list(relative: String): List<JSONObject> {
        val dir = resolve(relative)
        if (!dir.isDirectory) throw IOException("התיקייה לא קיימת")
        val children = dir.listFiles() ?: throw IOException("אין הרשאה לקרוא את התיקייה הזו")
        return children.map(::describe)
    }

    fun stat(relative: String): JSONObject? = resolve(relative).takeIf { it.exists() }?.let(::describe)

    private fun describe(file: File): JSONObject = JSONObject()
        .put("name", file.name)
        .put("dir", file.isDirectory)
        .put("size", if (file.isDirectory) 0 else file.length())
        .put("mtime", file.lastModified())
        .put("count", if (file.isDirectory) file.list()?.size ?: -1 else -1)
}
