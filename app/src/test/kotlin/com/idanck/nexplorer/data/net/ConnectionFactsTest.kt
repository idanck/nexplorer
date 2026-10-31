package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [ConnectionFacts] — השורות של "פרטי חיבור" (1.14): בלי סיסמאות, ולפי סוג החיבור. */
class ConnectionFactsTest {

    private fun map(facts: List<ConnectionFacts.Fact>) = facts.associate { it.label to it.value }

    @Test
    fun `quickconnect shows the id, the address in use and when it was checked`() {
        val qc = NetConnection("0f0f0f0f0f0f", NetProtocol.Synology, "", "", null, "", "alex", secret = "aXY=:cGs=", quickConnect = "my-nas")
        val status = SynologyFs.Status(QuickConnect.Route.Relay, "my-nas.de1.quickconnect.to", 443, checkedAt = 1_000_000)
        val facts = map(ConnectionFacts.of(qc, status, now = 1_000_000 + 5 * 60_000))
        assertEquals("Synology · QuickConnect", facts["סוג"])
        assertEquals("my-nas", facts["מזהה"])
        assertEquals("alex", facts["משתמש"])
        assertEquals("my-nas.de1.quickconnect.to", facts["כתובת בפועל"])
        assertEquals("https · תעודה תקפה", facts["אבטחה"])
        assertEquals("לפני 5 דק׳", facts["נבדק"])
        assertFalse(facts.containsKey("שיתוף"))
        assertFalse(facts.values.any { it.contains("aXY") })
    }

    @Test
    fun `home network synology, smb and sftp`() {
        val home = NetConnection("1a1a1a1a1a1a", NetProtocol.Synology, "", "192.168.1.20", null, "", "alex", hostKey = "x509-sha256 AB")
        val h = map(ConnectionFacts.of(home, null, 0))
        assertEquals("Synology · רשת ביתית", h["סוג"])
        assertEquals("192.168.1.20", h["שרת"])
        assertEquals("כל השיתופים", h["שיתוף"])
        assertEquals("https://192.168.1.20:5001", h["כתובת בפועל"])
        assertEquals("https · תעודה שאושרה", h["אבטחה"])
        assertFalse(h.containsKey("נבדק"))

        val smb = NetConnection("2b2b2b2b2b2b", NetProtocol.Smb, "NAS", "192.168.1.20", 4450, "volume2", "")
        val s = map(ConnectionFacts.of(smb, null, 0))
        assertEquals("192.168.1.20:4450", s["שרת"])
        assertEquals("volume2", s["שיתוף"])
        assertEquals("אורח", s["משתמש"])
        assertFalse(s.containsKey("אבטחה"))

        val sftp = NetConnection("3c3c3c3c3c3c", NetProtocol.Sftp, "Pi", "192.168.1.30", 2222, "home/pi", "pi", hostKey = "ssh-ed25519 AAAA")
        val p = map(ConnectionFacts.of(sftp, null, 0))
        assertEquals("/home/pi", p["תיקייה"])
        assertEquals("SSH · מפתח השרת אושר", p["אבטחה"])
        assertEquals("QuickConnect", ConnectionFacts.kind(NetConnection("x", NetProtocol.Synology, "", "", null, "", "a", quickConnect = "n")))
        assertEquals("עכשיו", ConnectionFacts.ago(10_000))
        assertEquals("לפני 2 שע׳", ConnectionFacts.ago(125 * 60_000L))
    }
}
