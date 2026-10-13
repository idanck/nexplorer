package com.idanck.nexplorer.data.archive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchivesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun tree(): File {
        val src = tmp.newFolder("src")
        File(src, "a.txt").writeText("alpha")
        File(src, "sub").mkdirs()
        File(src, "sub/שלום.txt").writeText("עברית")
        File(src, "sub/big.bin").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
        return src
    }

    @Test
    fun `round trip without and with a password`() = runBlocking {
        val src = tree()
        for (password in listOf(null, "s3cret".toCharArray())) {
            val zip = File(tmp.root, "out-${password != null}.zip")
            var bytes = 0L
            ZipArchives.compress(listOf(src), zip, password, checkpoint = {}, onBytes = { bytes += it })
            assertEquals(5 + "עברית".toByteArray().size + 300_000L, bytes)
            assertEquals(password != null, ZipArchives.inspect(zip).encrypted)
            assertEquals(3, ZipArchives.inspect(zip).entries)

            val target = tmp.newFolder("x-${password != null}")
            val result = ZipArchives.extract(zip, target, password, checkpoint = {}, onBytes = {})
            assertEquals(0, result.skipped)
            assertEquals("alpha", File(target, "src/a.txt").readText())
            assertEquals("עברית", File(target, "src/sub/שלום.txt").readText())
            assertEquals(300_000L, File(target, "src/sub/big.bin").length())
            assertFalse("אין קבצי part שנשארו", target.walk().any { it.name.endsWith(".nexplorer-part") })
        }
    }

    @Test
    fun `wrong or missing password fails cleanly and leaves nothing`() = runBlocking {
        val zip = File(tmp.root, "locked.zip")
        ZipArchives.compress(listOf(tree()), zip, "right".toCharArray(), {}, {})
        for (password in listOf(null, "wrong".toCharArray())) {
            val target = tmp.newFolder("t-${password != null}")
            try {
                ZipArchives.extract(zip, target, password, {}, {})
                fail("expected WrongPassword")
            } catch (e: ZipArchives.WrongPassword) {
                // צפוי
            }
            assertEquals(0, target.listFiles()!!.size)
        }
    }

    @Test
    fun `zip slip entries are skipped and existing files are not overwritten`() = runBlocking {
        val zip = File(tmp.root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            for (name in listOf("../escape.txt", "/abs.txt", "ok.txt")) {
                out.putNextEntry(ZipEntry(name))
                out.write("x".toByteArray())
                out.closeEntry()
            }
        }
        val target = tmp.newFolder("t")
        File(target, "ok.txt").writeText("mine")
        val result = ZipArchives.extract(zip, target, null, {}, {})
        assertEquals(2, result.skipped)
        assertFalse(File(tmp.root, "escape.txt").exists())
        assertEquals("mine", File(target, "ok.txt").readText())
        assertTrue(File(target, "ok (1).txt").exists())
    }

    @Test
    fun `cancel removes the partial output`() = runBlocking {
        val zip = File(tmp.root, "c.zip")
        ZipArchives.compress(listOf(tree()), zip, null, {}, {})
        val target = tmp.newFolder("cancel")
        var calls = 0
        try {
            ZipArchives.extract(zip, target, null, checkpoint = { if (++calls > 6) throw IllegalStateException("cancel") }, onBytes = {})
            fail("expected cancel")
        } catch (e: IllegalStateException) {
            // צפוי
        }
        assertEquals(0, target.walk().count { it.isFile })
    }
}
