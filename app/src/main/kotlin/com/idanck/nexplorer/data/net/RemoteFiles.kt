package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * כל המקומות ברשת: לפי המזהה שבנתיב ([NetPaths]) מוצא את החיבור השמור, ופותח לו [RemoteFs] אחד שנשמר
 * עד שהחיבור משתנה או נמחק. [connectionOf] ו-[passwordOf] — מההעדפות ומה-Keystore (באפליקציה), או
 * ישירות (בבדיקות מול שרת).
 */
class RemoteFiles(
    private val connectionOf: (String) -> NetConnection?,
    private val passwordOf: (NetConnection) -> CharArray?,
) {
    private val open = HashMap<String, Pair<NetConnection, RemoteFs>>()

    /** [IOException] כשהחיבור נמחק (מועדף ישן) או שהסיסמה שלו לא נקראת. */
    fun fsFor(path: String): RemoteFs {
        val id = NetPaths.connectionId(path) ?: throw IOException("נתיב רשת לא תקין")
        val connection = connectionOf(id) ?: throw IOException("החיבור הזה נמחק מהמגירה")
        synchronized(open) {
            open[id]?.let { (known, fs) -> if (known == connection) return fs else fs.close() }
            val password = connection.secret?.let { passwordOf(connection) }
            val fs = try {
                create(connection, password)
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
        val parent = NetPaths.parent(path) ?: throw IOException("אי אפשר לשנות שם של שיתוף")
        if (NetPaths.segments(path).size < 2) throw IOException("אי אפשר לשנות שם של שיתוף")
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
        var bytes = 0L
        var files = 0
        var folders = 0
        val stack = ArrayDeque<String>()
        for (path in paths) {
            val fs = fsFor(path)
            // רשימת השיתופים: לא סורקים את כל השרת — רק סופרים שיתופים.
            if (NetPaths.segments(path).isEmpty()) {
                folders += fs.list(path).size
                continue
            }
            val root = fs.stat(path) ?: continue
            if (root.isDirectory) stack.addLast(path) else {
                files++
                bytes += root.size
            }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                for (child in fs.list(stack.removeLast())) {
                    if (child.isDirectory) {
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
        if (NetPaths.segments(parent).isEmpty()) throw IOException("פתח שיתוף כדי ליצור בו")
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

        fun create(connection: NetConnection, password: CharArray?): RemoteFs = when (connection.protocol) {
            NetProtocol.Smb -> SmbFs(connection, password)
        }

        /**
         * "בדוק והתחבר": חיבור זמני בפרטים שהוזנו — רשימת השורש (השיתופים, או השיתוף שנבחר).
         * null — הצליח; אחרת ההודעה.
         */
        suspend fun test(connection: NetConnection, password: CharArray?): String? = withContext(Dispatchers.IO) {
            try {
                create(connection, password).use { it.list(connection.root) }
                null
            } catch (e: IOException) {
                e.message ?: "החיבור נכשל"
            } catch (e: RuntimeException) {
                e.message ?: "החיבור נכשל"
            }
        }
    }
}
