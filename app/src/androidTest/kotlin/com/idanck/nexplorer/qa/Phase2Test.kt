package com.idanck.nexplorer.qa

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** שלב 2: מנתח אחסון, ZIP (עם סיסמה ובלי), עורך טקסט ושינוי שם מרובה. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase2Test {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        // כפילות ודאית למנתח: אותו תוכן, מעל סף 64 KB.
        File(root, "Music/song.mp3").copyTo(File(root, "Music/song-copy.mp3"))
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

    private fun itemMenu(name: String) {
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        clickDisplayed(hasContentDescription("עוד"))
        compose.waitFor(hasText("הוסף לארכיון"))
    }

    @Test
    fun t01_analyzer() {
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תצוגה"))
        // גם במגירה (שמורכבת גם כשהיא סגורה) יש "מנתח אחסון" — רק זה שבתפריט.
        clickDisplayed(hasText("מנתח אחסון"))
        compose.waitFor(hasText("מה אפשר לפנות"), 60_000)
        Qa.screenshot(compose, "50-analyzer")
        // הפתיחה אסינכרונית (בדיקה שהקבצים עוד קיימים), ושמות הקבצים מופיעים גם בסקירה —
        // מחכים לשורת "נבחר" שקיימת רק ברשימה, אחרת "חזרה" יוצאת מהמנתח מוקדם מדי.
        compose.onNodeWithText("כפילויות").performClick()
        compose.waitFor(hasText("נבחר", substring = true))
        compose.waitFor(hasText("song-copy.mp3", substring = true))
        Qa.screenshot(compose, "51-analyzer-duplicates")
        Qa.device.pressBack()
        compose.waitFor(hasText("קבצים גדולים"))
        compose.onNodeWithText("קבצים גדולים").performClick()
        compose.waitFor(hasText("נבחר", substring = true))
        compose.waitFor(hasText("big.bin", substring = true))
        Qa.screenshot(compose, "52-analyzer-largest")
        Qa.device.pressBack()
        Qa.device.pressBack()
        compose.waitFor(hasText("readme.md"))
    }

    @Test
    fun t02_zipFolderThenExtractIntoFolder() {
        itemMenu("Documents")
        Qa.screenshot(compose, "53-item-menu-phase2")
        compose.onNodeWithText("הוסף לארכיון").performClick()
        compose.waitFor(hasText("שם הקובץ"))
        Qa.screenshot(compose, "54-compress-dialog")
        compose.onNodeWithText("צור").performClick()
        compose.waitForDisk(30_000) { File(root, "Documents.zip").length() > 0 }
        compose.waitFor(hasText("Documents.zip"))

        compose.onNodeWithText("Documents.zip").performClick()
        compose.waitFor(hasText("אחרי חילוץ", substring = true), 15_000)
        Qa.screenshot(compose, "55-extract-dialog")
        compose.onNodeWithText("חלץ לתיקייה", substring = true).performClick()
        compose.waitForDisk(30_000) { File(root, "Documents (1)/Documents/notes.txt").isFile }
        assertTrue(File(root, "Documents (1)/Documents/דוח שנתי 2026.txt").readText().startsWith("שלום"))
    }

    @Test
    fun t03_zipWithPasswordThenExtractHere() {
        itemMenu("readme.md")
        compose.onNodeWithText("הוסף לארכיון").performClick()
        compose.waitFor(hasText("שם הקובץ"))
        // השדה השני הוא הסיסמה; אחרי שמקלידים בה נפתח שדה האימות.
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("qa-pass")
        compose.waitFor(hasText("הסיסמה שוב"))
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("qa-pass")
        compose.onNodeWithText("צור").performClick()
        compose.waitForDisk(30_000) { File(root, "readme.zip").length() > 0 }
        compose.waitFor(hasText("readme.zip"))

        compose.onNodeWithText("readme.zip").performClick()
        compose.waitFor(hasText("מוגן בסיסמה", substring = true), 15_000)
        compose.onNode(hasSetTextAction()).performTextInput("qa-pass")
        Qa.screenshot(compose, "56-extract-password")
        compose.onNodeWithText("חלץ לכאן").performClick()
        // readme.md כבר קיים — החילוץ לא דורס, אלא נותן שם פנוי.
        compose.waitForDisk(30_000) { File(root, "readme (1).md").isFile }
        assertTrue(File(root, "readme.md").readText().startsWith("# nexplorer QA"))
    }

    @Test
    fun t04_editTextAndSave() {
        itemMenu("readme.md")
        compose.onNodeWithText("ערוך כטקסט").performClick()
        compose.waitFor(hasSetTextAction(), 15_000)
        compose.onNode(hasSetTextAction()).performTextInput("edited on the emulator\n")
        compose.waitFor(hasText("יש שינויים שלא נשמרו"))
        Qa.screenshot(compose, "57-editor")
        compose.onNodeWithText("שמור").performClick()
        compose.waitFor(hasText("נשמר"))
        assertTrue(File(root, "readme.md").readText().contains("edited on the emulator"))
        Qa.device.pressBack()
        compose.waitFor(hasText("readme.md"))
    }

    @Test
    fun t05_batchRename() {
        compose.onNodeWithText("archive.zip").performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        compose.onNodeWithText("big.bin").performClick()
        compose.onNodeWithText("readme.md").performClick()
        compose.onNodeWithContentDescription("שנה שם").performClick()
        compose.waitFor(hasText("שינוי שם", substring = true))
        // חפש, החלף, בהתחלה, בסוף — "בהתחלה" הוא השלישי.
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("QA-{n}-")
        compose.waitFor(hasText("שנה 3"))
        Qa.screenshot(compose, "58-batch-rename")
        compose.onNodeWithText("שנה 3").performClick()
        // המספור לפי סדר התצוגה (ברירת המחדל: תאריך) — הבדיקה לא תלויה בו.
        val expected = Regex("""QA-[1-3]-(archive\.zip|big\.bin|readme\.md)""")
        compose.waitForDisk {
            root.list().orEmpty().count { expected.matches(it) } == 3
        }
        val renamed = root.list().orEmpty().filter { expected.matches(it) }
        assertTrue("כל מספר פעם אחת", renamed.map { it.substring(3, 4) }.toSet() == setOf("1", "2", "3"))
        assertTrue("המקורות לא קיימים", listOf("archive.zip", "big.bin", "readme.md").none { File(root, it).exists() })
        compose.waitFor(hasText(renamed.first()))
        Qa.screenshot(compose, "59-after-batch-rename")
    }
}
