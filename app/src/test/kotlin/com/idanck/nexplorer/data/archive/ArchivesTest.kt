package com.idanck.nexplorer.data.archive

import com.idanck.nexplorer.core.ArchiveFormat
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bigSize = 300_000

    private fun tree(): File {
        val src = tmp.newFolder("src")
        File(src, "a.txt").writeText("alpha")
        File(src, "sub").mkdirs()
        File(src, "sub/שלום.txt").writeText("עברית")
        File(src, "sub/big.bin").writeBytes(ByteArray(bigSize) { (it % 251).toByte() })
        return src
    }

    private val treeBytes = 5 + "עברית".toByteArray().size + 300_000L

    private fun assertTree(target: File) {
        assertEquals("alpha", File(target, "src/a.txt").readText())
        assertEquals("עברית", File(target, "src/sub/שלום.txt").readText())
        assertEquals(bigSize.toLong(), File(target, "src/sub/big.bin").length())
        assertFalse("אין קבצי part שנשארו", target.walk().any { it.name.endsWith(".nexplorer-part") })
    }

    private suspend fun extract(archive: File, target: File, password: CharArray? = null, checkpoint: suspend () -> Unit = {}) =
        Archives.extract(archive, target, password, checkpoint, onTotal = {}, onBytes = {})

    @Test
    fun `zip and 7z round trip without and with a password`() = runBlocking {
        val src = tree()
        for (ext in listOf("zip", "7z")) {
            for (password in listOf(null, "s3cret".toCharArray())) {
                val archive = File(tmp.root, "out-${password != null}.$ext")
                var bytes = 0L
                val created = Archives.compress(listOf(src), archive, password, checkpoint = {}, onBytes = { bytes += it })
                assertEquals(archive, created)
                assertEquals(treeBytes, bytes)
                val info = Archives.inspect(archive)
                assertEquals(ArchiveFormat.entries.first { it.extension == ext }, info.format)
                assertEquals("$ext encrypted", password != null, info.encrypted)
                if (info.entries != null) assertEquals(3, info.entries)

                val target = tmp.newFolder("x-$ext-${password != null}")
                var total = 0L
                var done = 0L
                val result = Archives.extract(archive, target, password, {}, onTotal = { total = it }, onBytes = { done += it })
                assertEquals(0, result.unsafe)
                assertEquals(treeBytes, total)
                assertEquals(treeBytes, done)
                assertTree(target)
            }
        }
    }

    @Test
    fun `wrong or missing password fails cleanly and leaves nothing`() = runBlocking {
        for (ext in listOf("zip", "7z")) {
            val archive = File(tmp.root, "locked.$ext")
            Archives.compress(listOf(tree()), archive, "right".toCharArray(), {}, {})
            for (password in listOf(null, "wrong".toCharArray())) {
                val target = tmp.newFolder("t-$ext-${password != null}")
                try {
                    extract(archive, target, password)
                    fail("expected WrongPassword for $ext")
                } catch (e: Archives.WrongPassword) {
                    // צפוי
                }
                assertEquals("$ext left files behind", 0, target.listFiles()!!.size)
            }
            tmp.root.resolve("src").deleteRecursively()
        }
    }

    @Test
    fun `7z with an encrypted file list asks for the password before listing`() = runBlocking {
        // נוצר ב-py7zr עם header_encryption — Commons Compress מצפין רק את התוכן.
        val archive = fixture("headers-encrypted.7z")
        val info = Archives.inspect(archive)
        assertTrue(info.encrypted)
        assertNull(info.entries)
        val wrong = tmp.newFolder("wrong")
        try {
            extract(archive, wrong, "nope".toCharArray())
            fail("expected WrongPassword")
        } catch (e: Archives.WrongPassword) {
            // צפוי
        }
        assertEquals(0, wrong.listFiles()!!.size)
        val target = tmp.newFolder("right")
        extract(archive, target, "secret".toCharArray())
        assertEquals("hidden names", File(target, "inside/note.txt").readText())
    }

    @Test
    fun `tar in every compression extracts the same tree`() = runBlocking {
        val writers: Map<String, (OutputStream) -> OutputStream> = mapOf(
            "tar" to { it },
            "tar.gz" to { GzipCompressorOutputStream(it) },
            "tgz" to { GzipCompressorOutputStream(it) },
            "tar.bz2" to { BZip2CompressorOutputStream(it) },
            "tar.xz" to { XZCompressorOutputStream(it) },
        )
        val src = tree()
        for ((ext, wrap) in writers) {
            val archive = File(tmp.root, "t.$ext")
            wrap(archive.outputStream()).use { out -> writeTar(out, src) }
            val info = Archives.inspect(archive)
            if (ext == "tar") {
                assertEquals(3, info.entries)
                assertEquals(treeBytes, info.totalBytes)
            } else {
                assertNull(info.totalBytes)
            }
            val target = tmp.newFolder("x-$ext")
            var total = 0L
            var done = 0L
            val result = Archives.extract(archive, target, null, {}, onTotal = { total = it }, onBytes = { done += it })
            assertEquals("$ext: progress is the compressed file", archive.length(), total)
            assertEquals(archive.length(), done)
            assertEquals(1, result.special)
            assertTree(target)
            assertFalse("$ext: the link is not created", File(target, "src/link").exists())
        }
    }

    @Test
    fun `a single compressed file comes out under its own name`() = runBlocking {
        val gz = File(tmp.root, "notes.txt.gz")
        GZIPOutputStream(gz.outputStream()).use { it.write("plain notes".toByteArray()) }
        assertEquals(ArchiveFormat.Gz, Archives.inspect(gz).format)
        val target = tmp.newFolder("gz")
        extract(gz, target)
        assertEquals("plain notes", File(target, "notes.txt").readText())
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
        val tar = File(tmp.root, "evil.tar")
        TarArchiveOutputStream(tar.outputStream()).use { out ->
            for (name in listOf("../escape.txt", "ok.txt")) {
                out.putArchiveEntry(TarArchiveEntry(name, true).apply { size = 1 })
                out.write("x".toByteArray())
                out.closeArchiveEntry()
            }
        }
        for ((archive, unsafe) in listOf(zip to 2, tar to 1)) {
            val target = tmp.newFolder("t-${archive.extension}")
            File(target, "ok.txt").writeText("mine")
            val result = extract(archive, target)
            assertEquals(unsafe, result.unsafe)
            assertFalse(File(tmp.root, "escape.txt").exists())
            assertEquals("mine", File(target, "ok.txt").readText())
            assertTrue(File(target, "ok (1).txt").exists())
        }
    }

    @Test
    fun `cancel removes the partial output`() = runBlocking {
        for (ext in listOf("zip", "7z", "tar.gz")) {
            val src = tree()
            val archive = File(tmp.root, "c.$ext")
            if (ext == "tar.gz") {
                GzipCompressorOutputStream(archive.outputStream()).use { writeTar(it, src) }
            } else {
                Archives.compress(listOf(src), archive, null, {}, {})
            }
            src.deleteRecursively()
            val target = tmp.newFolder("cancel-$ext")
            var calls = 0
            try {
                extract(archive, target, checkpoint = { if (++calls > 6) throw IllegalStateException("cancel") })
                fail("expected cancel for $ext")
            } catch (e: IllegalStateException) {
                // צפוי
            }
            assertEquals("$ext left files behind", 0, target.walk().count { it != target })
        }
    }

    @Test
    fun `rar is not handled here`() {
        assertNull(com.idanck.nexplorer.core.ArchiveFormats.of("movie.rar"))
    }

    /** TAR של [src] כמו ש-`tar` יוצר: תיקיות, קבצים — ועוד קישור סימבולי, שלא אמור להיווצר. */
    private fun writeTar(out: OutputStream, src: File) {
        val tar = TarArchiveOutputStream(out)
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        src.walkTopDown().forEach { file ->
            val name = file.relativeTo(src.parentFile!!).path
            tar.putArchiveEntry(tar.createArchiveEntry(file, name))
            if (file.isFile) file.inputStream().use { it.copyTo(tar) }
            tar.closeArchiveEntry()
        }
        val link = TarArchiveEntry("src/link", TarArchiveEntry.LF_SYMLINK).apply { linkName = "/sdcard" }
        tar.putArchiveEntry(link)
        tar.closeArchiveEntry()
        tar.finish()
    }

    private fun fixture(name: String): File {
        val stream = requireNotNull(javaClass.getResourceAsStream("/archives/$name")) { "missing fixture $name" }
        return File(tmp.root, name).also { out -> stream.use { input -> out.outputStream().use { input.copyTo(it) } } }
    }
}
