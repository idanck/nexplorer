package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetConnectionTest {

    private val nas = NetConnection(
        id = "a1b2c3d4e5f6",
        protocol = NetProtocol.Smb,
        name = "NAS",
        host = "192.168.1.20",
        port = null,
        share = "volume2",
        user = "alex",
        secret = "aXY=:c2Vh",
    )

    @Test
    fun `round trip keeps every field`() {
        val guest = nas.copy(id = "000000000001", name = "", share = "", user = "", port = 4450, domain = "WORK", secret = null)
        assertEquals(listOf(nas, guest), NetConnection.decode(NetConnection.encode(listOf(nas, guest))))
    }

    @Test
    fun `broken or unknown records are skipped without losing the rest`() {
        val raw = """[{"id":"x","protocol":"Ftp","host":"h"},{"nope":1},""" +
            NetConnection.encode(listOf(nas)).removePrefix("[")
        assertEquals(listOf(nas), NetConnection.decode(raw))
        assertEquals(emptyList<NetConnection>(), NetConnection.decode("not json"))
        assertEquals(emptyList<NetConnection>(), NetConnection.decode(null))
    }

    @Test
    fun `root, labels and the server address`() {
        assertEquals("smb://a1b2c3d4e5f6/volume2", nas.root)
        assertEquals("smb://a1b2c3d4e5f6", nas.copy(share = "").root)
        assertEquals("NAS", nas.label)
        assertEquals("192.168.1.20", nas.copy(name = " ").label)
        assertEquals("alex@192.168.1.20", nas.subtitle)
        assertEquals("192.168.1.30:4450", nas.copy(user = "", host = "192.168.1.30", port = 4450).subtitle)
        assertEquals("smb://192.168.1.20/volume2/Photos", nas.displayUrl("${nas.root}/Photos"))
        assertEquals("NAS › volume2 › Photos", nas.displayPath("${nas.root}/Photos"))
        assertEquals(445, nas.effectivePort)
    }

    @Test
    fun `new ids are distinct hex`() {
        val a = NetConnection.newId()
        val b = NetConnection.newId()
        assertNotEquals(a, b)
        assertTrue(a.matches(Regex("[0-9a-f]{12}")))
    }

    private val pi = NetConnection(
        id = "0f0f0f0f0f0f",
        protocol = NetProtocol.Sftp,
        name = "",
        host = "192.168.1.30",
        port = 2222,
        share = "home/pi",
        user = "pi",
        secret = "aXY=:cGs=",
        hostKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAwCQ3YO9UUcgTvXdrMIpuZbCOR6Pv8embB8zCEKNkUf",
        keySecret = "aXY=:a2V5",
    )

    @Test
    fun `sftp keeps the host key and the private key`() {
        assertEquals(listOf(pi), NetConnection.decode(NetConnection.encode(listOf(pi))))
        assertEquals("sftp://0f0f0f0f0f0f/home/pi", pi.root)
        assertEquals("sftp://0f0f0f0f0f0f", pi.copy(share = "").root)
        assertEquals("pi@192.168.1.30:2222", pi.subtitle)
        assertEquals("sftp://192.168.1.30:2222/home/pi/x", pi.displayUrl("${pi.root}/x"))
        assertEquals("sftp://192.168.1.30:2222/", pi.displayUrl("sftp://0f0f0f0f0f0f"))
        assertEquals(22, pi.copy(port = null).effectivePort)
    }

    @Test
    fun `records saved before sftp have no keys`() {
        val old = """[{"id":"a1b2c3d4e5f6","protocol":"Smb","name":"NAS","host":"192.168.1.20","port":null,"share":"volume2","user":"alex","domain":"","secret":"aXY=:c2Vh"}]"""
        assertEquals(listOf(nas), NetConnection.decode(old))
    }

    @Test
    fun `webdav keeps http and roots at the address`() {
        val cloud = NetConnection("0d0d0d0d0d0d", NetProtocol.WebDav, "Cloud", "cloud.example.com", null, "remote.php/dav/files/idan", "idan", secret = "aXY=:cGs=")
        val home = cloud.copy(id = "0e0e0e0e0e0e", plainHttp = true, port = 8080)
        assertEquals(listOf(cloud, home), NetConnection.decode(NetConnection.encode(listOf(cloud, home))))
        assertEquals("dav://0d0d0d0d0d0d", cloud.root)
        assertEquals(443, cloud.effectivePort)
        assertEquals(80, cloud.copy(plainHttp = true).effectivePort)
        assertEquals("https://cloud.example.com/remote.php/dav/files/idan/Photos", cloud.displayUrl("${cloud.root}/Photos"))
        assertEquals("http://nas:5005/dav", cloud.copy(host = "nas", port = 5005, share = "dav", plainHttp = true).displayUrl(cloud.root))
        assertEquals("idan@nas:5005", cloud.copy(host = "nas", port = 5005, plainHttp = true).subtitle)
        assertEquals("Cloud › Photos", cloud.displayPath("${cloud.root}/Photos"))
        // כתובת אמיתית — "ערוך" מדביק אותה בחזרה ל-DavAddress.
        val odd = cloud.copy(host = "fe80::1", port = 8443, share = "dav/a b", plainHttp = false)
        assertEquals("https://[fe80::1]:8443/dav/a%20b/x%23%25%3F", odd.displayUrl("${odd.root}/x#%?"))
        assertEquals(com.idanck.nexplorer.core.DavAddress("fe80::1", 8443, "dav/a b", false), com.idanck.nexplorer.core.DavAddress.parse(odd.displayUrl(odd.root)))
    }
}
