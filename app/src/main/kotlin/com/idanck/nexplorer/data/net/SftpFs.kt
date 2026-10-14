package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.PacketType
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * חיבור SFTP אחד (sshj). כמו [SmbFs]: החיבור נפתח בפעם הראשונה ונשמר, ושרת שסגר חיבור שעמד —
 * מתחברים מחדש פעם אחת.
 *
 * המפתח של השרת נבדק מול [NetConnection.hostKey] בכל התחברות. שרת שעוד לא אושר, או שהמפתח שלו השתנה —
 * [HostKeyUntrusted] עם המפתח שהוצג, ו"בדוק והתחבר" שואל את המשתמש.
 *
 * [password] — סיסמת המשתמש, או של המפתח כשיש [privateKey] (קובץ OpenSSH / PEM / PuTTY). שניהם מועתקים
 * לכאן ונמחקים ב-[close]; sshj מקבל את המפתח כטקסט, והעותק שלו נשאר בזיכרון כל עוד החיבור פתוח.
 */
class SftpFs(private val connection: NetConnection, password: CharArray?, privateKey: CharArray?) : RemoteFs {

    private val password: CharArray? = password?.copyOf()
    private val privateKey: CharArray? = privateKey?.copyOf()
    private val usesKey = privateKey != null
    private val lock = Any()
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null
    /** עולה בכל [reset] — כמו ב-[SmbFs]. */
    private var generation = 0

    override fun list(path: String): List<Entry> = retrying {
        val client = client()
        val dir = remoteOf(path)
        client.ls(dir).mapNotNull { info ->
            val name = info.name
            if (name == "." || name == "..") return@mapNotNull null
            val child = NetPaths.child(path, name)
            if (info.attributes.type != FileMode.Type.SYMLINK) return@mapNotNull entryOf(child, name, info.attributes, isLink = false)
            // קישור: מוצג כמו מה שהוא מצביע עליו (תיקייה או קובץ); קישור שבור — קובץ ריק, כמו ב-ls.
            entryOf(child, name, runCatching { client.stat(remoteOf(child)) }.getOrNull() ?: info.attributes, isLink = true)
        }
    }

    override fun stat(path: String): Entry? = retrying {
        val client = client()
        val remote = remoteOf(path)
        val own = lstatOrNull(client, remote) ?: return@retrying null
        val name = NetPaths.name(path).ifEmpty { connection.label }
        if (own.type != FileMode.Type.SYMLINK) return@retrying entryOf(path, name, own, isLink = false)
        entryOf(path, name, client.statExistence(remote) ?: own, isLink = true)
    }

    private fun lstatOrNull(client: SFTPClient, remote: String): FileAttributes? = try {
        client.lstat(remote)
    } catch (e: SFTPException) {
        if (e.statusCode == Response.StatusCode.NO_SUCH_FILE) null else throw e
    }

    override fun openRead(path: String): InputStream = retrying {
        val file = client().open(remoteOf(path), EnumSet.of(OpenMode.READ))
        val stream = try {
            file.ReadAheadRemoteFileInputStream(UNCONFIRMED)
        } catch (e: Exception) {
            runCatching { file.close() }
            throw e
        }
        object : FilterInputStream(stream) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() {
                try {
                    super.close()
                } finally {
                    closeQuietly(file)
                }
            }
        }
    }

    override fun openWrite(path: String): OutputStream {
        val file = retrying {
            try {
                // EXCL: נכשל כשהקובץ קיים — אף פעם לא דורסים.
                client().open(remoteOf(path), EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL))
            } catch (e: SFTPException) {
                throw existsOr(path, e)
            }
        }
        return object : FilterOutputStream(file.RemoteFileOutputStream(0L, UNCONFIRMED)) {
            // FilterOutputStream כותב בית-בית כברירת מחדל. sshj שולח כל write כבקשה אחת, ושרת מסרב לבקשה
            // גדולה מדי (OpenSSH: 256KB, והחוצץ של ההעתקה בגודל הזה) — לכן בגושים.
            override fun write(b: ByteArray, off: Int, len: Int) = guard {
                var at = off
                while (at < off + len) {
                    val n = minOf(WRITE_CHUNK, off + len - at)
                    out.write(b, at, n)
                    at += n
                }
            }
            override fun write(b: Int) = guard { out.write(b) }
            override fun close() {
                try {
                    // הכתיבות שעוד לא אושרו: כשל שלהן (דיסק מלא) נראה רק כאן.
                    guard { out.close() }
                } finally {
                    closeQuietly(file)
                }
            }
        }
    }

    override fun mkdir(path: String) = retrying {
        try {
            client().mkdir(remoteOf(path))
        } catch (e: SFTPException) {
            throw existsOr(path, e)
        }
    }

    override fun delete(path: String, isDirectory: Boolean) = retrying {
        val client = client()
        try {
            if (isDirectory) client.rmdir(remoteOf(path)) else client.rm(remoteOf(path))
        } catch (e: SFTPException) {
            // OpenSSH מחזיר "Failure" כללי על תיקייה שלא ריקה.
            if (isDirectory && e.statusCode == Response.StatusCode.FAILURE && client.ls(remoteOf(path)).any { it.name != "." && it.name != ".." }) {
                throw Explained("התיקייה לא ריקה", e)
            }
            throw e
        }
    }

    override fun rename(path: String, target: String) {
        if (!NetPaths.sameShare(path, target)) throw Explained("אפשר להעביר בשינוי שם רק בתוך אותו שרת")
        retrying {
            val client = client()
            try {
                // RENAME של SFTP 3: נכשל כשהיעד קיים — בלי דריסה.
                client.rename(remoteOf(path), remoteOf(target))
            } catch (e: SFTPException) {
                if (e.statusCode != Response.StatusCode.FAILURE) throw e
                if (lstatOrNull(client, remoteOf(target)) != null) throw Explained("כבר קיים פריט בשם הזה", e)
                // בין שני כוננים בשרת (בית ← דיסק USB) שינוי שם לא אפשרי — מעתיקים ומוחקים.
                if (lstatOrNull(client, remoteOf(path)) != null) throw RemoteCrossDevice("השרת לא יכול להעביר לשם בשינוי שם", e)
                throw e
            }
        }
    }

    override fun setLastModified(path: String, time: Long) {
        runCatching {
            retrying {
                val seconds = time / 1000
                client().setattr(remoteOf(path), FileAttributes.Builder().withAtimeMtime(seconds, seconds).build())
            }
        }
    }

    /** `statvfs@openssh.com` — OpenSSH מדווח; שרת אחר — בלי פס תפוסה. */
    override fun space(path: String): RemoteSpace? = runCatching {
        retrying {
            val engine = client().sftpEngine
            if (!engine.supportsServerExtension("statvfs", "openssh.com")) return@retrying null
            val response = engine.request(engine.newExtendedRequest("statvfs@openssh.com").putString(remoteOf(path)))
                .retrieve(engine.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            response.ensurePacketTypeIs(PacketType.EXTENDED_REPLY)
            response.readUInt64() // f_bsize
            val fragment = response.readUInt64()
            val blocks = response.readUInt64()
            response.readUInt64() // f_bfree
            val available = response.readUInt64()
            RemoteSpace(free = available * fragment, total = blocks * fragment)
        }
    }.getOrNull()?.takeIf { it.total > 0 }

    /** תיקיית הבית בשרת ("home/idan") — כששדה "תיקייה" ריק. */
    fun home(): String = retrying { client().canonicalize(".").trim('/') }

    override fun close() {
        synchronized(lock) { reset() }
        password?.fill('\u0000')
        privateKey?.fill('\u0000')
    }

    // ---------- חיבור ----------

    private fun client(): SFTPClient = synchronized(lock) {
        sftp?.takeIf { ssh?.isConnected == true && ssh?.isAuthenticated == true }?.let { return it }
        reset()
        SshCrypto.install()
        val verifier = Verifier(connection.hostKey)
        // keepalive@openssh.com: חיבור שמת בשקט (מעבר Wi-Fi) נסגר אחרי כמה תשובות שלא הגיעו, ולא נשאר תקוע.
        val client = SSHClient(DefaultConfig().apply { keepAliveProvider = KeepAliveProvider.KEEP_ALIVE })
        try {
            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_S
            client.connectTimeout = CONNECT_TIMEOUT_MS
            client.timeout = READ_TIMEOUT_MS
            client.addHostKeyVerifier(verifier)
            try {
                client.connect(connection.host, connection.effectivePort)
            } catch (e: Exception) {
                verifier.offered?.let { offered -> if (offered != connection.hostKey) throw HostKeyUntrusted(offered, connection.hostKey != null) }
                throw e
            }
            try {
                client.auth(connection.user, authMethods(client))
            } catch (e: net.schmizz.sshj.userauth.UserAuthException) {
                val locked = generateSequence<Throwable>(e) { it.cause }.any { it is com.hierynomus.sshj.common.KeyDecryptionFailedException }
                if (locked && password == null) throw Explained("המפתח מוגן בסיסמה — הזן אותה בשדה הסיסמה", e)
                throw e
            }
            val sftp = client.newSFTPClient()
            ssh = client
            this.sftp = sftp
            sftp
        } catch (e: Exception) {
            closeQuietly(client)
            throw e
        }
    }

    private fun authMethods(client: SSHClient): List<AuthMethod> {
        val finder = password?.let(::Secret)
        privateKey?.let { key ->
            // המפתח נטען מהטקסט (לא מקובץ), והסיסמה שלו — אם יש — מ-[password].
            val provider = try {
                client.loadKeys(String(key), null, finder)
            } catch (e: net.schmizz.sshj.common.SSHException) {
                // "No provider available for Unknown key file" — לא קובץ מפתח.
                throw Explained("הקובץ שנבחר אינו מפתח פרטי של SSH", e)
            }
            return listOf(AuthPublickey(provider))
        }
        val secret = finder ?: throw Explained("צריך סיסמה או מפתח")
        // יש שרתים שמאפשרים סיסמה רק דרך keyboard-interactive (PAM).
        return listOf(AuthPassword(secret), AuthKeyboardInteractive(PasswordResponseProvider(secret)))
    }

    private fun reset() {
        generation++
        sftp?.let(::closeQuietly)
        ssh?.let(::closeQuietly)
        sftp = null
        ssh = null
    }

    private fun <T> retrying(block: () -> T): T {
        val (established, started) = synchronized(lock) { (sftp != null) to generation }
        return try {
            block()
        } catch (e: Exception) {
            if (!established || !isStale(e)) throw SftpErrors.map(e, connection, usesKey)
            synchronized(lock) { if (generation == started) reset() }
            try {
                block()
            } catch (again: Exception) {
                throw SftpErrors.map(again, connection, usesKey)
            }
        }
    }

    private fun isStale(e: Throwable): Boolean {
        // השרת ניתק: sshj סוגר את החיבור ברקע. אחרת — שגיאת תעבורה בתוך הבקשה.
        if (ssh?.isConnected != true) return true
        val chain = generateSequence(e) { it.cause }
        // בקשה שלא נענתה בזמן — כנראה חיבור שמת בלי שנסגר; חיבור חדש פעם אחת.
        if (chain.any { it is java.util.concurrent.TimeoutException || it is java.net.SocketTimeoutException }) return true
        if (e is SFTPException) return e.statusCode == Response.StatusCode.NO_CONNECTION || e.statusCode == Response.StatusCode.CONNECITON_LOST
        return chain.any {
            it is net.schmizz.sshj.transport.TransportException || it is net.schmizz.sshj.connection.ConnectionException || it is java.net.SocketException
        }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw SftpErrors.map(e, connection, usesKey)
    }

    /** כשל ביצירה: אם יש כבר פריט בשם — "כבר קיים" (OpenSSH מחזיר "Failure" כללי). */
    private fun existsOr(path: String, e: SFTPException): Exception {
        if (e.statusCode == Response.StatusCode.FILE_ALREADY_EXISTS) return Explained("כבר קיים פריט בשם הזה", e)
        if (e.statusCode == Response.StatusCode.FAILURE && runCatching { lstatOrNull(client(), remoteOf(path)) }.getOrNull() != null) {
            return Explained("כבר קיים פריט בשם הזה", e)
        }
        return e
    }

    private fun remoteOf(path: String): String = "/" + NetPaths.segments(path).joinToString("/")

    private fun entryOf(path: String, name: String, attributes: FileAttributes, isLink: Boolean): Entry {
        val isDir = attributes.type == FileMode.Type.DIRECTORY
        return Entry(
            path = path,
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0 else attributes.size.coerceAtLeast(0),
            childCount = -1,
            lastModified = attributes.mtime * 1000,
            isHidden = name.startsWith("."),
            kind = FileKinds.of(name, isDir),
            isLink = isLink,
        )
    }

    private fun closeQuietly(resource: java.io.Closeable) {
        runCatching { resource.close() }
    }

    /** סיסמה שנמסרת ל-sshj בכל התחברות מחדש — עותק בכל פעם, כי sshj מוחק את מה שקיבל. */
    private class Secret(private val value: CharArray) : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = value.copyOf()
        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    /** משווה את המפתח שהשרת הציג לזה שאושר; את המפתח שהוצג שומר — לשאלה "לסמוך על השרת?". */
    private class Verifier(private val trusted: String?) : HostKeyVerifier {
        @Volatile
        var offered: String? = null

        override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
            val encoded = SshHostKeys.encode(key)
            offered = encoded
            return encoded == trusted
        }

        // סוג המפתח שאושר קודם בהצעה: שרת עם כמה מפתחות מציג את אותו אחד בכל פעם.
        override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> =
            trusted?.substringBefore(' ')?.let { listOf(it) } ?: emptyList()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val KEEPALIVE_S = 15
        /** בקשות קריאה וכתיבה שבדרך בבת אחת — בלי זה כל 32KB מחכה לתשובה מהשרת. */
        const val UNCONFIRMED = 16
        /** הגודל שכל שרת SFTP מקבל בבקשת כתיבה אחת. */
        const val WRITE_CHUNK = 32 * 1024
    }
}

/** הודעה שכבר נוסחה ב-[SftpFs] — [SftpErrors] מעביר אותה כמו שהיא. */
internal class Explained(message: String, cause: Throwable? = null) : IOException(message, cause)

/** השרת עוד לא אושר, או שהמפתח שלו השתנה ([changed]). [offered] — "ssh-ed25519 AAAA…". */
class HostKeyUntrusted(val offered: String, val changed: Boolean) : IOException(
    if (changed) "המפתח של השרת השתנה — ערוך את החיבור כדי לאשר אותו מחדש" else "צריך לאשר את המפתח של השרת",
)
