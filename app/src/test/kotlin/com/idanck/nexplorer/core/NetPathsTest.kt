package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetPathsTest {

    private val root = NetPaths.root(NetProtocol.Smb, "a1b2c3")

    @Test
    fun `remote paths are recognized and local ones are not`() {
        assertEquals("smb://a1b2c3", root)
        assertTrue(NetPaths.isRemote(root))
        assertTrue(NetPaths.isRemote("$root/volume2/Photos"))
        assertFalse(NetPaths.isRemote("/storage/emulated/0"))
        assertFalse(NetPaths.isRemote("smb:/broken"))
        assertEquals(NetProtocol.Smb, NetPaths.protocolOf("$root/x"))
        assertNull(NetPaths.protocolOf("/sdcard"))
    }

    @Test
    fun `segments, names and parents`() {
        val photo = "$root/volume2/Photos/2026/a.jpg"
        assertEquals("a1b2c3", NetPaths.connectionId(photo))
        assertEquals(listOf("volume2", "Photos", "2026", "a.jpg"), NetPaths.segments(photo))
        assertEquals("a.jpg", NetPaths.name(photo))
        assertEquals("$root/volume2/Photos/2026", NetPaths.parent(photo))
        assertEquals(root, NetPaths.parent("$root/volume2"))
        assertNull(NetPaths.parent(root))
        assertEquals("", NetPaths.name(root))
        assertEquals(emptyList<String>(), NetPaths.segments(root))
        assertEquals("$root/volume2", NetPaths.child(root, "volume2"))
        assertEquals("$root/volume2/x", NetPaths.child("$root/volume2/", "x"))
    }

    @Test
    fun `parts round-trip for a content uri, and odd parts are refused`() {
        val file = "$root/volume2/Photos/דוח #1 50%.pdf"
        assertEquals(listOf("smb", "a1b2c3", "volume2", "Photos", "דוח #1 50%.pdf"), NetPaths.parts(file))
        assertEquals(file, NetPaths.fromParts(NetPaths.parts(file)!!))
        val sftp = NetPaths.child(NetPaths.root(NetProtocol.Sftp, "s1"), "etc")
        assertEquals(sftp, NetPaths.fromParts(NetPaths.parts(sftp)!!))
        assertEquals("dav://d1", NetPaths.fromParts(listOf("dav", "d1")))
        assertNull(NetPaths.parts("/storage/emulated/0/a.pdf"))
        assertNull(NetPaths.fromParts(listOf("ftp", "x", "a")))
        assertNull(NetPaths.fromParts(listOf("smb")))
        assertNull(NetPaths.fromParts(listOf("smb", "a1b2c3", "..", "x")))
        assertNull(NetPaths.fromParts(listOf("smb", "a1b2c3", "a/b")))
        assertNull(NetPaths.fromParts(listOf("smb", "", "x")))
    }

    @Test
    fun `parentOf and nameOf work for local and remote paths`() {
        assertEquals("/storage/emulated/0", NetPaths.parentOf("/storage/emulated/0/Download"))
        assertEquals("Download", NetPaths.nameOf("/storage/emulated/0/Download"))
        // java.io.File היה מקצר את "//" — כאן לא.
        assertEquals("$root/share", NetPaths.parentOf("$root/share/dir"))
        assertEquals("dir", NetPaths.nameOf("$root/share/dir"))
    }

    @Test
    fun `same share means a server-side rename is possible`() {
        assertTrue(NetPaths.sameShare("$root/v/a/b.txt", "$root/v/c"))
        assertFalse(NetPaths.sameShare("$root/v/a", "$root/w/a"))
        assertFalse(NetPaths.sameShare("$root/v/a", "smb://other/v/a"))
        assertFalse(NetPaths.sameShare(root, "$root/v"))
        assertFalse(NetPaths.sameShare("/storage/a", "/storage/b"))
    }

    @Test
    fun `smb names refuse what windows refuses`() {
        assertNull(NetPaths.validateName(NetProtocol.Smb, "report (1).pdf"))
        assertNull(NetPaths.validateName(NetProtocol.Smb, "תמונות 2026"))
        listOf("a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\\b").forEach {
            assertNotNull(it, NetPaths.validateName(NetProtocol.Smb, it))
        }
        assertNotNull(NetPaths.validateName(NetProtocol.Smb, "name."))
        assertNotNull(NetPaths.validateName(NetProtocol.Smb, "name "))
        // הכללים הכלליים עדיין חלים.
        assertNotNull(NetPaths.validateName(NetProtocol.Smb, "a/b"))
        assertNotNull(NetPaths.validateName(NetProtocol.Smb, ""))
    }

    @Test
    fun `sftp paths are full server paths from the root`() {
        val sftp = NetPaths.root(NetProtocol.Sftp, "f00d")
        assertEquals("sftp://f00d", sftp)
        assertEquals(NetProtocol.Sftp, NetPaths.protocolOf("$sftp/home/pi"))
        assertEquals(listOf("home", "pi", "x.txt"), NetPaths.segments("$sftp/home/pi/x.txt"))
        assertEquals(sftp, NetPaths.parent("$sftp/home"))
        // "/" של השרת הוא תיקייה רגילה, לא רשימת שיתופים.
        assertFalse(NetPaths.isShareList(sftp))
        assertFalse(NetPaths.isShare("$sftp/home"))
        assertTrue(NetPaths.isFixed(sftp))
        assertFalse(NetPaths.isFixed("$sftp/home"))
        // שינוי שם בכל השרת; לא בין שרתים ולא בין פרוטוקולים.
        assertTrue(NetPaths.sameShare("$sftp/home/a", "$sftp/mnt/usb/a"))
        assertFalse(NetPaths.sameShare("$sftp/home/a", "sftp://other/home/a"))
        assertFalse(NetPaths.sameShare("$sftp/home/a", "smb://f00d/home/a"))
        assertNull(NetPaths.validateName(NetProtocol.Sftp, "a:b*?.txt."))
        assertNotNull(NetPaths.validateName(NetProtocol.Sftp, "a/b"))
    }

    @Test
    fun `smb share list and shares are fixed`() {
        assertTrue(NetPaths.isShareList(root))
        assertTrue(NetPaths.isShare("$root/volume2"))
        assertFalse(NetPaths.isShare("$root/volume2/Photos"))
        assertTrue(NetPaths.isFixed(root))
        assertTrue(NetPaths.isFixed("$root/volume2"))
        assertFalse(NetPaths.isFixed("$root/volume2/Photos"))
        assertFalse(NetPaths.isFixed("/storage/emulated/0"))
    }
}
