package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveAndReportTest {

    @Test
    fun `zip slip entries are rejected`() {
        assertNull(ArchivePaths.safeRelative("../../Android/data/x"))
        assertNull(ArchivePaths.safeRelative("a/../../b"))
        assertNull(ArchivePaths.safeRelative("/sdcard/DCIM/a.jpg"))
        assertNull(ArchivePaths.safeRelative("C:/Windows/x"))
        assertNull(ArchivePaths.safeRelative("..\\evil.txt"))
        assertNull(ArchivePaths.safeRelative("./"))
    }

    @Test
    fun `normal entries are cleaned`() {
        assertEquals("a/b.txt", ArchivePaths.safeRelative("a/b.txt"))
        assertEquals("a/b", ArchivePaths.safeRelative("./a//b/"))
        assertEquals("dir/file.txt", ArchivePaths.safeRelative("dir\\file.txt"))
        assertEquals("שלום/קובץ.txt", ArchivePaths.safeRelative("שלום/קובץ.txt"))
    }

    @Test
    fun `default names`() {
        assertEquals("report.zip", ArchivePaths.defaultZipName(listOf("report.pdf"), "Docs"))
        assertEquals("Docs.zip", ArchivePaths.defaultZipName(listOf("a", "b"), "Docs"))
        assertEquals("photos", ArchivePaths.extractFolderName("photos.zip"))
    }

    @Test
    fun `duplicate candidates group by exact size above the minimum`() {
        val sizes = listOf(100_000L, 100_000L, 100_000L, 5L, 5L, 200_000L)
        val groups = StorageReport.sameSizeGroups(sizes, { it })
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().size)
        assertEquals(200_000L, StorageReport.reclaimable(groups) { it })
    }

    @Test
    fun `junk rules`() {
        assertEquals(true, StorageReport.isJunkFile("video.mp4.crdownload"))
        assertEquals(true, StorageReport.isJunkFile("x.TMP"))
        assertEquals(false, StorageReport.isJunkFile("notes.txt"))
        assertEquals(true, StorageReport.isJunkFolder("DCIM/.thumbnails"))
        assertEquals(true, StorageReport.isJunkFolder("Android/media/com.whatsapp/cache"))
        assertEquals(false, StorageReport.isJunkFolder("Documents/project/cache"))
        assertEquals(false, StorageReport.isJunkFolder("cache"))
    }
}
