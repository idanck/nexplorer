package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * כל המקומות ברשת: לפי המזהה שבנתיב ([NetPaths]) מוצא את החיבור השמור, ופותח לו [RemoteFs] אחד שנשמר
 * עד שהחיבור משתנה או נמחק. [connectionOf] — מההעדפות; [decrypt] — פותח את הצפנים שבחיבור (הסיסמה
 * והמפתח הפרטי) ב-Keystore; [cloudAuth] — האסימונים של חיבור ענן. בבדיקות מול שרת — ישירות.
 */
class RemoteFiles(
    private val connectionOf: (String) -> NetConnection?,
    private val decrypt: (String) -> CharArray,
    private val cloudAuth: (NetConnection) -> CloudAuth = { throw IOException("אין התחברות לענן") },
) {
    private val open = HashMap<String, Pair<NetConnection, RemoteFs>>()

    /** [IOException] כשהחיבור נמחק (מועדף ישן) או שהסיסמה או המפתח שלו לא נקראים. */
    fun fsFor(path: String): RemoteFs {
        val id = NetPaths.connectionId(path) ?: throw IOException("נתיב רשת לא תקין")
        val connection = connectionOf(id) ?: throw IOException("החיבור הזה נמחק מהמגירה")
        synchronized(open) {
            open[id]?.let { (known, fs) -> if (known == connection) return fs else fs.close() }
            val password = connection.secret?.let(decrypt)
            val fs = try {
                val key = connection.keySecret?.let(decrypt)
                try {
                    create(connection, password, key, if (connection.protocol.cloud) cloudAuth(connection) else null)
                } finally {
                    key?.fill('\u0000')
                }
            } finally {
                password?.fill('\u0000')
            }
            open[id] = connection to fs
            return fs
        }
    }

    /**
     * חיבור שנערך או נמחק: נסגר, והפעם הבאה מתחברת מחדש בפרטים החדשים. ב-IO: הסגירה מדברת עם השרת,
     * ומחכה להתחברות שבאמצע (עד זמן ההמתנה).
     */
    suspend fun forget(id: String) = withContext(Dispatchers.IO) {
        synchronized(open) { open.remove(id) }?.second?.let { runCatching { it.close() } }
        Unit
    }

    suspend fun list(path: String): Result<List<Entry>> = io { fsFor(path).list(path) }

    suspend fun stat(path: String): Entry? = withContext(Dispatchers.IO) { fsFor(path).stat(path) }

    suspend fun space(path: String): RemoteSpace? = withContext(Dispatchers.IO) { runCatching { fsFor(path).space(path) }.getOrNull() }

    /** החיבור של [path] כשהוא NAS של Synology — לחיפוש, להעתקה ולחישוב גדלים בצד השרת. */
    fun synology(path: String): SynologyFs? =
        if (NetPaths.protocolOf(path) == NetProtocol.Synology) fsFor(path) as? SynologyFs else null

    /** גודל תיקייה ב-NAS (Synology — DirSize); null — לא Synology, או כשל. ל"גודל תיקיות" ברשימה. */
    suspend fun folderSize(path: String): Long? = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        runCatching { synology(path)?.treeStats(listOf(path)) { !context.isActive }?.bytes }.getOrNull()
    }

    /** תמונה ממוזערת מהשרת (Synology); null — אין, או כשל. */
    suspend fun thumbnail(path: String, large: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { fsFor(path).thumbnail(path, large) }.getOrNull()
    }

    suspend fun createFolder(parent: String, name: String): Result<String> = io {
        val target = NetPaths.child(parent, name)
        validate(parent, name)
        val fs = fsFor(parent)
        if (fs.stat(target) != null) throw IOException("כבר קיים פריט בשם הזה")
        fs.mkdir(target)
        target
    }

    suspend fun createFile(parent: String, name: String): Result<String> = io {
        val target = NetPaths.child(parent, name)
        validate(parent, name)
        fsFor(parent).openWrite(target).close()
        target
    }

    suspend fun rename(path: String, newName: String): Result<String> = io {
        val parent = NetPaths.parent(path) ?: throw IOException(if (NetPaths.isShareList(path)) "אי אפשר לשנות שם של שיתוף" else "אי אפשר לשנות את השם של השורש בשרת")
        if (NetPaths.isShare(path)) throw IOException("אי אפשר לשנות שם של שיתוף")
        validate(parent, newName)
        val target = NetPaths.child(parent, newName)
        if (target == path) return@io path
        val fs = fsFor(path)
        // שינוי רישיות בלבד: השרת (כמו Windows) רואה את השם החדש כקיים.
        if (!target.equals(path, ignoreCase = true) && fs.stat(target) != null) throw IOException("כבר קיים פריט בשם הזה")
        fs.rename(path, target)
        target
    }

    /** גודל ומספר פריטים של עץ ברשת — לחלון המאפיינים. כל תיקייה היא בקשה; הביטול נבדק בין בקשות. */
    suspend fun treeStats(paths: List<String>): TreeStats = withContext(Dispatchers.IO) {
        // Synology: ה-NAS סופר בעצמו (DirSize) — בקשה אחת במקום תיקייה אחר תיקייה. רשימת השיתופים — כמו בכל מקום.
        val context = currentCoroutineContext()
        val syno = paths.firstOrNull()?.let { synology(it) }
        if (syno != null && paths.none { NetPaths.isShareList(it) } && paths.all { NetPaths.connectionId(it) == NetPaths.connectionId(paths.first()) }) {
            return@withContext syno.treeStats(paths) { !context.isActive }
        }
        var bytes = 0L
        var files = 0
        var folders = 0
        val stack = ArrayDeque<String>()
        for (path in paths) {
            val fs = fsFor(path)
            // רשימת השיתופים: לא סורקים את כל השרת — רק סופרים שיתופים.
            if (NetPaths.isShareList(path)) {
                folders += fs.list(path).size
                continue
            }
            val root = fs.stat(path) ?: continue
            if (root.isDirectory && !root.isLink) stack.addLast(path) else {
                files++
                bytes += root.size
            }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                for (child in fs.list(stack.removeLast())) {
                    // קישור — לא נכנסים דרכו (קישור ל-/ היה סורק את כל השרת).
                    if (child.isDirectory && !child.isLink) {
                        folders++
                        stack.addLast(child.path)
                    } else {
                        files++
                        bytes += child.size
                    }
                }
            }
        }
        TreeStats(bytes, files, folders)
    }

    data class TreeStats(val bytes: Long, val files: Int, val folders: Int)

    /**
     * עותק מקומי זמני לפתיחה או לשיתוף: `cache/remote/<uuid>/<שם>` — תיקייה לכל הורדה, כך ששמות לא
     * מתנגשים והשם המקורי נשמר. נמחק בהפעלה הבאה ([clearStale]); ביטול באמצע מוחק מייד.
     */
    suspend fun download(cacheDir: File, path: String, onBytes: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        clearStale(cacheDir)
        val dir = File(File(cacheDir, CACHE_DIR), UUID.randomUUID().toString())
        if (!dir.mkdirs()) throw IOException("לא ניתן ליצור עותק זמני")
        val target = File(dir, NetPaths.name(path))
        try {
            val fs = fsFor(path)
            fs.openRead(path).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
            fs.stat(path)?.lastModified?.takeIf { it > 0 }?.let(target::setLastModified)
            target
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    private var cleared = false

    /** העותקים מהריצה הקודמת — פעם אחת לתהליך, כמו בקבצים מוצפנים שנפתחו. */
    @Synchronized
    fun clearStale(cacheDir: File) {
        if (cleared) return
        File(cacheDir, CACHE_DIR).deleteRecursively()
        cleared = true
    }

    private fun validate(parent: String, name: String) {
        val protocol = NetPaths.protocolOf(parent) ?: NetProtocol.Smb
        NetPaths.validateName(protocol, name)?.let { throw IOException(it) }
        if (NetPaths.isShareList(parent)) throw IOException("פתח שיתוף כדי ליצור בו")
    }

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    companion object {
        const val CACHE_DIR = "remote"
        private const val BUFFER = 256 * 1024

        fun create(connection: NetConnection, password: CharArray?, privateKey: CharArray? = null, auth: CloudAuth? = null): RemoteFs = when (connection.protocol) {
            NetProtocol.Smb -> SmbFs(connection, password)
            NetProtocol.Sftp -> SftpFs(connection, password, privateKey)
            NetProtocol.WebDav -> WebDavFs(connection, password)
            NetProtocol.Synology -> SynologyFs(connection, password)
            NetProtocol.GoogleDrive -> DriveFs(connection, auth ?: throw IOException("אין התחברות ל-Google Drive"))
            NetProtocol.OneDrive -> OneDriveFs(connection, auth ?: throw IOException("אין התחברות ל-OneDrive"))
        }

        /**
         * "התחבר" בענן: החשבון (השם במגירה, [NetConnection.user]) ורשימת השורש — באסימון של ההתחברות שעכשיו,
         * לפני שהחיבור נשמר.
         */
        suspend fun testCloud(connection: NetConnection, auth: CloudAuth): ConnectCheck = withContext(Dispatchers.IO) {
            try {
                create(connection, null, null, auth).use { fs ->
                    val account = when (fs) {
                        is DriveFs -> fs.account()
                        is OneDriveFs -> fs.account()
                        else -> connection.user
                    }
                    fs.list(connection.root)
                    ConnectCheck.Ok(connection.copy(user = account))
                }
            } catch (e: IOException) {
                ConnectCheck.Failed(e.message ?: "ההתחברות נכשלה")
            } catch (e: RuntimeException) {
                ConnectCheck.Failed(e.message ?: "ההתחברות נכשלה")
            }
        }

        /**
         * "בדוק והתחבר": חיבור זמני בפרטים שהוזנו — רשימת השורש (השיתופים, השיתוף שנבחר, או התיקייה ב-SFTP).
         * SFTP עם [NetConnection.HOME] — נפתח בתיקיית הבית, והחיבור שחוזר כבר מכיל אותה.
         */
        suspend fun test(connection: NetConnection, password: CharArray?, privateKey: CharArray? = null): ConnectCheck = withContext(Dispatchers.IO) {
            try {
                create(connection, password, privateKey).use { fs ->
                    val resolved = if (fs is SftpFs && connection.share == NetConnection.HOME) connection.copy(share = fs.home()) else connection
                    fs.list(resolved.root)
                    ConnectCheck.Ok(resolved)
                }
            } catch (e: HostKeyUntrusted) {
                ConnectCheck.TrustHost(e.offered, e.changed)
            } catch (e: IOException) {
                ConnectCheck.Failed(e.message ?: "החיבור נכשל")
            } catch (e: RuntimeException) {
                ConnectCheck.Failed(e.message ?: "החיבור נכשל")
            }
        }
    }
}

/** התוצאה של "בדוק והתחבר". */
sealed interface ConnectCheck {
    /** הצליח. [connection] — עם מה שהבדיקה השלימה (תיקיית הבית ב-SFTP). */
    data class Ok(val connection: NetConnection) : ConnectCheck

    data class Failed(val message: String) : ConnectCheck

    /** SFTP: שרת שעוד לא אושר, או שהמפתח שלו השתנה — שואלים את המשתמש לפני שממשיכים. */
    data class TrustHost(val hostKey: String, val changed: Boolean) : ConnectCheck
}
