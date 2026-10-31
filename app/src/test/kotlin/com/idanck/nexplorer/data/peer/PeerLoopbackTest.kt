package com.idanck.nexplorer.data.peer

import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.data.net.NetConnection
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** שרת אמיתי על 127.0.0.1 ותיקייה זמנית: חיבור בקוד, ואז כל הפעולות של [PeerFs]. */
class PeerLoopbackTest {

    private lateinit var root: File
    private lateinit var host: TestHost
    private lateinit var server: PeerServer
    private val phone = PeerIdentity("phone-1", "הטלפון של alex")

    private class TestHost(override val root: File) : PeerHost {
        override val identity = PeerIdentity("tv-1", "סלון")
        val keys = ConcurrentHashMap<String, ByteArray>()
        val codes = LinkedBlockingQueue<PairingRequest>()
        val ended = LinkedBlockingQueue<PairingRequest>()
        @Volatile var accept = true
        @Volatile var lastOffer: List<OfferedFile> = emptyList()
        override fun keyFor(peerId: String) = keys[peerId]?.copyOf()
        override fun autoAccept(peerId: String) = false
        override fun onPairing(request: PairingRequest) { codes.put(request) }
        override fun onPairingEnded(request: PairingRequest) { ended.put(request) }
        override fun onPaired(peerId: String, peerName: String, key: ByteArray) { keys[peerId] = key.copyOf() }
        override fun onOffer(peerId: String, files: List<OfferedFile>, total: Long, timeoutMillis: Long): Boolean {
            lastOffer = files
            return accept
        }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("peer").toFile()
        host = TestHost(root)
        server = PeerServer(host, InetAddress.getLoopbackAddress()).start()
    }

    @After
    fun tearDown() {
        server.close()
        root.deleteRecursively()
    }

    private val address get() = InetSocketAddress(InetAddress.getLoopbackAddress(), server.port)

    private fun pair(): PairedPeer {
        val client = PairingClient(address, phone)
        client.start()
        val shown = host.codes.poll(5, TimeUnit.SECONDS) ?: error("no code shown")
        assertEquals("הטלפון של alex", shown.peerName)
        return client.confirm(shown.code)
    }

    private fun fs(paired: PairedPeer): PeerFs {
        val connection = NetConnection(id = "c1", protocol = NetProtocol.Peer, name = paired.name, host = paired.id, port = null, share = "", user = "")
        return PeerFs(connection, paired.key, { phone }) { id -> if (id == "tv-1") address else null }
    }

    @Test
    fun pairsAndStoresTheSameKeyOnBothSides() {
        val paired = pair()
        assertEquals("tv-1", paired.id)
        assertEquals("סלון", paired.name)
        assertArrayEquals(paired.key, host.keys["phone-1"])
        assertNotNull(host.ended.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun wrongCodeStoresNothing() {
        val client = PairingClient(address, phone)
        client.start()
        val shown = host.codes.poll(5, TimeUnit.SECONDS)!!
        val wrong = ((shown.code.toInt() + 1) % 1_000_000).toString().padStart(6, '0')
        try {
            client.confirm(wrong)
            fail("expected WrongCode")
        } catch (_: PairingClient.WrongCode) {
        }
        client.close()
        assertNotNull(host.ended.poll(5, TimeUnit.SECONDS))
        assertTrue(host.keys.isEmpty())
    }

    @Test
    fun unpairedSessionIsRefused() {
        try {
            PeerSession.open(address, phone, PeerCrypto.random(32))
            fail("expected refusal")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("חבר אותו מחדש"))
        }
    }

    @Test
    fun wrongKeyCannotTalk() {
        pair()
        val fake = PairedPeer("tv-1", "סלון", PeerCrypto.random(32))
        try {
            fs(fake).list("peer://c1")
            fail("expected failure")
        } catch (_: IOException) {
        }
    }

    @Test
    fun fileOperations() {
        File(root, "Movies").mkdir()
        File(root, "Movies/a.txt").writeText("hello world")
        val fs = fs(pair())
        val list = fs.list("peer://c1")
        assertEquals(listOf("Movies"), list.map { it.name })
        assertTrue(list.single().isDirectory)
        assertEquals("peer://c1/Movies", list.single().path)

        val a = fs.stat("peer://c1/Movies/a.txt")!!
        assertEquals(11, a.size)
        assertNull(fs.stat("peer://c1/Movies/none"))
        assertEquals("world", fs.openRead("peer://c1/Movies/a.txt", 6).use { String(it.readBytes()) })

        // קובץ גדול מכמה מסגרות.
        val big = ByteArray(300_000) { (it % 251).toByte() }
        fs.openWrite("peer://c1/Movies/big.bin", big.size.toLong(), 1_600_000_000_000).use { it.write(big) }
        assertArrayEquals(big, File(root, "Movies/big.bin").readBytes())
        assertEquals(1_600_000_000_000, File(root, "Movies/big.bin").lastModified())
        assertArrayEquals(big.copyOfRange(100_000, big.size), fs.openRead("peer://c1/Movies/big.bin", 100_000).use { it.readBytes() })

        try {
            fs.openWrite("peer://c1/Movies/a.txt").close()
            fail("overwrite must fail")
        } catch (e: IOException) {
            assertEquals("כבר קיים פריט בשם הזה", e.message)
        }

        fs.mkdir("peer://c1/Movies/Kids")
        assertTrue(File(root, "Movies/Kids").isDirectory)
        fs.rename("peer://c1/Movies/a.txt", "peer://c1/Movies/Kids/b.txt")
        assertTrue(File(root, "Movies/Kids/b.txt").isFile)
        fs.delete("peer://c1/Movies/Kids/b.txt", false)
        fs.delete("peer://c1/Movies/Kids", true)
        assertFalse(File(root, "Movies/Kids").exists())
        assertNotNull(fs.space("peer://c1"))
        // השיחה המשותפת עדיין תקינה אחרי שגיאה של פעולה.
        try {
            fs.mkdir("peer://c1/Movies")
            fail("mkdir over existing must fail")
        } catch (e: IOException) {
            assertEquals("כבר קיים פריט בשם הזה", e.message)
        }
        assertEquals(listOf("big.bin"), fs.list("peer://c1/Movies").map { it.name })
        fs.close()
    }

    @Test
    fun pathsCannotEscapeTheRoot() {
        val fs = fs(pair())
        for (bad in listOf("peer://c1/../etc", "peer://c1/Movies/../../x")) {
            try {
                fs.list(bad)
                fail("escaped with $bad")
            } catch (e: IOException) {
                assertEquals("נתיב לא תקין", e.message)
            }
        }
    }

    @Test
    fun offerAcceptedReturnsReceiveFolder() {
        val fs = fs(pair())
        val dir = fs.offer(listOf(OfferedFile("x.mp4", 10), OfferedFile("y.jpg", 5)))
        assertEquals("peer://c1/Download/nexplorer", dir)
        assertTrue(File(root, "Download/nexplorer").isDirectory)
        assertEquals(listOf("x.mp4", "y.jpg"), host.lastOffer.map { it.name })
        host.accept = false
        assertNull(fs.offer(listOf(OfferedFile("z", 1))))
        assertEquals(NetPaths.root(NetProtocol.Peer, "c1"), "peer://c1")
    }
}
