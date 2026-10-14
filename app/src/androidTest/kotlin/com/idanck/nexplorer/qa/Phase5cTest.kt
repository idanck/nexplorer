package com.idanck.nexplorer.qa

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.data.net.ConnectionSecrets
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteFs
import com.idanck.nexplorer.data.net.WebDavFs
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * שלב 5ג: מקום ברשת ב-WebDAV, מול Apache על המארח (‏.github/qa/webdav-server.sh): ‏http ב-10.0.2.2:8080
 * ו-https בתעודה עצמית ב-8443, הכתובת `/dav/`, משתמש qa. טביעת האצבע של התעודה —
 * /sdcard/nexplorer-qa-keys/dav-fingerprint.txt (instrumented.sh דוחף אותה).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase5cTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container

    private val password = "qa-pass"
    private val fingerprint get() = File("/sdcard/nexplorer-qa-keys/dav-fingerprint.txt").readText().trim()
    private val http = NetConnection("qa0000000003", NetProtocol.WebDav, "Cloud", "10.0.2.2", 8080, "dav", "qa", plainHttp = true)
    private val base = NetPaths.child(http.root, "nexplorer-qa")

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        runBlocking { container.settings.update { it.copy(connections = emptyList(), favorites = emptyList()) } }
        runBlocking { container.remote.forget(http.id) }
        WebDavFs(http, password.toCharArray()).use { fs ->
            wipe(fs, base)
            fs.mkdir(base)
            fs.openWrite("$base/readme.txt").use { it.write("hello from apache\n".toByteArray()) }
            fs.mkdir("$base/Photos")
            fs.openWrite("$base/Photos/shot.png").use { out -> File(root, "Photos/shot-1.png").inputStream().use { it.copyTo(out) } }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        runBlocking { container.settings.update { it.copy(connections = emptyList()) } }
        runCatching { WebDavFs(http, password.toCharArray()).use { wipe(it, base) } }
    }

    private fun wipe(fs: RemoteFs, path: String) {
        val entry = fs.stat(path) ?: return
        if (entry.isDirectory) fs.list(path).forEach { wipe(fs, it.path) }
        fs.delete(path, entry.isDirectory)
    }

    private fun save(connection: NetConnection) = runBlocking {
        container.settings.update { it.copy(connections = listOf(connection)) }
        container.settings.state.first { connection in it.connections }
        container.remote.forget(connection.id)
    }

    private fun remoteStat(path: String) = container.remote.fsFor(path).stat(path)

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun launch(path: String) {
        scenario = Qa.launch(path)
    }

    private fun logged(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e("Phase5cTest", "$name failed", t)
            runCatching { Qa.shell("screencap -p /sdcard/Download/nexplorer-qa/failed-5c-$name.png") }
            throw t
        }
    }

    @Test
    fun t01_addWebDavOverHttpsTrustsTheSelfSignedCertificate() = logged("t01") {
        launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("הוסף מקום"))
        compose.onNodeWithText("הוסף מקום").performClick()
        compose.waitFor(hasText("פרטי חיבור"))
        compose.onNodeWithText("WebDAV").performClick()
        compose.waitFor(hasText("כתובת"))

        compose.onNodeWithContentDescription("כתובת").performTextInput("https://10.0.2.2:8443/dav/")
        compose.onNodeWithContentDescription("משתמש").performTextInput("qa")
        compose.onNodeWithContentDescription("סיסמה").performTextInput("wrong")
        Qa.screenshot(compose, "130-add-webdav")

        // תעודה עצמית: קודם טביעת האצבע — אותה ש-openssl הדפיס בשרת.
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("לסמוך על השרת?"), 30_000)
        compose.waitFor(hasText(fingerprint))
        Qa.screenshot(compose, "131-webdav-trust-certificate")
        compose.onNodeWithText("סמוך והתחבר").performClick()
        compose.waitFor(hasText("שם המשתמש או הסיסמה שגויים"), 30_000)
        assertTrue("חיבור שנכשל לא נשמר", container.settings.state.value.connections.isEmpty())

        compose.onNodeWithContentDescription("סיסמה").performTextClearance()
        compose.onNodeWithContentDescription("סיסמה").performTextInput(password)
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("release-smoke-dav.txt"), 30_000)
        compose.waitFor(hasText("nexplorer-qa"))
        Qa.screenshot(compose, "132-webdav-root")
        val saved = container.settings.state.value.connections.single()
        assertEquals(NetProtocol.WebDav, saved.protocol)
        assertEquals("dav", saved.share)
        assertEquals(8443, saved.port)
        assertEquals("x509-sha256 $fingerprint", saved.hostKey)
        assertEquals(password, String(ConnectionSecrets.decrypt(requireNotNull(saved.secret))))

        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("10.0.2.2 · WebDAV"))
        Qa.screenshot(compose, "133-drawer-with-webdav")
    }

    @Test
    fun t02_openCopyMoveAndEditOverHttp() = logged("t02") {
        save(http.copy(secret = ConnectionSecrets.encrypt(password.toCharArray())))
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot.png"), 30_000)
        compose.onNodeWithText("shot.png").performClick()
        compose.waitFor(hasText("1 / 1"), 30_000)
        Qa.screenshot(compose, "134-webdav-image-opened")
        Qa.device.pressBack()
        compose.waitFor(hasText("shot.png"))

        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "Documents").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/Documents/report.pdf")?.size == 48 * 1024L }
        assertNotNull(remoteStat("$base/Documents/דוח שנתי 2026.txt"))
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "big.bin").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/big.bin")?.size == 3 * 1024 * 1024L }
        assertEquals(
            File(root, "big.bin").readBytes().toList().hashCode(),
            container.remote.fsFor(base).openRead("$base/big.bin").use { it.readBytes() }.toList().hashCode(),
        )

        container.transfers.enqueue(TransferKind.Move, listOf("$base/readme.txt"), File(root, "Empty").absolutePath)
        compose.waitForDisk(60_000) { File(root, "Empty/readme.txt").isFile && remoteStat("$base/readme.txt") == null }
        assertEquals("hello from apache\n", File(root, "Empty/readme.txt").readText())

        // בתוך השרת: MOVE.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/Documents"), "$base/Photos")
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents/notes.txt") != null && remoteStat("$base/Documents") == null }

        Qa.device.pressBack()
        compose.waitFor(hasText("big.bin"), 30_000)
        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("תיקייה חדשה"))
        compose.onNodeWithText("תיקייה חדשה").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("Created")
        compose.onNodeWithText("צור").performClick()
        compose.waitFor(hasText("Created"), 30_000)
        compose.onNodeWithText("Created").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("שנה שם").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        compose.onNodeWithText("שנה").performClick()
        compose.waitFor(hasText("Renamed"), 30_000)
        assertNull(remoteStat("$base/Created"))

        container.transfers.enqueue(TransferKind.Delete, listOf("$base/Photos", "$base/Renamed"), null)
        compose.waitForDisk(60_000) { remoteStat("$base/Photos") == null && remoteStat("$base/Renamed") == null }
        compose.waitGone(hasText("Renamed"), 15_000)
        Qa.screenshot(compose, "135-webdav-after-transfers")
    }

    @Test
    fun t03_aChangedCertificateIsBlockedUntilTrustedAgain() = logged("t03") {
        val https = http.copy(port = 8443, plainHttp = false, secret = ConnectionSecrets.encrypt(password.toCharArray()))
        save(https.copy(hostKey = "x509-sha256 00:11:22:33"))
        launch(https.root)
        compose.waitFor(hasText("התעודה של השרת השתנתה — ערוך את החיבור כדי לאשר אותה מחדש"), 30_000)
        Qa.screenshot(compose, "136-webdav-certificate-changed")

        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("Cloud · WebDAV"))
        compose.onNodeWithText("Cloud · WebDAV").performTouchInput { longClick() }
        compose.onNodeWithText("ערוך").performClick()
        compose.waitFor(hasText("ערוך מקום"))
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("התעודה של השרת השתנתה"), 30_000)
        Qa.screenshot(compose, "137-webdav-trust-changed")
        compose.onNodeWithText("סמוך על התעודה החדשה").performClick()
        compose.waitFor(hasText("release-smoke-dav.txt"), 30_000)
        assertEquals("x509-sha256 $fingerprint", container.settings.state.value.connections.single().hostKey)
    }

    /**
     * תעודה של רשות מוכרת (ערוץ העדכונים, Let's Encrypt / Cloudflare) — לא "תעודה עצמית". באנדרואיד,
     * עם domain-config ב-network_security_config, בדיקה בלי שם השרת נדחית — וכל שרת רגיל היה נשאל עליו.
     */
    @Test
    fun t04_aCaSignedCertificateIsTrustedWithoutAsking() = logged("t04") {
        val public = NetConnection("qa0000000004", NetProtocol.WebDav, "", "nexplorer-updates.idanck.com", null, "", "")
        val result = runBlocking { com.idanck.nexplorer.data.net.RemoteFiles.test(public, null) }
        Log.i("Phase5cTest", "CA-signed: $result")
        assertTrue("$result", result !is com.idanck.nexplorer.data.net.ConnectCheck.TrustHost)
    }
}
