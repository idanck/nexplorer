package com.idanck.nexplorer.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `round trip keeps the exact text and leaves no temp file`() {
        val file = tmp.newFile("notes.txt")
        val text = "שלום\r\nline 2\n﻿tail"
        TextFiles.save(file, text)
        assertEquals(TextFiles.Loaded.Text(text), TextFiles.load(file))
        assertFalse(File(tmp.root, ".notes.txt.nexplorer-part").exists())
    }

    @Test
    fun `binary and invalid utf8 are refused`() {
        val nul = tmp.newFile("a.bin").apply { writeBytes(byteArrayOf(65, 0, 66)) }
        assertEquals(TextFiles.Loaded.Binary, TextFiles.load(nul))
        val latin1 = tmp.newFile("b.txt").apply { writeBytes(byteArrayOf(0xE9.toByte(), 0x20)) }
        assertEquals(TextFiles.Loaded.Binary, TextFiles.load(latin1))
    }

    @Test
    fun `too large is reported, not read`() {
        val big = tmp.newFile("big.txt").apply { writeBytes(ByteArray((TextFiles.MAX_BYTES + 1).toInt()) { 'a'.code.toByte() }) }
        assertTrue(TextFiles.load(big) is TextFiles.Loaded.TooLarge)
    }
}
