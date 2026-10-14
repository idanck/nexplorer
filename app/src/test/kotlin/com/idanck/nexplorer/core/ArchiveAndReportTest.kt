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
        assertEquals("report.zip", ArchivePaths.defaultArchiveName(listOf("report.pdf"), "Docs", ArchiveFormat.Zip))
        assertEquals("Docs.7z", ArchivePaths.defaultArchiveName(listOf("a", "b"), "Docs", ArchiveFormat.SevenZ))
        assertEquals("photos", ArchivePaths.extractFolderName("photos.zip"))
        assertEquals("backup", ArchivePaths.extractFolderName("backup.tar.gz"))
        assertEquals("logs", ArchivePaths.extractFolderName("logs.TBZ2"))
    }

    @Test
    fun `archive formats by name, longest suffix first`() {
        assertEquals(ArchiveFormat.TarGz, ArchiveFormats.of("a.tar.gz"))
        assertEquals(ArchiveFormat.TarGz, ArchiveFormats.of("A.TGZ"))
        assertEquals(ArchiveFormat.Gz, ArchiveFormats.of("notes.txt.gz"))
        assertEquals(ArchiveFormat.TarXz, ArchiveFormats.of("kernel.tar.xz"))
        assertEquals(ArchiveFormat.SevenZ, ArchiveFormats.of("x.7z"))
        assertNull(ArchiveFormats.of("x.rar"))
        assertNull(ArchiveFormats.of(".zip"))
        assertNull(ArchiveFormats.of("readme.md"))
        assertEquals("notes.txt", ArchiveFormats.baseName("notes.txt.gz"))
        assertEquals("a", ArchiveFormats.baseName("a.tar.bz2"))
    }

    @Test
    fun `switching the format swaps only the archive extension`() {
        assertEquals("report.7z", ArchiveFormats.withExtension("report.zip", ArchiveFormat.SevenZ))
        assertEquals("report.zip", ArchiveFormats.withExtension("report.zip", ArchiveFormat.Zip))
        assertEquals("v1.2.zip", ArchiveFormats.withExtension("v1.2", ArchiveFormat.Zip))
        assertEquals("site.zip", ArchiveFormats.withExtension(" site.tar.gz ", ArchiveFormat.Zip))
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
