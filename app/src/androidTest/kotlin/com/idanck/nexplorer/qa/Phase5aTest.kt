package com.idanck.nexplorer.qa

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
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
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.net.RemoteFs
import com.idanck.nexplorer.data.net.SmbFs
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * שלב 5א: מקום ברשת ב-SMB, מול Samba על המארח (‏.github/qa/smb-server.sh): האמולטור מגיע אליו
 * ב-10.0.2.2. משתמש qa, שיתופים qa ו-second. כל בדיקה מתחילה מ-`qa/nexplorer-qa` טרי.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase5aTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val container get() = (ApplicationProvider.getApplicationContext<Context>() as NexplorerApp).container

    private val password = "qa-pass"
    private val server = NetConnection("qa0000000001", NetProtocol.Smb, "QA", "10.0.2.2", null, "", "qa")
    private val share = NetPaths.child(NetPaths.root(NetProtocol.Smb, server.id), "qa")
    private val base = NetPaths.child(share, "nexplorer-qa")

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        runBlocking { container.settings.update { it.copy(connections = emptyList(), favorites = emptyList()) } }
        runBlocking { container.remote.forget(server.id) }
        SmbFs(server, password.toCharArray()).use { fs ->
            wipe(fs, base)
            fs.mkdir(base)
            fs.openWrite("$base/readme.txt").use { it.write("hello from samba\n".toByteArray()) }
            fs.mkdir("$base/Photos")
            fs.openWrite("$base/Photos/shot.png").use { out -> File(root, "Photos/shot-1.png").inputStream().use { it.copyTo(out) } }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        runCatching { SmbFs(server, password.toCharArray()).use { wipe(it, base) } }
    }

    private fun wipe(fs: RemoteFs, path: String) {
        val entry = fs.stat(path) ?: return
        if (entry.isDirectory) fs.list(path).forEach { wipe(fs, it.path) }
        fs.delete(path, entry.isDirectory)
    }

    /** החיבור שמור כמו אחרי "בדוק והתחבר" — הסיסמה מוצפנת ב-Keystore. */
    private fun saveConnection() = runBlocking {
        val saved = server.copy(secret = ConnectionSecrets.encrypt(password.toCharArray()))
        container.settings.update { it.copy(connections = listOf(saved)) }
        container.settings.state.first { saved in it.connections }
    }

    private fun remoteStat(path: String) = container.remote.fsFor(path).stat(path)

    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun launch(path: String) {
        scenario = Qa.launch(path)
    }

    /**
     * כשל — ללוג ולצילום, לפני ש-tearDown סוגר את המסך. שגיאה בתוך Compose נתפסת ע"י מסגרת הבדיקות
     * ונזרקת כאן; אם הקומפוזיציה נפגמה, הסגירה מפילה את התהליך — והסיבה המקורית לא הייתה נשמרת.
     */
    private fun logged(name: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Log.e("Phase5aTest", "$name failed", t)
            Qa.logTexts(compose, "Phase5aTest")
            runCatching { Qa.shell("screencap -p /sdcard/Download/nexplorer-qa/failed-$name.png") }
            throw t
        }
    }

    @Test
    fun t01_addPlaceFromTheDrawerConnectsAndListsShares() = logged("t01") {
        launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("הוסף מקום"))
        compose.onNodeWithText("הוסף מקום").performClick()
        compose.waitFor(hasText("פרטי חיבור"))
        Qa.screenshot(compose, "107-add-place")

        compose.onNodeWithContentDescription("שרת").performTextInput("10.0.2.2")
        compose.onNodeWithContentDescription("משתמש").performTextInput("qa")
        compose.onNodeWithContentDescription("סיסמה").performTextInput("wrong")
        button("בדוק והתחבר").performClick()
        compose.waitFor(hasText("שם המשתמש או הסיסמה שגויים"), 30_000)
        Qa.screenshot(compose, "108-add-place-wrong-password")
        assertTrue("חיבור שנכשל לא נשמר", container.settings.state.value.connections.isEmpty())

        compose.onNodeWithContentDescription("סיסמה").performTextClearance()
        compose.onNodeWithContentDescription("סיסמה").performTextInput(password)
        button("בדוק והתחבר").performClick()
        // בלי שיתוף: רשימת השיתופים בשרת.
        compose.waitFor(hasText("second"), 30_000)
        Qa.screenshot(compose, "109-smb-shares")
        // לשיתוף אין תאריך: "שיתוף" בשורת הפרטים, ולא "1 בינו׳ 1970" או מדור "ינואר 1970".
        assertEquals(2, compose.onAllNodesWithText("שיתוף").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText("1970", substring = true).fetchSemanticsNodes().isEmpty())
        val saved = container.settings.state.value.connections.single()
        assertEquals("10.0.2.2", saved.host)
        assertNull(saved.port)
        assertEquals(password, String(ConnectionSecrets.decrypt(requireNotNull(saved.secret))))
        assertFalse("הסיסמה לא נשמרת כמו שהיא", saved.secret!!.contains(password))

        compose.onNodeWithText("qa").performClick()
        compose.waitFor(hasText("nexplorer-qa"), 30_000)
        compose.onNodeWithText("nexplorer-qa").performClick()
        compose.waitFor(hasText("readme.txt"), 30_000)
        Qa.screenshot(compose, "110-smb-folder")

        // במגירה: החיבור, עם השרת או עם פס התפוסה של השיתוף.
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("10.0.2.2 · SMB"))
        Qa.screenshot(compose, "111-drawer-with-smb")
    }

    @Test
    fun t02_openFromTheServerAndCopyBothWays() = logged("t02") {
        saveConnection()
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot.png"), 30_000)
        compose.onNodeWithText("shot.png").performClick()
        // עותק זמני במכשיר, ואז מציג התמונות.
        compose.waitFor(hasText("1 / 1"), 30_000)
        Qa.screenshot(compose, "112-smb-image-opened")
        Qa.device.pressBack()
        compose.waitFor(hasText("shot.png"))

        // מהמכשיר לשרת — תיקייה שלמה.
        container.transfers.enqueue(TransferKind.Copy, listOf(File(root, "Documents").absolutePath), base)
        compose.waitForDisk(60_000) { remoteStat("$base/Documents/report.pdf")?.size == 48 * 1024L }
        assertEquals(
            "hello from the emulator\n",
            container.remote.fsFor(base).openRead("$base/Documents/notes.txt").use { it.readBytes() }.toString(Charsets.UTF_8),
        )
        assertNotNull(remoteStat("$base/Documents/דוח שנתי 2026.txt"))
        assertTrue("בהעתקה המקור נשאר", File(root, "Documents/notes.txt").isFile)

        // מהשרת למכשיר בהעברה: הקובץ נמחק מהשרת אחרי שנשמר.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/readme.txt"), File(root, "Empty").absolutePath)
        compose.waitForDisk(60_000) { File(root, "Empty/readme.txt").isFile && remoteStat("$base/readme.txt") == null }
        assertEquals("hello from samba\n", File(root, "Empty/readme.txt").readText())

        // בתוך אותו שיתוף: שינוי שם בשרת.
        container.transfers.enqueue(TransferKind.Move, listOf("$base/Documents"), "$base/Photos")
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents/notes.txt") != null && remoteStat("$base/Documents") == null }

        container.transfers.enqueue(TransferKind.Delete, listOf("$base/Photos/Documents"), null)
        compose.waitForDisk(60_000) { remoteStat("$base/Photos/Documents") == null }
        compose.waitGone(hasText("Documents"), 15_000)
        Qa.screenshot(compose, "113-smb-after-transfers")
    }

    @Test
    fun t03_pasteCreateRenameAndDeleteOnTheServer() = logged("t03") {
        saveConnection()
        launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)

        // העתק במכשיר, הדבק בשרת — דרך המגירה.
        compose.onNodeWithText("big.bin").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("העתק").performClick()
        compose.waitFor(hasText("הדבק כאן (1)"))
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("QA · SMB"))
        compose.onNodeWithText("QA · SMB").performClick()
        compose.waitFor(hasText("qa"), 30_000)
        // ברשימת השיתופים אין לאן להדביק.
        compose.waitFor(hasText("פתח שיתוף כדי להדביק"))
        compose.onNodeWithText("qa").performClick()
        compose.waitFor(hasText("nexplorer-qa"), 30_000)
        compose.onNodeWithText("nexplorer-qa").performClick()
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithText("הדבק כאן (1)").performClick()
        compose.waitForDisk(60_000) { remoteStat("$base/big.bin")?.size == 3 * 1024 * 1024L }
        compose.waitFor(hasText("big.bin"), 15_000)

        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("תיקייה חדשה"))
        Qa.screenshot(compose, "114-smb-new-menu")
        compose.onNodeWithText("תיקייה חדשה").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("Created")
        compose.onNodeWithText("צור").performClick()
        compose.waitFor(hasText("Created"), 30_000)
        assertTrue(remoteStat("$base/Created")!!.isDirectory)

        compose.onNodeWithText("Created").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("שנה שם").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        compose.onNodeWithText("שנה").performClick()
        compose.waitFor(hasText("Renamed"), 30_000)
        assertNull(remoteStat("$base/Created"))

        compose.onNodeWithText("Renamed").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("מחק").performClick()
        compose.waitFor(hasText("למחוק את הפריט?"))
        compose.onNodeWithText("מחק").performClick()
        compose.waitForDisk(60_000) { remoteStat("$base/Renamed") == null }
        compose.waitGone(hasText("Renamed"), 15_000)
        Qa.screenshot(compose, "115-smb-after-ui-ops")
    }

    @Test
    fun t04_removeFromTheDrawerForgetsItsFavorites() = logged("t04") {
        saveConnection()
        runBlocking { container.settings.update { it.copy(favorites = listOf(base, root.absolutePath)) } }
        launch(base)
        compose.waitFor(hasText("readme.txt"), 30_000)
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        compose.waitFor(hasText("QA · SMB"))
        compose.onNodeWithText("QA · SMB").performTouchInput { longClick() }
        compose.waitFor(hasText("הסר מהמגירה"))
        Qa.screenshot(compose, "116-drawer-connection-menu")
        compose.onNodeWithText("הסר מהמגירה").performClick()
        compose.waitForDisk(15_000) { container.settings.state.value.connections.isEmpty() }
        assertEquals(listOf(root.absolutePath), container.settings.state.value.favorites)
        // היינו בתוך החיבור — חוזרים למכשיר.
        compose.waitGone(hasText("readme.txt"), 15_000)
    }

    @Test
    fun t05_videoPlaysStraightFromTheServer() = logged("t05") {
        val clip = Qa.asset("clip.mp4")
        SmbFs(server, password.toCharArray()).use { fs ->
            fs.mkdir("$base/Videos")
            fs.openWrite("$base/Videos/clip.mp4").use { it.write(clip) }
            // דילוג: קריאה מאמצע הקובץ, בלי לקרוא את מה שלפניו.
            Qa.assertReadsFromOffsets(fs, "$base/Videos/clip.mp4", clip)
        }
        saveConnection()
        launch(base)
        compose.waitFor(hasText("Videos"), 30_000)
        compose.onNodeWithText("Videos").performClick()
        compose.waitFor(hasText("clip.mp4"), 30_000)
        compose.onNodeWithText("clip.mp4").performClick()
        // ישר לנגן, בלי חלון ההורדה: מנגן מהשרת, ואין עותק במכשיר.
        compose.waitFor(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "מנגן"), 30_000)
        Qa.screenshot(compose, "138-smb-video-streaming")
        val copies = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, RemoteFiles.CACHE_DIR).walk().filter { it.name == "clip.mp4" }.toList()
        assertTrue("הסרטון הורד למכשיר: $copies", copies.isEmpty())
        Qa.device.pressBack()
        compose.waitFor(hasText("clip.mp4"), 10_000)
    }
}
