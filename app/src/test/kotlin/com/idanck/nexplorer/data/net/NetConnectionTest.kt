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
        host = "192.168.50.171",
        port = null,
        share = "volume2",
        user = "idanc",
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
        assertEquals("192.168.50.171", nas.copy(name = " ").label)
        assertEquals("idanc@192.168.50.171", nas.subtitle)
        assertEquals("192.168.50.83:4450", nas.copy(user = "", host = "192.168.50.83", port = 4450).subtitle)
        assertEquals("smb://192.168.50.171/volume2/Photos", nas.displayUrl("${nas.root}/Photos"))
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
        host = "192.168.50.83",
        port = 41725,
        share = "home/idan",
        user = "idan",
        secret = "aXY=:cGs=",
        hostKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIARk2H5Oiday1HWmOEmiYJHdVqW5oLiGDoUrxA4N8FWP",
        keySecret = "aXY=:a2V5",
    )

    @Test
    fun `sftp keeps the host key and the private key`() {
        assertEquals(listOf(pi), NetConnection.decode(NetConnection.encode(listOf(pi))))
        assertEquals("sftp://0f0f0f0f0f0f/home/idan", pi.root)
        assertEquals("sftp://0f0f0f0f0f0f", pi.copy(share = "").root)
        assertEquals("idan@192.168.50.83:41725", pi.subtitle)
        assertEquals("sftp://192.168.50.83:41725/home/idan/x", pi.displayUrl("${pi.root}/x"))
        assertEquals("sftp://192.168.50.83:41725/", pi.displayUrl("sftp://0f0f0f0f0f0f"))
        assertEquals(22, pi.copy(port = null).effectivePort)
    }

    @Test
    fun `records saved before sftp have no keys`() {
        val old = """[{"id":"a1b2c3d4e5f6","protocol":"Smb","name":"NAS","host":"192.168.50.171","port":null,"share":"volume2","user":"idanc","domain":"","secret":"aXY=:c2Vh"}]"""
        assertEquals(listOf(nas), NetConnection.decode(old))
    }
}
