package com.idanck.nexplorer.data.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuickConnect] — פענוח התשובה של `get_server_info`. הדוגמה במבנה של תשובה אמיתית (DSM 7.4, 30/10/2026), עם ערכי
 * דוגמה בלבד.
 */
class QuickConnectTest {

    private val sample = JSONObject(
        """
        {"command":"get_server_info","errno":0,"version":1,
         "env":{"control_host":"dec.quickconnect.to","relay_region":"de1"},
         "server":{"serverID":"000000001","ddns":"NULL","fqdn":"NULL",
                   "external":{"ip":"203.0.113.7","ipv6":"::"},
                   "interface":[{"ip":"192.168.1.20","mask":"255.255.255.0","name":"ovs_eth0"}]},
         "service":{"port":5001,"ext_port":0,"relay_ip":"my-nas.de1.quickconnect.to","relay_dn":"my-nas.de1.quickconnect.to",
                    "relay_port":443,"https_ip":"my-nas.de1.quickconnect.to","https_port":443},
         "smartdns":{"host":"MY-NAS.direct.quickconnect.to",
                     "lan":["192-168-1-20.MY-NAS.direct.quickconnect.to","10-0-0-5.MY-NAS.direct.quickconnect.to"],
                     "external":"syn4-abc.my-nas.direct.quickconnect.to"}}
        """.trimIndent(),
    )

    @Test
    fun `local addresses first, in order, with the address inside the name`() {
        val server = QuickConnect.parse(sample, secure = true)
        assertEquals(
            listOf("https://192-168-1-20.my-nas.direct.quickconnect.to:5001/", "https://10-0-0-5.my-nas.direct.quickconnect.to:5001/"),
            server.local.map { it.url.toString() },
        )
        assertTrue(server.local.all { it.route == QuickConnect.Route.Local })
        assertEquals("192.168.1.20", server.local[0].ip?.hostAddress)
        val relay = server.relay!!
        assertEquals("https://my-nas.de1.quickconnect.to/", relay.url.toString())
        assertEquals(QuickConnect.Route.Relay, relay.route)
        assertNull(relay.ip)
        // ה-DNS של החיבור: השם המקומי — לכתובת שבתוכו, בלי לשאול את ה-DNS (שבבית לפעמים חוסם כתובות פרטיות).
        val dns = QuickConnect.resolver(server)
        assertEquals("10.0.0.5", dns.lookup("10-0-0-5.my-nas.direct.quickconnect.to").single().hostAddress)
    }

    @Test
    fun `no relay yet, no local names`() {
        val bare = JSONObject("""{"errno":0,"service":{"port":5001,"relay_dn":"","relay_port":0},"smartdns":{}}""")
        val server = QuickConnect.parse(bare, secure = true)
        assertTrue(server.local.isEmpty())
        assertNull(server.relay)
    }

    @Test
    fun `errors map to hebrew`() {
        assertNull(QuickConnect.error(sample))
        assertEquals(
            "המזהה לא נמצא ב-QuickConnect",
            QuickConnect.error(JSONObject("""{"errno":4,"suberrno":1,"errinfo":"get_server_info.go:69[Alias not found]"}"""))?.message,
        )
        assertEquals("מזהה QuickConnect לא תקין", QuickConnect.error(JSONObject("""{"errno":9}"""))?.message)
        assertTrue(QuickConnect.error(JSONObject("""{"errno":30}"""))!!.message!!.contains("רשת ביתית"))
    }

    @Test
    fun `ids and addresses in names`() {
        assertTrue(QuickConnect.isValidId("my-nas"))
        assertTrue(QuickConnect.isValidId("Nas01"))
        assertFalse(QuickConnect.isValidId("my-nas.quickconnect.to"))
        assertFalse(QuickConnect.isValidId("שם"))
        assertFalse(QuickConnect.isValidId(""))
        assertNull(QuickConnect.ipOf("my-nas.direct.quickconnect.to"))
        assertNull(QuickConnect.ipOf("300-1-1-1.x.direct.quickconnect.to"))
        assertEquals("10.0.2.2", QuickConnect.ipOf("10-0-2-2.my-nas.direct.example")?.hostAddress)
    }
}
