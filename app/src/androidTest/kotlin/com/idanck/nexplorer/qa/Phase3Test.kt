package com.idanck.nexplorer.qa

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.regex.Pattern

/** שלב 3א: 7z עם סיסמה, חילוץ TAR.GZ, וגיליון ה-APK עד מתקין המערכת. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase3Test {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        tarGz(File(root, "logs.tar.gz"))
        // ה-APK של האפליקציה עצמה: מותקן, באותה גרסה ובאותה חתימה.
        File(ApplicationProvider.getApplicationContext<Context>().packageCodePath).copyTo(File(root, "nexplorer-self.apk"))
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

    @Test
    fun t01_sevenZipWithPasswordRoundTrip() {
        compose.onNodeWithText("Documents").performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        clickDisplayed(hasContentDescription("עוד"))
        compose.waitFor(hasText("הוסף לארכיון"))
        compose.onNodeWithText("הוסף לארכיון").performClick()
        compose.waitFor(hasText("שם הקובץ"))
        compose.onNodeWithText("7z").performClick()
        compose.waitFor(hasText("Documents.7z"))
        // שדה 0 השם, 1 הסיסמה; אחרי שמקלידים בה נפתח שדה האימות.
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("qa-7z")
        compose.waitFor(hasText("הסיסמה שוב"))
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("qa-7z")
        Qa.screenshot(compose, "70-compress-7z")
        compose.onNodeWithText("צור").performClick()
        compose.waitForDisk(60_000) { File(root, "Documents.7z").length() > 0 }

        compose.waitFor(hasText("Documents.7z"))
        compose.onNodeWithText("Documents.7z").performClick()
        compose.waitFor(hasText("מוגן בסיסמה", substring = true), 15_000)
        compose.onNode(hasSetTextAction()).performTextInput("qa-7z")
        Qa.screenshot(compose, "71-extract-7z")
        compose.onNodeWithText("חלץ לתיקייה", substring = true).performClick()
        val extracted = File(root, "Documents (1)/Documents")
        // החילוץ רץ רשומה אחרי רשומה: מחכים לכל השלוש, כל אחת בגודל המלא שלה.
        compose.waitForDisk(60_000) {
            File(extracted, "notes.txt").isFile && File(extracted, "דוח שנתי 2026.txt").isFile &&
                File(extracted, "report.pdf").length() == 48 * 1024L
        }
        assertTrue(File(extracted, "דוח שנתי 2026.txt").readText().startsWith("שלום"))
    }

    @Test
    fun t02_tarGzExtractHere() {
        compose.waitFor(hasText("logs.tar.gz"))
        compose.onNodeWithText("logs.tar.gz").performClick()
        compose.waitFor(hasText("TAR.GZ", substring = true), 15_000)
        Qa.screenshot(compose, "72-extract-targz")
        compose.onNodeWithText("חלץ לכאן").performClick()
        compose.waitForDisk(30_000) { File(root, "logs/app.log").isFile && File(root, "logs/old/2025.log").isFile }
        assertEquals("first line\n", File(root, "logs/app.log").readText())
    }

    @Test
    fun t03_apkSheetLeadsToTheSystemInstaller() {
        compose.waitFor(hasText("nexplorer-self.apk"))
        compose.onNodeWithText("nexplorer-self.apk").performClick()
        compose.waitFor(hasText("com.idanck.nexplorer.debug"), 20_000)
        compose.waitFor(hasText("אותה גרסה מותקנת"))
        assertTrue(compose.onAllNodesWithText("פתח").fetchSemanticsNodes().isNotEmpty())
        Qa.screenshot(compose, "73-apk-sheet")

        compose.onNodeWithText("התקן מחדש").performClick()
        // המתקין של המערכת (או בקשת ההרשאה שלו להתקין ממקור לא ידוע) — כל מה שבחבילה שלו.
        val installer = Qa.device.wait(Until.hasObject(By.pkg(Pattern.compile(".*packageinstaller.*"))), 15_000)
        Qa.screenshot(compose, "74-apk-installer")
        assertTrue("מתקין המערכת לא נפתח", installer)
        Qa.device.pressBack()
        // בחזרה הגיליון קורא את ה-APK מחדש — עדיין אותה גרסה.
        compose.waitFor(hasText("אותה גרסה מותקנת"), 15_000)
    }

    /** כמו `tar czf`: תיקייה, שני קבצים (אחד בתת-תיקייה). */
    private fun tarGz(file: File) {
        TarArchiveOutputStream(GzipCompressorOutputStream(file.outputStream())).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("logs/"))
            tar.closeArchiveEntry()
            for ((name, text) in listOf("logs/app.log" to "first line\n", "logs/old/2025.log" to "older\n")) {
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }
}
