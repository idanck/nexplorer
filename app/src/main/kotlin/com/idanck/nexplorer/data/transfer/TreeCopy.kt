package com.idanck.nexplorer.data.transfer

import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.net.RemoteCrossDevice
import com.idanck.nexplorer.data.net.RemoteFs
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/** פריט בצד אחד של העתקה. */
internal data class Node(val path: String, val name: String, val isDirectory: Boolean, val size: Long, val lastModified: Long)

/**
 * צד אחד של העתקה שעוברת ברשת: תיקייה במכשיר או בשרת. הקריאות חוסמות (רצות ב-IO). העתקה מקומית
 * לגמרי לא עוברת כאן — היא נשארת ב-TransferManager, עם הקישורים, התיקיות המוצפנות והסורק.
 */
internal interface Side {
    fun stat(path: String): Node?
    fun list(dir: String): List<Node>
    fun child(dir: String, name: String): String
    fun mkdir(path: String)
    fun read(path: String): InputStream
    /** קובץ חדש; קיים — כשל. */
    fun write(path: String): OutputStream
    /** שם זמני → שם סופי, באותה תיקייה. */
    fun finish(temp: String, dest: String)
    fun delete(path: String, isDirectory: Boolean)
    fun setModified(path: String, time: Long)
    /** העברה בלי להעתיק בתים (שינוי שם) — false כשאי אפשר כאן, ואז מעתיקים ומוחקים. */
    fun move(from: String, to: String): Boolean
}

/** האחסון של המכשיר. כמו ההעתקה המקומית: לא עוקבים אחרי קישורים בתוך עץ, וכל קובץ נכתב לדיסק לפני שמו הסופי. */
internal object LocalSide : Side {
    override fun stat(path: String): Node? {
        val file = File(path)
        if (!file.exists()) return null
        return Node(file.absolutePath, file.name, file.isDirectory, if (file.isDirectory) 0 else file.length(), file.lastModified())
    }

    override fun list(dir: String): List<Node> {
        val children = File(dir).listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${File(dir).name}\"")
        return children.filter { !LocalFiles.isLink(it) }.map { Node(it.absolutePath, it.name, it.isDirectory, if (it.isDirectory) 0 else it.length(), it.lastModified()) }
    }

    override fun child(dir: String, name: String): String = File(dir, name).absolutePath

    override fun mkdir(path: String) {
        val dir = File(path)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("לא ניתן ליצור את \"${dir.name}\"")
    }

    override fun read(path: String): InputStream = File(path).inputStream()

    override fun write(path: String): OutputStream {
        val file = File(path)
        if (file.exists()) throw IOException("כבר קיים פריט בשם \"${file.name}\"")
        val stream = FileOutputStream(file)
        // נסגר רק אחרי שהבתים בדיסק — אחרת העברה מוחקת את המקור לפני שהעותק באמת שמור.
        return object : FilterOutputStream(stream) {
            override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
            override fun close() {
                stream.use {
                    it.flush()
                    it.fd.sync()
                }
            }
        }
    }

    override fun finish(temp: String, dest: String) {
        if (!File(temp).renameTo(File(dest))) throw IOException("לא ניתן לשמור את \"${File(dest).name}\"")
    }

    override fun delete(path: String, isDirectory: Boolean) {
        try {
            Files.deleteIfExists(File(path).toPath())
        } catch (e: IOException) {
            throw IOException(
                if (isDirectory) "חלק מהפריטים נשארו ב-\"${File(path).name}\" (נוספו בזמן ההעברה, או שהם קישורים)"
                else "לא ניתן למחוק את \"${File(path).name}\"",
                e,
            )
        }
    }

    override fun setModified(path: String, time: Long) {
        File(path).setLastModified(time)
    }

    override fun move(from: String, to: String): Boolean = File(from).renameTo(File(to))
}

/** חיבור אחד ברשת. העברה בשינוי שם — רק בתוך אותו שיתוף (ב-SFTP — באותו כונן בשרת). */
internal class RemoteSide(val fs: RemoteFs) : Side {
    override fun stat(path: String): Node? = fs.stat(path)?.let { Node(it.path, it.name, it.isDirectory, it.size, it.lastModified) }

    override fun list(dir: String): List<Node> = fs.list(dir).map { Node(it.path, it.name, it.isDirectory, it.size, it.lastModified) }

    override fun child(dir: String, name: String): String = NetPaths.child(dir, name)

    override fun mkdir(path: String) = fs.mkdir(path)

    override fun read(path: String): InputStream = fs.openRead(path)

    override fun write(path: String): OutputStream = fs.openWrite(path)

    override fun finish(temp: String, dest: String) = fs.rename(temp, dest)

    override fun delete(path: String, isDirectory: Boolean) {
        try {
            fs.delete(path, isDirectory)
        } catch (e: IOException) {
            if (!isDirectory) throw e
            throw IOException("חלק מהפריטים נשארו ב-\"${NetPaths.name(path)}\" (נוספו בזמן ההעברה?)", e)
        }
    }

    override fun setModified(path: String, time: Long) = fs.setLastModified(path, time)

    override fun move(from: String, to: String): Boolean {
        if (!NetPaths.sameShare(from, to)) return false
        return try {
            fs.rename(from, to)
            true
        } catch (e: RemoteCrossDevice) {
            false
        }
    }
}

/**
 * העתקה, העברה ומחיקה של עצים בין [Side]-ים — אותם כללים כמו בהעתקה מקומית: שם פנוי ביעד, קובץ
 * נכתב לשם זמני (`.<שם>.nexplorer-part`) ומקבל את שמו רק כשהוא שלם, בהעברה כל קובץ נמחק מהמקור מייד
 * אחרי שהעותק שלו נשמר, ותיקייה — רק כשהתרוקנה.
 */
internal class TreeCopy(
    private val checkpoint: suspend () -> Unit,
    private val onBytes: (Long) -> Unit,
    private val onName: (String) -> Unit,
    /** קובץ שנכתב ביעד ([removed] — המקור שנמחק בהעברה). */
    private val onFile: (dest: String, removed: String?) -> Unit = { _, _ -> },
) {

    fun uniqueIn(side: Side, dir: String, name: String): String = FileNames.unique(name) { side.stat(side.child(dir, it)) != null }

    suspend fun sizeOf(side: Side, node: Node): Long {
        if (!node.isDirectory) return node.size
        var total = 0L
        val stack = ArrayDeque<String>().apply { addLast(node.path) }
        while (stack.isNotEmpty()) {
            checkpoint()
            for (child in side.list(stack.removeLast())) {
                if (child.isDirectory) stack.addLast(child.path) else total += child.size
            }
        }
        return total
    }

    /** [src] אל `[destDir]/[name]`. */
    suspend fun copy(from: Side, src: Node, to: Side, destDir: String, name: String, move: Boolean) {
        checkpoint()
        val dest = to.child(destDir, name)
        if (src.isDirectory) {
            to.mkdir(dest)
            for (child in from.list(src.path)) copy(from, child, to, dest, child.name, move)
            to.setModified(dest, src.lastModified)
            if (move) from.delete(src.path, isDirectory = true)
            return
        }
        onName(src.name)
        val temp = to.child(destDir, ".$name$PART")
        // שארית מריצה שנקטעה (התהליך נהרג באמצע) — שלנו, ולא שווה כלום.
        if (to.stat(temp) != null) to.delete(temp, isDirectory = false)
        try {
            from.read(src.path).use { input ->
                to.write(temp).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        checkpoint()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
            to.finish(temp, dest)
        } catch (e: Exception) {
            runCatching { to.delete(temp, isDirectory = false) }
            throw e
        }
        if (src.lastModified > 0) runCatching { to.setModified(dest, src.lastModified) }
        if (move) {
            try {
                from.delete(src.path, isDirectory = false)
            } catch (e: IOException) {
                throw IOException("ההעתקה הצליחה, אבל לא ניתן למחוק את המקור \"${src.name}\"", e)
            }
        }
        onFile(dest, if (move) src.path else null)
    }

    /** עותק חלקי אחרי כשל בהעתקה (לא בהעברה — שם הוא המקום היחיד של מה שכבר הועבר). */
    fun discard(side: Side, path: String) {
        runCatching {
            val node = side.stat(path) ?: return
            if (node.isDirectory) side.list(path).forEach { discard(side, it.path) }
            side.delete(path, node.isDirectory)
        }
    }

    /** מחיקה בסדר post-order: קודם התוכן, ואז התיקייה. [onItem] — אחרי כל פריט. */
    suspend fun delete(side: Side, node: Node, onItem: (Node) -> Unit) {
        checkpoint()
        if (node.isDirectory) for (child in side.list(node.path)) delete(side, child, onItem)
        side.delete(node.path, node.isDirectory)
        onItem(node)
    }

    /** כמה פריטים יימחקו (כולל התיקיות עצמן) — לפס ההתקדמות. */
    suspend fun countOf(side: Side, node: Node): Int {
        if (!node.isDirectory) return 1
        var count = 1
        val stack = ArrayDeque<String>().apply { addLast(node.path) }
        while (stack.isNotEmpty()) {
            checkpoint()
            for (child in side.list(stack.removeLast())) {
                count++
                if (child.isDirectory) stack.addLast(child.path)
            }
        }
        return count
    }

    companion object {
        const val PART = ".nexplorer-part"
        private const val BUFFER = 256 * 1024
    }
}
