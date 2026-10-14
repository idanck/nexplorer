package com.idanck.nexplorer.data.transfer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** שרת מדומה בזיכרון — במקום SMB. נתיבים "mem:/a/b". */
private class MemorySide : Side {
    val dirs = sortedSetOf("mem:")
    val files = sortedMapOf<String, ByteArray>()
    val modified = HashMap<String, Long>()
    /** קישורים (כמו בשרת SFTP): הנתיב ← התיקייה שהוא מצביע עליה. רשימה דרכו — של היעד. */
    val links = HashMap<String, String>()
    /** שם קובץ שהכתיבה אליו נכשלת באמצע. */
    var failOn: String? = null

    override fun stat(path: String): Node? = when {
        path in links -> Node(path, path.substringAfterLast('/'), true, 0, 0, isLink = true)
        path in dirs -> Node(path, path.substringAfterLast('/'), true, 0, modified[path] ?: 0)
        path in files -> Node(path, path.substringAfterLast('/'), false, files.getValue(path).size.toLong(), modified[path] ?: 0)
        else -> null
    }

    override fun list(dir: String): List<Node> {
        links[dir]?.let { target -> return list(target).map { it.copy(path = "$dir/${it.name}") } }
        if (dir !in dirs) throw IOException("no dir $dir")
        val prefix = "$dir/"
        return (dirs + files.keys + links.keys).filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }.map { stat(it)!! }
    }

    override fun child(dir: String, name: String) = "$dir/$name"

    override fun mkdir(path: String) {
        if (stat(path) != null) throw IOException("exists $path")
        dirs += path
    }

    override fun read(path: String): InputStream = ByteArrayInputStream(files[path] ?: throw IOException("no file $path"))

    override fun write(path: String): OutputStream {
        if (stat(path) != null) throw IOException("exists $path")
        files[path] = ByteArray(0)
        return object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (failOn != null && path.contains(failOn!!)) throw IOException("disk full")
                super.write(b, off, len)
            }

            override fun close() {
                files[path] = toByteArray()
            }
        }
    }

    override fun finish(temp: String, dest: String) {
        if (stat(dest) != null) throw IOException("exists $dest")
        files[dest] = files.remove(temp) ?: throw IOException("no temp")
    }

    override fun delete(path: String, isDirectory: Boolean) {
        if (path in links) {
            // קישור נמחק כקישור (rm), לא כתיקייה.
            if (isDirectory) throw IOException("rmdir on a link")
            links -= path
            return
        }
        if (isDirectory) {
            if (list(path).isNotEmpty()) throw IOException("not empty")
            dirs -= path
        } else if (files.remove(path) == null) {
            throw IOException("no file $path")
        }
    }

    override fun setModified(path: String, time: Long) {
        modified[path] = time
    }

    override fun move(from: String, to: String): Boolean = false
}

class TreeCopyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var bytes = 0L
    private val written = mutableListOf<Pair<String, String?>>()
    private val copier = TreeCopy(checkpoint = {}, onBytes = { bytes += it }, onName = {}, onFile = { dest, removed -> written += dest to removed })

    private fun localTree(): File {
        val root = tmp.newFolder("Photos")
        File(root, "2026").mkdirs()
        File(root, "a.jpg").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
        File(root, "2026/b.txt").writeText("שלום")
        File(root, "2026/b.txt").setLastModified(1_700_000_000_000)
        return root
    }

    @Test
    fun `local tree to the server keeps content and dates, and leaves no parts`() = runBlocking {
        val server = MemorySide()
        server.dirs += "mem:/share"
        val source = localTree()
        val node = LocalSide.stat(source.path)!!
        assertEquals(300_000L + "שלום".toByteArray().size, copier.sizeOf(LocalSide, node))

        copier.copy(LocalSide, node, server, "mem:/share", "Photos", move = false)

        assertTrue("mem:/share/Photos/2026" in server.dirs)
        assertArrayEquals(File(source, "a.jpg").readBytes(), server.files["mem:/share/Photos/a.jpg"])
        assertEquals("שלום", server.files["mem:/share/Photos/2026/b.txt"]!!.toString(Charsets.UTF_8))
        assertEquals(1_700_000_000_000, server.modified["mem:/share/Photos/2026/b.txt"])
        assertFalse(server.files.keys.any { it.endsWith(TreeCopy.PART) })
        assertEquals(copier.sizeOf(LocalSide, node), bytes)
        assertTrue("בהעתקה המקור נשאר", File(source, "a.jpg").isFile)
        assertEquals(2, written.size)
        assertTrue(written.all { it.second == null })
    }

    @Test
    fun `moving from the server deletes each file after it is saved, then the folders`() = runBlocking {
        val server = MemorySide()
        server.dirs += listOf("mem:/share", "mem:/share/Docs", "mem:/share/Docs/old")
        server.files["mem:/share/Docs/x.pdf"] = byteArrayOf(1, 2, 3)
        server.files["mem:/share/Docs/old/y.txt"] = "y".toByteArray()
        val target = tmp.newFolder("Download")

        copier.copy(server, server.stat("mem:/share/Docs")!!, LocalSide, target.path, "Docs", move = true)

        assertArrayEquals(byteArrayOf(1, 2, 3), File(target, "Docs/x.pdf").readBytes())
        assertEquals("y", File(target, "Docs/old/y.txt").readText())
        assertNull(server.stat("mem:/share/Docs"))
        assertTrue(server.files.isEmpty())
        assertTrue(written.all { it.second != null })
    }

    @Test
    fun `a failed write leaves no part file, and discard removes the partial copy`() = runBlocking {
        val server = MemorySide()
        server.dirs += "mem:/share"
        server.failOn = "a.jpg"
        val source = localTree()
        try {
            copier.copy(LocalSide, LocalSide.stat(source.path)!!, server, "mem:/share", "Photos", move = false)
            fail("ההעתקה הייתה אמורה להיכשל")
        } catch (e: IOException) {
            assertEquals("disk full", e.message)
        }
        assertFalse(server.files.keys.any { it.endsWith(TreeCopy.PART) })
        copier.discard(server, "mem:/share/Photos")
        assertNull(server.stat("mem:/share/Photos"))
        assertTrue("המקור לא נפגע", File(source, "a.jpg").isFile)
    }

    @Test
    fun `a part left by a killed run is replaced`() = runBlocking {
        val server = MemorySide()
        server.dirs += "mem:/share"
        server.files["mem:/share/.b.txt${TreeCopy.PART}"] = "half".toByteArray()
        val file = tmp.newFile("b.txt").apply { writeText("whole") }
        copier.copy(LocalSide, LocalSide.stat(file.path)!!, server, "mem:/share", "b.txt", move = false)
        assertEquals("whole", server.files["mem:/share/b.txt"]!!.toString(Charsets.UTF_8))
        assertEquals(setOf("mem:/share/b.txt"), server.files.keys)
    }

    @Test
    fun `free names on the other side`() {
        val server = MemorySide()
        server.dirs += "mem:/share"
        server.files["mem:/share/a.txt"] = ByteArray(0)
        server.files["mem:/share/a (1).txt"] = ByteArray(0)
        assertEquals("a (2).txt", copier.uniqueIn(server, "mem:/share", "a.txt"))
        assertEquals("b.txt", copier.uniqueIn(server, "mem:/share", "b.txt"))
    }

    @Test
    fun `delete is post-order and counts every item`() = runBlocking {
        val server = MemorySide()
        server.dirs += listOf("mem:/share", "mem:/share/A", "mem:/share/A/B")
        server.files["mem:/share/A/1"] = ByteArray(1)
        server.files["mem:/share/A/B/2"] = ByteArray(1)
        val node = server.stat("mem:/share/A")!!
        assertEquals(4, copier.countOf(server, node))
        val order = mutableListOf<String>()
        copier.delete(server, node) { order += it.path }
        assertEquals(4, order.size)
        assertEquals("mem:/share/A", order.last())
        assertTrue(order.indexOf("mem:/share/A/B/2") < order.indexOf("mem:/share/A/B"))
        assertEquals(setOf("mem:", "mem:/share"), server.dirs.toSet())
    }

    @Test
    fun `a move stops when a folder did not empty`() = runBlocking {
        val server = MemorySide()
        server.dirs += listOf("mem:/share", "mem:/share/D")
        server.files["mem:/share/D/1"] = ByteArray(1)
        val target = tmp.newFolder("T")
        // קובץ שהגיע לתיקייה באמצע ההעברה (סנכרון) — לא נמחק בלי שהועתק.
        val racing = TreeCopy(checkpoint = {}, onBytes = {}, onName = { server.files["mem:/share/D/late"] = ByteArray(1) })
        try {
            racing.copy(server, server.stat("mem:/share/D")!!, LocalSide, target.path, "D", move = true)
            fail("התיקייה לא התרוקנה — ההעברה הייתה אמורה להיעצר")
        } catch (e: IOException) {
            assertEquals("not empty", e.message)
        }
        assertTrue(File(target, "D/1").isFile)
        assertTrue("mem:/share/D/late" in server.files)
    }

    @Test
    fun `links are never followed - delete removes the link, copy and counts skip it`() = runBlocking {
        val server = MemorySide()
        server.dirs += listOf("mem:/outside", "mem:/victim")
        server.files["mem:/outside/keep.txt"] = "keep".toByteArray()
        server.files["mem:/victim/f"] = "x".toByteArray()
        server.links["mem:/victim/data"] = "mem:/outside"
        // לולאה: קישור לשורש.
        server.links["mem:/victim/loop"] = "mem:"
        val copy = TreeCopy(checkpoint = {}, onBytes = {}, onName = {})
        val victim = server.stat("mem:/victim")!!
        assertEquals(4, copy.countOf(server, victim))
        assertEquals(1L, copy.sizeOf(server, victim))

        val other = MemorySide()
        copy.copy(server, victim, other, "mem:", "victim", move = false)
        assertEquals(setOf("mem:/victim/f"), other.files.keys)
        try {
            copy.copy(server, server.stat("mem:/victim/data")!!, other, "mem:", "data", move = false)
            fail("copying a link to a folder")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("קישור לתיקייה"))
        }

        copy.delete(server, victim) {}
        assertFalse("mem:/victim" in server.dirs)
        assertTrue(server.links.isEmpty())
        assertArrayEquals("keep".toByteArray(), server.files["mem:/outside/keep.txt"])
    }
}
