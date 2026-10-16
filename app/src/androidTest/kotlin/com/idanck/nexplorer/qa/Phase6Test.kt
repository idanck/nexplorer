package com.idanck.nexplorer.qa

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.idanck.nexplorer.data.net.CloudAuths
import com.idanck.nexplorer.data.net.FixedToken
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * שלב 6: Google Drive ו-OneDrive, מול שרת מדומה על המארח (‏.github/qa/cloud-server.py, ‏10.0.2.2:8090). אין
 * באמולטור חשבון Google או Microsoft — החיבורים נשמרים ישירות, והאסימון של הבדיקה ([CloudAuths.override])
 * במקום ההתחברות. מסך "הוסף מקום" נבדק עד כפתור ההתחברות.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase6Test {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container

    private val drive = NetConnection("qa0000000061", NetProtocol.GoogleDrive, "", SERVER, PORT, "", "alex@example.com", plainHttp = true)
    private val oneDrive = NetConnection("qa0000000062", NetProtocol.OneDrive, "", SERVER, PORT, "", "alex@example.com", plainHttp = true)

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        control("/qa/reset")
        CloudAuths.override = { FixedToken("qa-token") }
        runBlocking {
            container.settings.update { it.copy(connections = emptyList(), favorites = emptyList()) }
            container.remote.forget(drive.id)
            container.remote.forget(oneDrive.id)
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        CloudAuths.override = null
        runBlocking { container.settings.update { it.copy(connections = emptyList()) } }
    }

    /** בקרה של השרת המדומה (חזרה לנתוני ההתחלה). */
    private fun control(path: String) {
        val connection = URL("http://$SERVER:$PORT$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.close()
        assertEquals("cloud-server $path", 204, connection.responseCode)
        connection.disconnect()
    }

    private fun save(connection: NetConnection) = runBlocking {
        container.settings.update { it.copy(connections = listOf(connection)) }
        container.settings.state.first { connection in it.connections }
        container.remote.forget(connection.id)
    }

    private fun remoteStat(path: String) = container.remote.fsFor(path).stat(path)

    private fun remoteBytes(path: String) = container.remote.fsFor(path).openRead(path).use { it.readBytes() }

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun logged(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e("Phase6Test", "$name failed", t)
            Qa.logTexts(compose, "Phase6Test")
            runCatching { Qa.shell("screencap -p /sdcard/Download/nexplorer-qa/failed-6-$name.png") }
            throw t
        }
    }

    @Test
    fun t01_addPlaceOffersTheCloudServices() = logged("t01") {
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("הוסף מקום"))
        compose.onNodeWithText("הוסף מקום").performClick()
        compose.waitFor(hasText("פרטי חיבור"))
        compose.onNodeWithText("ענן").performClick()
        compose.waitFor(hasText("חשבון Google"))
        compose.waitFor(hasText("חשבון Microsoft אישי"))
        // בענן אין שרתים ברשת ואין שדות שרת.
        compose.waitGone(hasText("נמצאו ברשת"))
        button("התחבר").assertExists()
        Qa.screenshot(compose, "140-add-cloud-google")
        compose.onNodeWithText("OneDrive").performClick()
        if (CloudAuths.microsoftClientId.isBlank()) compose.waitFor(hasText("OneDrive עוד לא מוגדר בגרסה הזו", substring = true))
        Qa.screenshot(compose, "141-add-cloud-onedrive")
    }

    @Test
    fun t02_googleDriveBrowseOpenAndTransfer() = logged("t02") {
        save(drive)
        transfers(drive, "142-drive")
        // Google Docs עם סיומת, שני קבצים באותו שם, / בשם, ו-Forms מוסתר.
        val base = NetPaths.child(drive.root, "nexplorer-qa")
        assertEquals("exported Report", String(remoteBytes("$base/Report.docx")))
        assertEquals("second\n", String(remoteBytes("$base/dup (1).txt")))
        assertEquals("slash\n", String(remoteBytes("$base/a∕b.txt")))
        assertNull(remoteStat("$base/Survey"))
    }

    @Test
    fun t03_oneDriveBrowseOpenAndTransfer() = logged("t03") {
        save(oneDrive)
        transfers(oneDrive, "146-onedrive")
        val base = NetPaths.child(oneDrive.root, "nexplorer-qa")
        assertEquals("encoded\n", String(remoteBytes("$base/# 100% עברית.txt")))
    }

    @Test
    fun t04_anExpiredSignInAsksToSignInAgain() = logged("t04") {
        CloudAuths.override = { FixedToken("qa-expired") }
        save(drive)
        scenario = Qa.launch(drive.root)
        compose.waitFor(hasText("ההתחברות ל-Google Drive פגה — ערוך את החיבור והתחבר מחדש"), 30_000)
        Qa.screenshot(compose, "150-drive-sign-in-again")
    }

    /** אותו סבב בשני השירותים: דפדוף, תמונה, העתקה לשני הכיוונים (גם בגושים), העברה בשירות, יצירה, שינוי שם ומחיקה. */
    private fun transfers(connection: NetConnection, shot: String) {
        val base = NetPaths.child(connection.root, "nexplorer-qa")
        scenario = Qa.launch(base)
        compose.waitFor(hasText("notes.txt"), 30_000)
        compose.waitFor(hasText("Photos"))
        Qa.screenshot(compose, "$shot-list")
        compose.onNodeWithText("photo.png").performClick()
        compose.waitFor(hasText("1 / 1"), 30_000)
        Qa.screenshot(compose, "$shot-image")
        Qa.device.pressBack()
        compose.waitFor(hasText("notes.txt"))

        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("${connection.label} · ${connection.protocol.label}"))
        Qa.screenshot(compose, "$shot-drawer")
        Qa.device.pressBack()

        // מהמכשיר: תיקייה, וקובץ של 9MB — כמה גושים בשני השירותים (8MB ב-Drive, 5MB ב-OneDrive).
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "Documents").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/Documents/report.pdf")?.size == 48 * 1024L }
        val big = File(root, "cloud.bin").apply { writeBytes(Random(6).nextBytes(9 * 1024 * 1024 + 123)) }
        container.transfers.enqueue(TransferKind.Copy, listOf(big.absolutePath), base)
        compose.waitForDisk(120_000) { remoteStat("$base/cloud.bin")?.size == big.length() }
        assertArrayEquals(big.readBytes(), remoteBytes("$base/cloud.bin"))

        // אל המכשיר: העברה — המקור נמחק מהשירות רק אחרי שהעותק נשמר.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/notes.txt"), File(root, "Empty").absolutePath)
        compose.waitForDisk(60_000) { File(root, "Empty/notes.txt").isFile && remoteStat("$base/notes.txt") == null }

        // בתוך השירות: העברה בלי להעביר בתים.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/Documents"), "$base/Photos")
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents/notes.txt") != null && remoteStat("$base/Documents") == null }

        compose.waitFor(hasText("cloud.bin"), 30_000)
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

        container.transfers.enqueue(TransferKind.Delete, listOf("$base/Photos", "$base/Renamed", "$base/cloud.bin"), null)
        compose.waitForDisk(60_000) { remoteStat("$base/Photos") == null && remoteStat("$base/Renamed") == null && remoteStat("$base/cloud.bin") == null }
        compose.waitGone(hasText("Renamed"), 15_000)
        Qa.screenshot(compose, "$shot-after-transfers")
    }

    private companion object {
        const val SERVER = "10.0.2.2"
        const val PORT = 8090
    }
}
