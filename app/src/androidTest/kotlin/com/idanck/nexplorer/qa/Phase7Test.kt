package com.idanck.nexplorer.qa

import android.content.Context
import android.util.Log
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
import com.idanck.nexplorer.data.net.ConnectionSecrets
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
import org.junit.Assert.assertNotNull
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
 * שלב 7א: Synology ברשת הביתית — מול שרת מדומה של DSM על המארח (‏.github/qa/syno-server.py, ‏10.0.2.2:8091, ב-http).
 * המשתמש alex / qa-pass. השיתופים בשורש, תמונות ממוזערות מה-NAS, פתיחה ישירה, העברות לשני הכיוונים ובתוך ה-NAS,
 * יצירה, שינוי שם ומחיקה. מסך "הוסף מקום" — האריח והשדות.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase7Test {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container

    private val nas get() = NetConnection("qa0000000071", NetProtocol.Synology, "DiskStation", SERVER, PORT, "", "alex", plainHttp = true, secret = ConnectionSecrets.encrypt("qa-pass".toCharArray()))

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        control("/qa/reset")
        runBlocking {
            container.settings.update { it.copy(connections = emptyList(), favorites = emptyList()) }
            container.remote.forget("qa0000000071")
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        runBlocking { container.settings.update { it.copy(connections = emptyList()) } }
    }

    private fun control(path: String) {
        val connection = URL("http://$SERVER:$PORT$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.close()
        assertEquals("syno-server $path", 204, connection.responseCode)
        connection.disconnect()
    }

    private fun save(connection: NetConnection) = runBlocking {
        container.settings.update { it.copy(connections = listOf(connection)) }
        container.settings.state.first { s -> s.connections.any { it.id == connection.id } }
        container.remote.forget(connection.id)
    }

    private fun remoteStat(path: String) = container.remote.fsFor(path).stat(path)

    private fun remoteBytes(path: String) = container.remote.fsFor(path).openRead(path).use { it.readBytes() }

    private fun setViewMode(mode: String) {
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תצוגה"))
        compose.onNodeWithText("אפשרויות תצוגה").performClick()
        compose.waitFor(hasText("מצב תצוגה"))
        compose.onNodeWithText(mode).performClick()
        Qa.device.pressBack()
        compose.waitGone(hasText("מצב תצוגה"))
    }

    private fun logged(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e("Phase7Test", "$name failed", t)
            Qa.logTexts(compose, "Phase7Test")
            runCatching { Qa.shell("screencap -p /sdcard/Download/nexplorer-qa/failed-7-$name.png") }
            throw t
        }
    }

    @Test
    fun t01_addPlaceOffersSynology() = logged("t01") {
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("הוסף מקום"))
        compose.onNodeWithText("הוסף מקום").performClick()
        compose.waitFor(hasText("פרטי חיבור"))
        compose.onNodeWithText("Synology").performClick()
        compose.waitFor(hasText("ה-NAS זמין ברשת הביתית", substring = true))
        compose.waitFor(hasText("שיתוף"))
        // ל-DSM אין "אורח" — בלי משתמש אי אפשר להתחבר.
        // השדה הראשון — "שרת".
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("192.168.1.20")
        compose.waitFor(hasText("צריך שם משתמש"))
        Qa.screenshot(compose, "170-add-synology")
    }

    @Test
    fun t02_browseThumbnailsAndTransfers() = logged("t02") {
        val nas = nas
        save(nas)
        scenario = Qa.launch(nas.root)
        // בשורש — השיתופים.
        compose.waitFor(hasText("nexplorer-qa"), 30_000)
        compose.waitFor(hasText("backup"))
        Qa.screenshot(compose, "171-synology-shares")
        compose.onNodeWithText("nexplorer-qa").performClick()
        compose.waitFor(hasText("notes.txt"), 30_000)
        compose.waitFor(hasText("Photos"))
        Qa.screenshot(compose, "172-synology-list")

        // תמונות ממוזערות — מה-NAS (`SYNO.FileStation.Thumb`), ברשת.
        val base = NetPaths.child(nas.root, "nexplorer-qa")
        assertNotNull(runBlocking { container.remote.thumbnail("$base/photo.png", large = true) })
        setViewMode("רשת")
        compose.waitFor(hasText("photo.png"))
        Thread.sleep(2_000)
        Qa.screenshot(compose, "173-synology-grid")
        setViewMode("רשימה")

        // מהמכשיר: תיקייה, וקובץ של 3MB; תאריך השינוי של המקור נשמר בהעלאה.
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "Documents").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/Documents/report.pdf")?.size == 48 * 1024L }
        val big = File(root, "nas.bin").apply {
            writeBytes(Random(7).nextBytes(3 * 1024 * 1024 + 77))
            setLastModified(1_650_000_000_000L)
        }
        container.transfers.enqueue(TransferKind.Copy, listOf(big.absolutePath), base)
        compose.waitForDisk(120_000) { remoteStat("$base/nas.bin")?.size == big.length() }
        assertArrayEquals(big.readBytes(), remoteBytes("$base/nas.bin"))
        assertEquals(1_650_000_000_000L, remoteStat("$base/nas.bin")?.lastModified)
        // פתיחה ישירה מה-NAS: מאמצע הקובץ (Range), וכמו אפליקציה אחרת (RemoteFilesProvider).
        Qa.assertReadsFromOffsets(container.remote.fsFor(base), "$base/nas.bin", big.readBytes())
        Qa.assertProviderReads("$base/nas.bin", big.readBytes())

        // אל המכשיר: העברה — המקור נמחק מה-NAS רק אחרי שהעותק נשמר.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/notes.txt"), File(root, "Empty").absolutePath)
        compose.waitForDisk(60_000) { File(root, "Empty/notes.txt").isFile && remoteStat("$base/notes.txt") == null }

        // בתוך ה-NAS: העברה בצד השרת, גם לשיתוף אחר.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/Documents"), "$base/Photos")
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents/report.pdf") != null && remoteStat("$base/Documents") == null }
        val backup = NetPaths.child(nas.root, "backup")
        container.transfers.enqueue(TransferKind.Move, listOf("$base/photo.png"), backup)
        compose.waitForDisk(60_000) { remoteStat("$backup/photo.png") != null && remoteStat("$base/photo.png") == null }

        compose.waitFor(hasText("nas.bin"), 30_000)
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

        container.transfers.enqueue(TransferKind.Delete, listOf("$base/Photos", "$base/Renamed", "$base/nas.bin"), null)
        compose.waitForDisk(60_000) { remoteStat("$base/Photos") == null && remoteStat("$base/Renamed") == null && remoteStat("$base/nas.bin") == null }
        compose.waitGone(hasText("Renamed"), 15_000)
        Qa.screenshot(compose, "174-synology-after-transfers")
    }

    @Test
    fun t03_aWrongPasswordSaysSo() = logged("t03") {
        save(nas.copy(secret = ConnectionSecrets.encrypt("nope".toCharArray())))
        scenario = Qa.launch(nas.root)
        compose.waitFor(hasText("שם המשתמש או הסיסמה שגויים"), 30_000)
        Qa.screenshot(compose, "175-synology-wrong-password")
    }

    private companion object {
        const val SERVER = "10.0.2.2"
        const val PORT = 8091
    }
}
