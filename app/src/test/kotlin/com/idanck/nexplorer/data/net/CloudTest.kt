package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTest {

    @Test
    fun `pkce challenge matches rfc 7636 appendix b`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val pkce = Pkce.create()
        // 32 בתים אקראיים ב-base64url בלי ריפוד: 43 תווים (RFC 7636: בין 43 ל-128).
        assertEquals(43, pkce.verifier.length)
        assertTrue(pkce.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(Pkce.challengeOf(pkce.verifier), pkce.challenge)
        assertNotEquals(pkce.verifier, Pkce.create().verifier)
    }

    @Test
    fun `microsoft authorize url carries pkce and the redirect`() {
        val pkce = Pkce("v", "c", "s")
        val store = object : TokenStore {
            override fun get(): String? = null
            override fun put(token: String) = Unit
        }
        val url = MicrosoftAuth("client-1", store).authorizeUrl("com.idanck.nexplorer://oauth", pkce)
        assertEquals("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize", url.newBuilder().query(null).build().toString())
        assertEquals("client-1", url.queryParameter("client_id"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("com.idanck.nexplorer://oauth", url.queryParameter("redirect_uri"))
        assertEquals("c", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("s", url.queryParameter("state"))
        assertTrue(url.queryParameter("scope")!!.split(' ').containsAll(listOf("Files.ReadWrite", "offline_access")))
    }

    @Test
    fun `graph item urls encode every segment`() {
        val drive = "https://graph.microsoft.com/v1.0/me/drive".toHttpUrl()
        assertEquals("https://graph.microsoft.com/v1.0/me/drive/root", OneDriveFs.itemUrl(drive, emptyList(), null).toString())
        assertEquals("https://graph.microsoft.com/v1.0/me/drive/root/children", OneDriveFs.itemUrl(drive, emptyList(), "children").toString())
        assertEquals("https://graph.microsoft.com/v1.0/me/drive/root:/Photos/a.jpg", OneDriveFs.itemUrl(drive, listOf("Photos", "a.jpg"), null).toString())
        assertEquals("https://graph.microsoft.com/v1.0/me/drive/root:/Photos:/children", OneDriveFs.itemUrl(drive, listOf("Photos"), "children").toString())
        // עברית, רווח, # ו-% — מקודדים, ולא נקטעים כמו fragment או כמו קידוד שבור.
        val url = OneDriveFs.itemUrl(drive, listOf("דוחות", "# 100% שנתי.txt"), "content")
        assertEquals("/v1.0/me/drive/root:/%D7%93%D7%95%D7%97%D7%95%D7%AA/%23%20100%25%20%D7%A9%D7%A0%D7%AA%D7%99.txt:/content", url.encodedPath)
        assertEquals(listOf("v1.0", "me", "drive", "root:", "דוחות", "# 100% שנתי.txt:", "content"), url.pathSegments)
    }

    @Test
    fun `cloud connections are labelled by the account`() {
        val drive = NetConnection(id = "c10ud0000001", protocol = NetProtocol.GoogleDrive, name = "", host = DriveFs.HOST, port = null, share = "", user = "alex@example.com")
        assertEquals("alex@example.com", drive.label)
        assertEquals("alex@example.com", drive.subtitle)
        assertEquals("gdrive://c10ud0000001", drive.root)
        assertEquals("gdrive://alex@example.com/Photos/a.jpg", drive.displayUrl("${drive.root}/Photos/a.jpg"))
        assertEquals("Work", drive.copy(name = "Work").label)
        // 1.10: בכותרת ובמגירה — שם השירות (או השם שנבחר), והחשבון בנפרד.
        assertEquals("Google Drive", drive.title)
        assertEquals("alex@example.com", drive.account)
        assertEquals("Work", drive.copy(name = "Work").title)
        assertEquals(null, drive.copy(user = "").account)
        // לפני ההתחברות אין חשבון.
        assertEquals("OneDrive", drive.copy(protocol = NetProtocol.OneDrive, user = "").label)
        assertEquals(listOf(drive), NetConnection.decode(NetConnection.encode(listOf(drive))))
        // שרת מדומה בבדיקות: http ופורט.
        assertEquals("http://10.0.2.2:8090/", CloudHttp.baseUrl(drive.copy(host = "10.0.2.2", port = 8090, plainHttp = true)).toString())
        assertEquals("https://www.googleapis.com/", CloudHttp.baseUrl(drive).toString())
    }

    @Test
    fun `cloud paths and names`() {
        val root = NetPaths.root(NetProtocol.OneDrive, "beef")
        assertEquals(NetProtocol.OneDrive, NetPaths.protocolOf("$root/a"))
        assertEquals(NetProtocol.GoogleDrive, NetPaths.protocolOf("gdrive://beef/a"))
        assertTrue(NetPaths.isFixed(root))
        assertTrue(NetPaths.sameShare("$root/a/x", "$root/b/x"))
        // OneDrive — כמו Windows; Drive — כל שם.
        assertNotNull(NetPaths.validateName(NetProtocol.OneDrive, "a:b.txt"))
        assertNotNull(NetPaths.validateName(NetProtocol.OneDrive, "name."))
        assertNotNull(NetPaths.validateName(NetProtocol.OneDrive, "CON.txt"))
        assertNotNull(NetPaths.validateName(NetProtocol.OneDrive, " lead"))
        assertNull(NetPaths.validateName(NetProtocol.OneDrive, "דוח (1).pdf"))
        assertNull(NetPaths.validateName(NetProtocol.GoogleDrive, "a:b*?.txt."))
    }
}
