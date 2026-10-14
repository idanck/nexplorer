package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FolderSizesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sums regular files at every depth`() {
        val root = tmp.newFolder("root")
        File(root, "a.bin").writeBytes(ByteArray(1000))
        File(root, "sub/deeper").mkdirs()
        File(root, "sub/b.bin").writeBytes(ByteArray(234))
        File(root, "sub/deeper/c.bin").writeBytes(ByteArray(5))
        File(root, "empty").mkdirs()
        assertEquals(1239L, FolderSizes.of(root))
        assertEquals(0L, FolderSizes.of(File(root, "empty")))
    }

    @Test
    fun `links are not followed`() {
        val root = tmp.newFolder("root")
        File(root, "a.bin").writeBytes(ByteArray(100))
        val outside = tmp.newFolder("outside")
        File(outside, "big.bin").writeBytes(ByteArray(10_000))
        // קישור לתיקייה מחוץ לעץ, וקישור ששולח חזרה לשורש (לולאה).
        Files.createSymbolicLink(File(root, "to-outside").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        assertEquals(100L, FolderSizes.of(root))
    }

    @Test
    fun `stops when asked`() {
        val root = tmp.newFolder("root")
        repeat(5) { File(root, "f$it").writeBytes(ByteArray(10)) }
        var visits = 0
        assertNull(FolderSizes.of(root) { ++visits < 3 })
    }

    @Test
    fun `missing folder has no size`() {
        assertNull(FolderSizes.of(File(tmp.root, "nope")))
    }
}
