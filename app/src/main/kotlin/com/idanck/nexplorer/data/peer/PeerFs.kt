package com.idanck.nexplorer.data.peer

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteFs
import com.idanck.nexplorer.data.net.RemoteSpace
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress

/**
 * מכשיר מחובר כמקום ברשת (`peer://<מזהה חיבור>/<נתיב באחסון הראשי שלו>`): עיון, העתקה לשני הכיוונים ונגינה ישירה.
 * [address] — הכתובת שה-NSD מצא עכשיו למכשיר ([NetConnection.host] הוא המזהה שלו); null — לא נראה ברשת.
 * הפעולות הקצרות על שיחה אחת (עם ניסיון חוזר אחד כשהיא נפלה); כל קריאה וכתיבה של קובץ — שיחה משלה, כדי שנגן שקורא
 * לא יחסום רשימה.
 */
class PeerFs(
    private val connection: NetConnection,
    key: ByteArray,
    private val me: () -> PeerIdentity,
    private val address: (peerId: String) -> InetSocketAddress?,
) : RemoteFs {

    private val key = key.copyOf()
    private val lock = Any()
    private var session: PeerSession? = null

    private fun where(): InetSocketAddress = address(connection.host) ?: throw IOException(PeerConnect.NOT_VISIBLE)

    private fun open(): PeerSession = PeerSession.open(where(), me(), key)

    private fun relative(path: String): String = NetPaths.segments(path).joinToString("/")

    /** [retry] — רק לפעולה שבטוח לחזור עליה (קריאה). */
    private fun call(request: JSONObject, retry: Boolean): JSONObject = synchronized(lock) {
        var attempt = 0
        while (true) {
            val s = session ?: open().also { session = it }
            try {
                return s.call(request)
            } catch (e: PeerSession.PeerError) {
                throw e
            } catch (e: IOException) {
                s.close()
                session = null
                if (!retry || attempt++ > 0) throw if (e.message.isNullOrBlank()) IOException(PeerConnect.NOT_VISIBLE, e) else e
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun entry(parent: String, o: JSONObject): Entry {
        val name = o.getString("name")
        val dir = o.optBoolean("dir")
        return Entry(
            path = NetPaths.child(parent, name),
            name = name,
            isDirectory = dir,
            size = if (dir) 0 else o.optLong("size"),
            childCount = if (dir) o.optInt("count", -1) else -1,
            lastModified = o.optLong("mtime"),
            isHidden = name.startsWith('.'),
            kind = FileKinds.of(name, dir),
        )
    }

    override fun list(path: String): List<Entry> {
        val array = call(JSONObject().put("op", "list").put("path", relative(path)), retry = true).optJSONArray("entries") ?: JSONArray()
        return (0 until array.length()).map { entry(path, array.getJSONObject(it)) }
    }

    override fun stat(path: String): Entry? {
        val o = call(JSONObject().put("op", "stat").put("path", relative(path)), retry = true).optJSONObject("entry") ?: return null
        val parent = NetPaths.parent(path)
        return if (parent == null) {
            Entry(path, connection.label, true, 0, o.optInt("count", -1), o.optLong("mtime"), false, FileKinds.of("", true))
        } else {
            entry(parent, o)
        }
    }

    override fun openRead(path: String, offset: Long): InputStream {
        val s = open()
        try {
            s.call(JSONObject().put("op", "read").put("path", relative(path)).put("offset", offset))
        } catch (e: IOException) {
            s.close()
            throw e
        }
        return object : InputStream() {
            private var chunk = ByteArray(0)
            private var pos = 0
            private var ended = false

            private fun fill(): Boolean {
                while (!ended && pos >= chunk.size) {
                    chunk = s.channel.receive()
                    pos = 0
                    if (chunk.isEmpty()) ended = true
                }
                return !ended || pos < chunk.size
            }

            override fun read(): Int = if (!fill()) -1 else chunk[pos++].toInt() and 0xff

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (!fill()) return -1
                val n = minOf(len, chunk.size - pos)
                System.arraycopy(chunk, pos, b, off, n)
                pos += n
                return n
            }

            override fun available(): Int = if (ended) 0 else chunk.size - pos

            override fun close() = s.close()
        }
    }

    override fun openWrite(path: String, size: Long, lastModified: Long): OutputStream {
        val s = open()
        try {
            s.call(JSONObject().put("op", "write").put("path", relative(path)).put("size", size).put("mtime", lastModified))
        } catch (e: IOException) {
            s.close()
            throw e
        }
        return object : OutputStream() {
            private val buffer = ByteArray(PeerChannel.MAX_FRAME)
            private var used = 0
            private var closed = false

            private fun drain() {
                if (used > 0) {
                    s.channel.send(buffer, 0, used)
                    used = 0
                }
            }

            override fun write(b: Int) {
                if (used == buffer.size) drain()
                buffer[used++] = b.toByte()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off
                var left = len
                while (left > 0) {
                    if (used == buffer.size) drain()
                    val n = minOf(left, buffer.size - used)
                    System.arraycopy(b, o, buffer, used, n)
                    used += n
                    o += n
                    left -= n
                }
            }

            override fun flush() {
                drain()
                s.channel.flush()
            }

            /** הסוף: מסגרת ריקה, ואישור שהקובץ נשמר. */
            override fun close() {
                if (closed) return
                closed = true
                try {
                    drain()
                    s.channel.send(ByteArray(0))
                    s.channel.flush()
                    val reply = s.channel.receiveJson()
                    reply.optString("error").takeIf { it.isNotEmpty() }?.let { throw IOException(it) }
                } finally {
                    s.close()
                }
            }
        }
    }

    override fun mkdir(path: String) {
        call(JSONObject().put("op", "mkdir").put("path", relative(path)), retry = false)
    }

    override fun delete(path: String, isDirectory: Boolean) {
        call(JSONObject().put("op", "delete").put("path", relative(path)).put("dir", isDirectory), retry = false)
    }

    override fun rename(path: String, target: String) {
        call(JSONObject().put("op", "rename").put("path", relative(path)).put("target", relative(target)), retry = false)
    }

    override fun setLastModified(path: String, time: Long) {
        runCatching { call(JSONObject().put("op", "setmtime").put("path", relative(path)).put("time", time), retry = true) }
    }

    override fun space(path: String): RemoteSpace? = runCatching {
        val o = call(JSONObject().put("op", "space"), retry = true)
        RemoteSpace(free = o.optLong("free"), total = o.optLong("total"))
    }.getOrNull()

    /**
     * "שלח למכשיר": מבקש מהמשתמש בצד השני לקבל. התיקייה שאליה להעתיק (`peer://…/Download/nexplorer`) — או null כשדחה.
     * מחכה לתשובה עד שתי דקות.
     */
    fun offer(files: List<OfferedFile>): String? {
        val s = open()
        try {
            s.timeout(PeerServer.OFFER_TIMEOUT.toInt() + 15_000)
            val array = JSONArray()
            files.forEach { array.put(JSONObject().put("name", it.name).put("size", it.size).put("dir", it.isDirectory)) }
            val reply = s.call(JSONObject().put("op", "offer").put("files", array).put("total", files.sumOf { it.size }))
            if (!reply.optBoolean("accept")) return null
            val dir = reply.optString("dir")
            return dir.split('/').filter { it.isNotEmpty() }.fold(connection.root) { acc, name -> NetPaths.child(acc, name) }
        } finally {
            s.close()
        }
    }

    override fun close() {
        synchronized(lock) {
            session?.close()
            session = null
        }
    }
}
