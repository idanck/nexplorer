package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DavAddressTest {

    @Test
    fun `nextcloud, synology and bare hosts`() {
        assertEquals(DavAddress("cloud.example.com", null, "remote.php/dav/files/idan", false), DavAddress.parse("https://cloud.example.com/remote.php/dav/files/idan/"))
        assertEquals(DavAddress("nas.local", 5006, "dav", false), DavAddress.parse("nas.local:5006/dav"))
        assertEquals(DavAddress("192.168.50.171", 5005, "", true), DavAddress.parse(" http://192.168.50.171:5005 "))
        assertEquals(DavAddress("host", null, "", true), DavAddress.parse("http://host:80/"))
        assertEquals(DavAddress("host", null, "a", false), DavAddress.parse("davs://host:443/a"))
        assertEquals(DavAddress("fe80::1", 8443, "dav", false), DavAddress.parse("https://[fe80::1]:8443/dav"))
    }

    @Test
    fun `paths are decoded and spaces are allowed`() {
        assertEquals("remote.php/dav/files/Idan Cohen", DavAddress.parse("https://h/remote.php/dav/files/Idan Cohen")!!.path)
        assertEquals("files/תמונות", DavAddress.parse("https://h/files/%D7%AA%D7%9E%D7%95%D7%A0%D7%95%D7%AA")!!.path)
    }

    @Test
    fun `not a server address`() {
        assertNull(DavAddress.parse(""))
        assertNull(DavAddress.parse("ftp://host/a"))
        assertNull(DavAddress.parse("https://"))
        assertNull(DavAddress.parse("https://user:pw@host/a"))
        assertNull(DavAddress.parse("https://host/a?x=1"))
        assertNull(DavAddress.parse("https://host:99999/a"))
    }
}
