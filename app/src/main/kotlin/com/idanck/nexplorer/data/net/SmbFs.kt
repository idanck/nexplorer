package com.idanck.nexplorer.data.net

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * חיבור SMB 2/3 אחד (smbj; רשימת השיתופים — srvsvc דרך dcerpc). חיבור, התחברות ושיתופים נפתחים בפעם
 * הראשונה שצריך אותם ונשמרים. שרת שסגר חיבור שעמד (NAS שנרדם, ניתוק Wi-Fi) — מתחברים מחדש פעם אחת
 * ומנסים שוב; כשל שני הוא כשל.
 *
 * [password] מועתקת לכאן ונמחקת ב-[close].
 */
class SmbFs(private val connection: NetConnection, password: CharArray?) : RemoteFs {

    private val password: CharArray = password?.copyOf() ?: CharArray(0)
    private val client = SMBClient(
        SmbConfig.builder()
            // גם זמן ההתחברות: שרת שלא עונה לא תוקע את הרשימה דקה.
            .withSoTimeout(SOCKET_TIMEOUT_S, TimeUnit.SECONDS)
            .withTimeout(REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
            .build(),
    )
    private val lock = Any()
    private var conn: Connection? = null
    private var session: Session? = null
    private val shares = HashMap<String, DiskShare>()
    /** עולה בכל [reset] — שתי פעולות שנכשלו יחד על חיבור שמת לא מאפסות זו את החיבור החדש של זו. */
    private var generation = 0

    override fun list(path: String): List<Entry> {
        val segments = NetPaths.segments(path)
        if (segments.isEmpty()) return listShares(path)
        return withShare(path) { share, inner ->
            share.list(inner).mapNotNull { info ->
                val name = info.fileName
                if (name == "." || name == "..") null
                else entryOf(NetPaths.child(path, name), name, info.fileAttributes, info.endOfFile, info.lastWriteTime.toEpochMillis())
            }
        }
    }

    override fun stat(path: String): Entry? {
        val segments = NetPaths.segments(path)
        // השורש של החיבור והשיתוף עצמו — תיקיות. השיתוף קיים אם אפשר להתחבר אליו.
        if (segments.size <= 1) {
            if (segments.isNotEmpty()) {
                try {
                    retrying { share(segments.first()) }
                } catch (e: NotFound) {
                    return null
                }
            }
            return Entry(path, NetPaths.name(path).ifEmpty { connection.label }, true, 0, -1, 0, false, FileKinds.of("", true))
        }
        return try {
            withShare(path) { share, inner ->
                val info = share.getFileInformation(inner)
                val name = segments.last()
                entryOf(path, name, info.basicInformation.fileAttributes, info.standardInformation.endOfFile, info.basicInformation.lastWriteTime.toEpochMillis())
            }
        } catch (e: NotFound) {
            null
        }
    }

    override fun openRead(path: String, offset: Long): InputStream = withShare(path) { share, inner ->
        val file = share.openFile(inner, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        // smbj מדלג בלי לקרוא: הבקשה הראשונה כבר מ-offset.
        val input = file.inputStream.apply { if (offset > 0) skip(offset) }
        object : FilterInputStream(input) {
            override fun read(): Int = guard { super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
            override fun close() {
                try {
                    super.close()
                } finally {
                    runCatching { file.close() }
                }
            }
        }
    }

    override fun openWrite(path: String): OutputStream = withShare(path) { share, inner ->
        val file = share.openFile(
            inner,
            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.FILE_WRITE_ATTRIBUTES),
            null,
            SMB2ShareAccess.ALL,
            // FILE_CREATE נכשל כשהקובץ קיים — אף פעם לא דורסים.
            SMB2CreateDisposition.FILE_CREATE,
            null,
        )
        object : FilterOutputStream(file.outputStream) {
            // FilterOutputStream כותב בית-בית כברירת מחדל.
            override fun write(b: ByteArray, off: Int, len: Int) = guard { out.write(b, off, len) }
            override fun write(b: Int) = guard { out.write(b) }
            override fun close() {
                try {
                    guard { out.close() }
                } finally {
                    runCatching { file.close() }
                }
            }
        }
    }

    override fun mkdir(path: String) = withShare(path) { share, inner -> share.mkdir(inner) }

    override fun delete(path: String, isDirectory: Boolean) = withShare(path) { share, inner ->
        if (isDirectory) share.rmdir(inner, false) else share.rm(inner)
    }

    override fun rename(path: String, target: String) {
        if (!NetPaths.sameShare(path, target)) throw IOException("אפשר להעביר בשינוי שם רק בתוך אותו שיתוף")
        withShare(path) { share, inner ->
            share.open(inner, EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use {
                it.rename(innerOf(target), false)
            }
        }
    }

    override fun setLastModified(path: String, time: Long) {
        runCatching {
            withShare(path) { share, inner ->
                share.open(inner, EnumSet.of(AccessMask.FILE_WRITE_ATTRIBUTES), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use {
                    val keep = FileBasicInformation.DONT_SET
                    it.setFileInformation(FileBasicInformation(keep, keep, FileTime.ofEpochMillis(time), keep, 0))
                }
            }
        }
    }

    override fun space(path: String): RemoteSpace? {
        if (NetPaths.segments(path).isEmpty()) return null
        return runCatching {
            withShare(path) { share, _ ->
                share.shareInformation.let { RemoteSpace(free = it.callerFreeSpace, total = it.totalSpace) }
            }
        }.getOrNull()?.takeIf { it.total > 0 }
    }

    override fun close() {
        synchronized(lock) { reset() }
        password.fill('\u0000')
        runCatching { client.close() }
    }

    // ---------- חיבור ----------

    private fun listShares(path: String): List<Entry> = retrying {
        // ההתחברות קודם: סיסמה שגויה היא "סיסמה שגויה", לא "אין רשימה".
        val session = session()
        val shares = try {
            ServerService(SMBTransportFactories.SRVSVC.getTransport(session)).shares1
        } catch (e: Throwable) {
            // Throwable: על תשובה פגומה dcerpc זורק java.rmi.UnmarshalException, שאין באנדרואיד —
            // ומה שמגיע הוא NoClassDefFoundError, לא Exception.
            if (e is Exception && isStale(e)) throw e
            throw IOException("השרת לא מחזיר את רשימת השיתופים — הזן את שם השיתוף בפרטי החיבור", e)
        }
        shares
            // תיקיות משותפות בלבד: בלי IPC$, מדפסות ושיתופי ניהול (C$, ADMIN$).
            .filter { (it.type.toLong() and STYPE_MASK) == STYPE_DISKTREE && (it.type.toLong() and STYPE_SPECIAL) == 0L && !it.netName.endsWith("$") }
            .map { Entry(NetPaths.child(path, it.netName), it.netName, true, 0, -1, 0, false, FileKinds.of(it.netName, true)) }
    }

    private fun session(): Session = synchronized(lock) {
        session?.takeIf { conn?.isConnected == true }?.let { return it }
        reset()
        val c = client.connect(connection.host, connection.effectivePort)
        try {
            val auth = if (connection.user.isBlank()) AuthenticationContext.anonymous()
            else AuthenticationContext(connection.user, password, connection.domain.ifBlank { null })
            c.authenticate(auth).also {
                conn = c
                session = it
            }
        } catch (e: Exception) {
            runCatching { c.close() }
            throw e
        }
    }

    private fun share(name: String): DiskShare = synchronized(lock) {
        shares[name]?.takeIf { it.isConnected }?.let { return it }
        val share = session().connectShare(name) as? DiskShare
            ?: throw IOException("\"$name\" אינו שיתוף של קבצים")
        shares[name] = share
        share
    }

    private fun reset() {
        generation++
        shares.values.forEach { runCatching { it.close() } }
        shares.clear()
        runCatching { session?.close() }
        runCatching { conn?.close(true) }
        session = null
        conn = null
    }

    private fun <T> withShare(path: String, block: (DiskShare, String) -> T): T {
        val shareName = NetPaths.segments(path).firstOrNull() ?: throw IOException("בחר שיתוף")
        return retrying { block(share(shareName), innerOf(path)) }
    }

    /**
     * חיבור שנסגר בצד השרת: פעם אחת מחדש. רק אם כבר היה חיבור — סיסמה שגויה או שרת כבוי לא מנסים
     * פעמיים (פעמיים זמן המתנה).
     */
    private fun <T> retrying(block: () -> T): T {
        val (established, started) = synchronized(lock) { (session != null) to generation }
        return try {
            block()
        } catch (e: Exception) {
            if (!established || !isStale(e)) throw SmbErrors.map(e, connection)
            synchronized(lock) { if (generation == started) reset() }
            try {
                block()
            } catch (again: Exception) {
                throw SmbErrors.map(again, connection)
            }
        }
    }

    private fun isStale(e: Throwable): Boolean {
        // החוט נקטע (דילוג בנגן מבטל את הטעינה ב-interrupt), לא החיבור: איפוס היה מפיל גם העברה שרצה בו.
        // smbj מחזיר את הדגל לפני שהוא זורק — לפעמים בלי InterruptedException בשרשרת (המתנה ל-credits).
        if (Thread.currentThread().isInterrupted || generateSequence(e) { it.cause }.any { it is InterruptedException }) return false
        if (e is SMBApiException) {
            return e.status == NtStatus.STATUS_NETWORK_NAME_DELETED || e.status == NtStatus.STATUS_USER_SESSION_DELETED ||
                e.status == NtStatus.STATUS_NETWORK_SESSION_EXPIRED
        }
        // השרת ניתק: smbj סוגר את החיבור ואת השיתופים שלו ברקע, ולפעמים לפני הקריאה ("DiskShare has
        // already been closed"). אחרת — שגיאת תעבורה בתוך הקריאה.
        if (conn?.isConnected != true) return true
        return generateSequence(e) { it.cause }.any {
            it is TransportException || it is java.net.SocketException || (it is SMBRuntimeException && it !is SMBApiException)
        }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw SmbErrors.map(e, connection)
    }

    private fun innerOf(path: String): String = NetPaths.segments(path).drop(1).joinToString("\\")

    private fun entryOf(path: String, name: String, attributes: Long, size: Long, modified: Long): Entry {
        val isDir = EnumWithValue.EnumUtils.isSet(attributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
        return Entry(
            path = path,
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0 else size,
            // לספור פריטים בכל תיקייה = בקשה לכל שורה. ברשת — לא.
            childCount = -1,
            lastModified = modified,
            isHidden = name.startsWith(".") || EnumWithValue.EnumUtils.isSet(attributes, FileAttributes.FILE_ATTRIBUTE_HIDDEN),
            kind = FileKinds.of(name, isDir),
        )
    }

    /** "לא נמצא" — [stat] מחזיר null במקום לזרוק. */
    class NotFound(message: String) : IOException(message)

    private companion object {
        const val SOCKET_TIMEOUT_S = 15L
        const val REQUEST_TIMEOUT_S = 60L
        const val STYPE_MASK = 0x0FFFFFFFL
        const val STYPE_DISKTREE = 0L
        const val STYPE_SPECIAL = 0x80000000L
    }
}
