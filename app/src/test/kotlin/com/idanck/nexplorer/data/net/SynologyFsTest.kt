package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import kotlin.random.Random

/**
 * [SynologyFs] מול השרת המדומה של DSM (`.github/qa/syno-server.py`), שרץ כאן כתהליך. הכללים שלו — אלה שנבדקו מול
 * NAS אמיתי (DSM 7.4): דפים, `Range`, העלאה בלי דריסה, 1100/1200 ← 414, ‏sid שפג (119).
 */
class SynologyFsTest {

    private val connection = NetConnection("syn0000000001", NetProtocol.Synology, "", "127.0.0.1", port, "", "alex", plainHttp = true)
    private val root = NetPaths.root(NetProtocol.Synology, connection.id)
    private val qa = NetPaths.child(root, "nexplorer-qa")

    private fun fs(password: String = "qa-pass") = SynologyFs(connection, password.toCharArray())

    @Before
    fun reset() = control("/qa/reset")

    @Test
    fun `lists the shares at the root and pages through a folder`() = fs().use { fs ->
        assertEquals(listOf("nexplorer-qa", "backup"), fs.list(root).map { it.name })
        assertTrue(fs.list(root).all { it.isDirectory })
        // השרת מחזיר עד 2 בכל דף — 4 פריטים הם שני דפים.
        val names = fs.list(qa).map { it.name }.sorted()
        assertEquals(listOf("# 100% עברית.txt", "Photos", "notes.txt", "photo.png"), names)
        val notes = fs.list(qa).first { it.name == "notes.txt" }
        assertEquals(15L, notes.size)
        assertEquals(1_700_000_000_000L, notes.lastModified)
        assertEquals("$qa/notes.txt", notes.path)
    }

    @Test
    fun `stat finds files, folders and shares, and null when missing`() = fs().use { fs ->
        assertEquals(15L, fs.stat("$qa/notes.txt")?.size)
        assertTrue(fs.stat("$qa/Photos")!!.isDirectory)
        assertTrue(fs.stat(qa)!!.isDirectory)
        assertTrue(fs.stat(root)!!.isDirectory)
        assertNull(fs.stat("$qa/missing.txt"))
        assertEquals("הקובץ לא נמצא ב-NAS, או שלא ניתן לקרוא אותו", runCatching { fs.openRead("$qa/missing.txt") }.exceptionOrNull()?.message)
        assertEquals("encoded\n", String(fs.openRead("$qa/# 100% עברית.txt").use { it.readBytes() }))
    }

    @Test
    fun `reads from an offset with Range`() = fs().use { fs ->
        val data = Random(7).nextBytes(300_000)
        fs.openWrite("$qa/big.bin", data.size.toLong()).use { it.write(data) }
        assertArrayEquals(data, fs.openRead("$qa/big.bin").use { it.readBytes() })
        assertArrayEquals(data.copyOfRange(123_456, data.size), fs.openRead("$qa/big.bin", 123_456).use { it.readBytes() })
        assertEquals(0, fs.openRead("$qa/big.bin", data.size.toLong() + 10).use { it.readBytes() }.size)
    }

    @Test
    fun `upload keeps the modification time and never overwrites`() = fs().use { fs ->
        fs.openWrite("$qa/new.txt", 3, lastModified = 1_650_000_000_000L).use { it.write("abc".toByteArray()) }
        val entry = fs.stat("$qa/new.txt")!!
        assertEquals(3L, entry.size)
        assertEquals(1_650_000_000_000L, entry.lastModified)
        val e = runCatching { fs.openWrite("$qa/new.txt").use { it.write("xyz".toByteArray()) } }.exceptionOrNull()
        assertEquals("כבר קיים פריט בשם הזה", e?.message)
        assertEquals("abc", String(fs.openRead("$qa/new.txt").use { it.readBytes() }))
    }

    @Test
    fun `creates, renames, moves across shares and deletes`() = fs().use { fs ->
        fs.mkdir("$qa/Created")
        assertEquals("כבר קיים פריט בשם הזה", runCatching { fs.mkdir("$qa/Created") }.exceptionOrNull()?.message)
        fs.rename("$qa/Created", "$qa/Renamed")
        assertNull(fs.stat("$qa/Created"))
        assertNotNull(fs.stat("$qa/Renamed"))
        assertEquals("כבר קיים פריט בשם הזה", runCatching { fs.rename("$qa/notes.txt", "$qa/photo.png") }.exceptionOrNull()?.message)
        // לתיקייה אחרת — בצד השרת (CopyMove), ואז השם החדש.
        fs.rename("$qa/notes.txt", "$qa/Photos/moved.txt")
        assertNull(fs.stat("$qa/notes.txt"))
        assertEquals("hello from dsm\n", String(fs.openRead("$qa/Photos/moved.txt").use { it.readBytes() }))
        // לשיתוף אחר.
        fs.rename("$qa/Photos/moved.txt", NetPaths.child(root, "backup/moved.txt"))
        assertNotNull(fs.stat(NetPaths.child(root, "backup/moved.txt")))
        assertEquals("התיקייה לא ריקה", runCatching { fs.delete("$qa/Photos", isDirectory = true) }.exceptionOrNull()?.message)
        fs.delete("$qa/Photos/inside.png", isDirectory = false)
        fs.delete("$qa/Photos", isDirectory = true)
        assertNull(fs.stat("$qa/Photos"))
        assertTrue(runCatching { fs.delete(qa, isDirectory = true) }.exceptionOrNull() is IOException)
    }

    @Test
    fun `an expired session signs in again`() = fs().use { fs ->
        assertEquals(4, fs.list(qa).size)
        control("/qa/expire")
        assertEquals(4, fs.list(qa).size)
        control("/qa/expire")
        assertEquals("hello from dsm\n", String(fs.openRead("$qa/notes.txt").use { it.readBytes() }))
        control("/qa/expire")
        assertNotNull(fs.thumbnail("$qa/photo.png", large = true))
    }

    @Test
    fun `a wrong password is reported in hebrew`() = fs("wrong").use { fs ->
        assertEquals("שם המשתמש או הסיסמה שגויים", runCatching { fs.list(root) }.exceptionOrNull()?.message)
    }

    @Test
    fun `thumbnails and free space come from the server`() = fs().use { fs ->
        val thumb = fs.thumbnail("$qa/photo.png", large = false)
        assertNotNull(thumb)
        assertEquals(0x89.toByte(), thumb!![0])
        assertNull(fs.thumbnail("$qa/notes.txt", large = false))
        val space = fs.space(qa)!!
        assertEquals(1000L * 1024 * 1024 * 1024, space.total)
        assertEquals(200L * 1024 * 1024 * 1024, space.free)
        assertNull(fs.space(root))
    }

    @Test
    fun `the nas searches, in pages, with size and name filters`() = fs().use { fs ->
        val found = mutableListOf<String>()
        fs.search(listOf(qa), "png", minSize = 0, modifiedAfter = 0, isCancelled = { false }) { entries ->
            found += entries.map { it.path }
            true
        }
        assertEquals(listOf("$qa/Photos/inside.png", "$qa/photo.png"), found.sorted())
        // בלי תו כללי — הכלה, בלי רישיות; עם — glob.
        val all = mutableListOf<String>()
        fs.search(listOf(root + "/nexplorer-qa", root + "/backup"), "*.TXT", 0, 0, { false }) { all += it.map { e -> e.name }; true }
        assertEquals(listOf("# 100% עברית.txt", "notes.txt"), all.sorted())
        val big = mutableListOf<String>()
        fs.search(listOf(qa), "*", minSize = 50, modifiedAfter = 0, isCancelled = { false }) { big += it.map { e -> e.name }; true }
        assertEquals(listOf("inside.png", "photo.png"), big.sorted())
        // false מהקריאה — עוצרים.
        var pages = 0
        fs.search(listOf(qa), "*", 0, 0, { false }) { pages++; false }
        assertEquals(1, pages)
    }

    @Test
    fun `folder sizes and server-side copies`() = fs().use { fs ->
        val stats = fs.treeStats(listOf(qa)) { false }
        assertEquals(1, stats.folders)
        assertEquals(4, stats.files)
        assertTrue(stats.bytes > 0)
        // אותו שם — CopyMove ישירות; שם אחר (קיים ביעד) — דרך תיקייה זמנית.
        var last = 0.0
        fs.copyOnServer("$qa/Photos", NetPaths.child(root, "backup"), "Photos", { false }) { last = it }
        assertEquals(1.0, last, 0.0)
        assertNotNull(fs.stat(NetPaths.child(root, "backup/Photos/inside.png")))
        assertNotNull(fs.stat("$qa/Photos/inside.png"))
        fs.copyOnServer("$qa/notes.txt", qa, "notes (1).txt", { false }) {}
        assertEquals("hello from dsm\n", String(fs.openRead("$qa/notes (1).txt").use { it.readBytes() }))
        assertTrue(fs.list(qa).none { it.name.startsWith(".nexplorer-copy") })
    }

    @Test
    fun `error codes map to hebrew`() {
        assertEquals("כבר קיים פריט בשם הזה", SynoErrors.file(414).message)
        assertEquals("אין מקום פנוי ב-NAS", SynoErrors.file(416).message)
        assertEquals("שם המשתמש או הסיסמה שגויים", SynoErrors.auth(400).message)
        assertTrue(SynoErrors.auth(403).message!!.contains("אימות דו-שלבי"))
        val nested = org.json.JSONObject("""{"success":false,"error":{"code":1100,"errors":[{"code":414,"path":"/a/b"}]}}""")
        assertEquals(414, SynologyFs.errorCodeOf(nested))
        assertEquals(119, SynologyFs.errorCodeOf(org.json.JSONObject("""{"success":false,"error":{"code":119}}""")))
    }

    companion object {
        private val port: Int = ServerSocket(0).use { it.localPort }
        private var server: Process? = null

        private fun control(path: String) {
            val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.outputStream.close()
            assertEquals(204, c.responseCode)
            c.disconnect()
        }

        @JvmStatic
        @BeforeClass
        fun start() {
            val script = generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, ".github/qa/syno-server.py") }
                .firstOrNull { it.isFile }
            val python = listOf("python3", "python").firstOrNull { runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false) }
            assumeTrue("syno-server.py and python3 are needed", script != null && python != null)
            val dir = Files.createTempDirectory("syno").toFile()
            server = ProcessBuilder(python, script!!.absolutePath, "--port", port.toString(), "--root", dir.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(File(dir.parentFile, "syno-server-$port.log"))
                .start()
            repeat(50) {
                if (runCatching { control("/qa/reset") }.isSuccess) return
                Thread.sleep(100)
            }
            error("syno-server did not start")
        }

        @JvmStatic
        @AfterClass
        fun stop() {
            server?.destroy()
        }
    }
}
