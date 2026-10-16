package com.idanck.nexplorer.qa

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.crypto.KeyRing
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** שלב 4ב: "הצפן" לתיקייה, "פתח" קובץ מוצפן (סיסמה שגויה ואז נכונה), ו"פענח" — מהבחירה ומההקשה. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase4bTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as NexplorerApp).container
    private val transfers get() = container.transfers
    private val photos get() = File(root, "Photos")
    private val password = "סיסמה qa"

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        // Phase4Test משאיר מועדפים: "Photos" במגירה (שמורכבת גם כשהיא סגורה) היה צומת שני עם אותו טקסט.
        runBlocking { container.settings.update { it.copy(favorites = emptyList(), hiddenFolders = emptySet()) } }
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() = scenario.close()

    private fun clickDisplayed(matcher: SemanticsMatcher) {
        val nodes = compose.onAllNodes(matcher)
        val count = nodes.fetchSemanticsNodes().size
        for (i in 0 until count) {
            if (runCatching { nodes[i].assertIsDisplayed() }.isSuccess) {
                nodes[i].performClick()
                return
            }
        }
        throw AssertionError("אין צומת מוצג שמתאים ל-$matcher")
    }

    private fun itemMenu(name: String, item: String) {
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        clickDisplayed(hasContentDescription("עוד"))
        compose.waitFor(hasText(item))
    }

    /** כפתור בחלון — כשגם הכותרת אומרת את אותה מילה. */
    private fun button(text: String) = compose.onNode(hasText(text) and hasClickAction())

    private fun encryptInBackground(file: File) {
        transfers.enqueue(TransferKind.Encrypt, listOf(file.absolutePath), null, password = password.toCharArray())
    }

    @Test
    fun t00_keyDerivationTime() {
        // PBKDF2 עם 600,000 חזרות — כמה זמן זה לוקח במכשיר (נאסף עם הצילומים).
        val started = System.nanoTime()
        KeyRing("x".toCharArray()).use { it.prepareSealing() }
        val ms = (System.nanoTime() - started) / 1_000_000
        File(Qa.shotsDir, "pbkdf2-api${Build.VERSION.SDK_INT}.txt").writeText("$ms ms\n")
    }

    @Test
    fun t01_encryptFolderThenOpenOneFile() {
        val original = File(photos, "shot-0.png").readBytes()
        itemMenu("Photos", "הצפן")
        Qa.screenshot(compose, "95-item-menu-encrypt")
        compose.onNodeWithText("הצפן").performClick()
        compose.waitFor(hasText("הסיסמה שוב"))
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(password)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(password)
        Qa.screenshot(compose, "96-encrypt-dialog")
        button("הצפן").performClick()

        // כל ארבעת הקבצים הוצפנו, והמקורות נמחקו רק אחרי זה.
        compose.waitForDisk(60_000) { (0..3).all { !File(photos, "shot-$it.png").exists() && File(photos, "shot-$it.png.enc").isFile } }
        assertTrue((0..3).all { EncryptedFiles.isEncrypted(File(photos, "shot-$it.png.enc")) })
        assertFalse(photos.walk().any { it.name.endsWith(".nexplorer-part") })

        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot-0.png.enc"))
        Qa.screenshot(compose, "97-encrypted-files")

        compose.onNodeWithText("shot-0.png.enc").performClick()
        compose.waitFor(hasText("קובץ מוצפן", substring = true))
        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        button("פתח").performClick()
        compose.waitFor(hasText("סיסמה שגויה"), 30_000)
        Qa.screenshot(compose, "98-wrong-password")

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(password)
        button("פתח").performClick()
        // המציג המובנה, עם השם המקורי — ולבד ("1 / 1"): הוא בתיקייה הזמנית. הקובץ המוצפן לא השתנה.
        compose.waitFor(hasText("shot-0.png"), 30_000)
        compose.waitFor(hasText("1 / 1"))
        Qa.screenshot(compose, "99-encrypted-opened")
        assertTrue(File(photos, "shot-0.png.enc").isFile)
        assertFalse(File(photos, "shot-0.png").exists())
        val preview = File(context.cacheDir, "decrypted").walk().first { it.name == "shot-0.png" }
        assertArrayEquals(original, preview.readBytes())
        Qa.device.pressBack()
        compose.waitFor(hasText("shot-1.png.enc"))
    }

    @Test
    fun t02_decryptFolderFromSelection() {
        val originals = (0..3).associateWith { File(photos, "shot-$it.png").readBytes() }
        encryptInBackground(photos)
        compose.waitForDisk(60_000) { (0..3).all { File(photos, "shot-$it.png.enc").isFile && !File(photos, "shot-$it.png").exists() } }

        // סיסמה שגויה: הפעולה נכשלת עם הסיבה, ושום קובץ לא משתנה.
        itemMenu("Photos", "פענח")
        compose.onNodeWithText("פענח").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        button("פענח").performClick()
        compose.waitFor(hasText("סיסמה שגויה", substring = true), 30_000)
        Qa.screenshot(compose, "100-decrypt-wrong-password")
        assertTrue((0..3).all { File(photos, "shot-$it.png.enc").isFile })
        // ההודעה יושבת מעל סרגל הבחירה — הקשה על ⋮ לפני שהיא נעלמת נוחתת עליה.
        compose.waitGone(hasText("סיסמה שגויה", substring = true), 15_000)

        itemMenu("Photos", "פענח")
        compose.onNodeWithText("פענח").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput(password)
        button("פענח").performClick()
        compose.waitForDisk(60_000) { (0..3).all { File(photos, "shot-$it.png").isFile && !File(photos, "shot-$it.png.enc").exists() } }
        originals.forEach { (i, bytes) -> assertArrayEquals(bytes, File(photos, "shot-$i.png").readBytes()) }
    }

    @Test
    fun t03_decryptOneFileFromItsDialog() {
        val readme = File(root, "readme.md")
        val text = readme.readText()
        encryptInBackground(readme)
        compose.waitForDisk(60_000) { File(root, "readme.md.enc").isFile && !readme.exists() }
        compose.waitFor(hasText("readme.md.enc"), 15_000)

        compose.onNodeWithText("readme.md.enc").performClick()
        compose.waitFor(hasText("קובץ מוצפן", substring = true))
        compose.onNode(hasSetTextAction()).performTextInput(password)
        Qa.screenshot(compose, "101-encrypted-file-dialog")
        button("פענח").performClick()
        compose.waitForDisk(60_000) { readme.isFile && !File(root, "readme.md.enc").exists() }
        assertEquals(text, readme.readText())
        compose.waitFor(hasText("readme.md"), 15_000)
    }
}
