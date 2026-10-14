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
import androidx.compose.ui.test.performScrollTo
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
import com.idanck.nexplorer.data.net.ConnectCheck
import com.idanck.nexplorer.data.net.ConnectionSecrets
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.net.RemoteFs
import com.idanck.nexplorer.data.net.SftpFs
import com.idanck.nexplorer.data.net.SshHostKeys
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
 * שלב 5ב: מקום ברשת ב-SFTP, מול OpenSSH על המארח (‏.github/qa/sftp-server.sh): 10.0.2.2:2222, משתמש qa.
 * המפתחות וטביעת האצבע של השרת — ב-/sdcard/nexplorer-qa-keys (instrumented.sh דוחף אותם). ב-API 29
 * אין Ed25519 ו-X25519 במערכת — כאן נבדק שהם מגיעים מהספק של האפליקציה (SshCrypto).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase5bTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container

    private val keys = File("/sdcard/nexplorer-qa-keys")
    private val password = "qa-pass"
    private val home = "tmp/nexplorer-sftp/home"
    private val draft = NetConnection("qa0000000002", NetProtocol.Sftp, "Pi", "10.0.2.2", 2222, home, "qa")
    private val base = NetPaths.child(draft.root, "nexplorer-qa")
    private lateinit var server: NetConnection

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        runBlocking { container.settings.update { it.copy(connections = emptyList(), favorites = emptyList()) } }
        runBlocking { container.remote.forget(draft.id) }
        val ask = runBlocking { RemoteFiles.test(draft, password.toCharArray()) }
        assertTrue("$ask", ask is ConnectCheck.TrustHost)
        server = draft.copy(hostKey = (ask as ConnectCheck.TrustHost).hostKey)
        SftpFs(server, password.toCharArray(), null).use { fs ->
            wipe(fs, base)
            fs.mkdir(base)
            fs.openWrite("$base/readme.txt").use { it.write("hello from sshd\n".toByteArray()) }
            fs.mkdir("$base/Photos")
            fs.openWrite("$base/Photos/shot.png").use { out -> File(root, "Photos/shot-1.png").inputStream().use { it.copyTo(out) } }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) runCatching { SftpFs(server, password.toCharArray(), null).use { wipe(it, base) } }
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

    private fun withPassword() = server.copy(secret = ConnectionSecrets.encrypt(password.toCharArray()))

    private fun remoteStat(path: String) = container.remote.fsFor(path).stat(path)

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun launch(path: String) {
        scenario = Qa.launch(path)
    }

    private fun logged(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e("Phase5bTest", "$name failed", t)
            runCatching { Qa.shell("screencap -p /sdcard/Download/nexplorer-qa/failed-5b-$name.png") }
            throw t
        }
    }

    @Test
    fun t01_addSftpPlaceAsksAboutTheHostKey() = logged("t01") {
        launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("הוסף מקום"))
        compose.onNodeWithText("הוסף מקום").performClick()
        compose.waitFor(hasText("פרטי חיבור"))
        compose.onNodeWithText("SFTP").performClick()
        compose.waitFor(hasText("מפתח פרטי"))

        compose.onNodeWithContentDescription("שרת").performTextInput("10.0.2.2")
        compose.onNodeWithContentDescription("משתמש").performTextInput("qa")
        compose.onNodeWithContentDescription("סיסמה").performTextInput("wrong")
        button("מתקדם").performClick()
        compose.waitFor(hasText("שם במגירה"))
        compose.onNodeWithContentDescription("פורט").performScrollTo().performTextInput("2222")
        Qa.screenshot(compose, "120-add-sftp")

        // שרת חדש: קודם טביעת האצבע — אותה ש-ssh-keygen מציג בשרת.
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("לסמוך על השרת?"), 30_000)
        val fingerprint = File(keys, "host-fingerprint.txt").readText().trim()
        compose.waitFor(hasText(fingerprint))
        Qa.screenshot(compose, "121-sftp-trust-host")
        compose.onNodeWithText("סמוך והתחבר").performClick()
        compose.waitFor(hasText("שם המשתמש או הסיסמה שגויים"), 30_000)
        assertTrue("חיבור שנכשל לא נשמר", container.settings.state.value.connections.isEmpty())

        // אותו שרת — לא שואל שוב.
        compose.onNodeWithContentDescription("סיסמה").performTextClearance()
        compose.onNodeWithContentDescription("סיסמה").performTextInput(password)
        button("בדוק והתחבר").performClick()
        // בלי תיקייה: הבית של qa בשרת.
        compose.waitFor(hasText("release-smoke-sftp.txt"), 30_000)
        compose.waitFor(hasText("nexplorer-qa"))
        Qa.screenshot(compose, "122-sftp-home")
        val saved = container.settings.state.value.connections.single()
        assertEquals(NetProtocol.Sftp, saved.protocol)
        assertEquals(home, saved.share)
        assertEquals(fingerprint, SshHostKeys.fingerprint(requireNotNull(saved.hostKey)))
        assertEquals(password, String(ConnectionSecrets.decrypt(requireNotNull(saved.secret))))

        compose.onNodeWithText("nexplorer-qa").performClick()
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("10.0.2.2 · SFTP"))
        Qa.screenshot(compose, "123-drawer-with-sftp")
    }

    @Test
    fun t02_openCopyMoveAndEditOnTheServer() = logged("t02") {
        save(withPassword())
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot.png"), 30_000)
        compose.onNodeWithText("shot.png").performClick()
        compose.waitFor(hasText("1 / 1"), 30_000)
        Qa.screenshot(compose, "124-sftp-image-opened")
        Qa.device.pressBack()
        compose.waitFor(hasText("shot.png"))

        // מהמכשיר לשרת — תיקייה שלמה, עם שם בעברית.
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "Documents").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/Documents/report.pdf")?.size == 48 * 1024L }
        assertNotNull(remoteStat("$base/Documents/דוח שנתי 2026.txt"))
        // קובץ גדול: כתיבה בגושים (שרת SFTP מסרב לבקשה של 256KB).
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "big.bin").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/big.bin")?.size == 3 * 1024 * 1024L }
        assertEquals(
            File(root, "big.bin").readBytes().toList().hashCode(),
            container.remote.fsFor(base).openRead("$base/big.bin").use { it.readBytes() }.toList().hashCode(),
        )

        // מהשרת למכשיר בהעברה.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/readme.txt"), File(root, "Empty").absolutePath)
        compose.waitForDisk(60_000) { File(root, "Empty/readme.txt").isFile && remoteStat("$base/readme.txt") == null }
        assertEquals("hello from sshd\n", File(root, "Empty/readme.txt").readText())

        // בתוך השרת: שינוי שם.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/Documents"), "$base/Photos")
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents/notes.txt") != null && remoteStat("$base/Documents") == null }

        // מהממשק: תיקייה חדשה, שינוי שם ומחיקה.
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
        Qa.screenshot(compose, "125-sftp-after-transfers")
    }

    @Test
    fun t03_privateKeysWithAndWithoutAPassphrase() = logged("t03") {
        val plain = File(keys, "qa_ed25519").readText().toCharArray()
        save(server.copy(keySecret = ConnectionSecrets.encrypt(plain)))
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        scenario.close()

        // מפתח עם סיסמה, בלי הסיסמה — הודעה ברורה.
        val locked = File(keys, "qa_ed25519_pass").readText().toCharArray()
        save(server.copy(keySecret = ConnectionSecrets.encrypt(locked)))
        launch(base)
        compose.waitFor(hasText("המפתח מוגן בסיסמה — הזן אותה בשדה הסיסמה"), 30_000)
        Qa.screenshot(compose, "126-sftp-key-needs-passphrase")
        scenario.close()

        save(server.copy(keySecret = ConnectionSecrets.encrypt(locked), secret = ConnectionSecrets.encrypt("key-pass".toCharArray())))
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        Qa.screenshot(compose, "127-sftp-with-key")
    }

    @Test
    fun t04_aChangedHostKeyIsBlockedUntilTrustedAgain() = logged("t04") {
        // מפתח אחר מזה של השרת — כאילו מישהו מתחזה אליו (או שהשרת הותקן מחדש).
        val other = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8g"
        save(withPassword().copy(hostKey = other))
        launch(base)
        compose.waitFor(hasText("המפתח של השרת השתנה — ערוך את החיבור כדי לאשר אותו מחדש"), 30_000)
        Qa.screenshot(compose, "128-sftp-host-key-changed")

        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("Pi · SFTP"))
        compose.onNodeWithText("Pi · SFTP").performTouchInput { longClick() }
        compose.onNodeWithText("ערוך").performClick()
        compose.waitFor(hasText("ערוך מקום"))
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("המפתח של השרת השתנה"), 30_000)
        Qa.screenshot(compose, "129-sftp-trust-changed")
        compose.onNodeWithText("סמוך על המפתח החדש").performClick()
        compose.waitFor(hasText("release-smoke-sftp.txt"), 30_000)
        assertEquals(server.hostKey, container.settings.state.value.connections.single().hostKey)
    }
}
