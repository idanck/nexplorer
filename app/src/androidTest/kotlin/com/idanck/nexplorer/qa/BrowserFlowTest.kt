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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.qa.Qa.exists
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * מסלולי המשתמש במסך הקבצים, על עץ קבוע (QaData) שנבנה מחדש לפני כל בדיקה.
 * כל בדיקה גם מצלמת את המסכים שהיא עוברת בהם — הצילומים נבדקים מול לוחות העיצוב.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BrowserFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() {
        Qa.device.unfreezeRotation()
        scenario.close()
    }

    /** המגירה מורכבת גם כשהיא סגורה, ולכן כמה מהתוויות קיימות פעמיים — רק זו שעל המסך. */
    private fun clickDisplayed(matcher: SemanticsMatcher) {
        val nodes = compose.onAllNodes(matcher)
        val count = nodes.fetchSemanticsNodes().size
        for (i in 0 until count) {
            val node = nodes[i]
            if (runCatching { node.assertIsDisplayed() }.isSuccess) {
                node.performClick()
                return
            }
        }
        throw AssertionError("אין צומת מוצג שמתאים ל-$matcher")
    }

    private fun longPress(name: String) {
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
    }

    private fun openOverflow() {
        compose.onNodeWithContentDescription("עוד פעולות").performClick()
        compose.waitFor(hasText("אפשרויות תצוגה"))
    }

    @Test
    fun t01_listShowsFolderContents() {
        listOf("Documents", "Photos", "Music", "Empty", "readme.md", "archive.zip", "big.bin").forEach {
            compose.waitFor(hasText(it))
        }
        assertFalse("קבצים מוסתרים כבויים כברירת מחדל", compose.exists(hasText(".hidden")))
        Qa.screenshot(compose, "10-list")
    }

    @Test
    fun t02_viewModesAndOverflow() {
        openOverflow()
        Qa.screenshot(compose, "11-overflow-menu")
        compose.onNodeWithText("אפשרויות תצוגה").performClick()
        compose.waitFor(hasText("מצב תצוגה"))
        Qa.screenshot(compose, "12-view-options")
        compose.onNodeWithText("רשת").performClick()
        Qa.device.pressBack()
        compose.waitGone(hasText("מצב תצוגה"))
        Qa.screenshot(compose, "13-grid")

        setViewMode("גלריה")
        Qa.screenshot(compose, "14-gallery")
        compose.onNodeWithText("Photos").performClick()
        // בגלריה לתמונות אין שם על המשבצת — מחכים לטעינת התמונות הממוזערות.
        Thread.sleep(1_500)
        Qa.screenshot(compose, "15-gallery-photos")
        Qa.device.pressBack()
        compose.waitFor(hasText("readme.md"))
        setViewMode("קומפקטי")
        Qa.screenshot(compose, "16-compact")
        setViewMode("רשימה")
        compose.waitFor(hasText("readme.md"))
    }

    private fun setViewMode(mode: String) {
        openOverflow()
        compose.onNodeWithText("אפשרויות תצוגה").performClick()
        compose.waitFor(hasText("מצב תצוגה"))
        compose.onNodeWithText(mode).performClick()
        Qa.device.pressBack()
        compose.waitGone(hasText("מצב תצוגה"))
    }

    @Test
    fun t03_createFolderAndFile() {
        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("קובץ חדש"))
        Qa.screenshot(compose, "17-new-menu")
        compose.onNodeWithText("תיקייה חדשה").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("QA folder")
        Qa.screenshot(compose, "18-new-folder-dialog")
        compose.onNodeWithText("צור").performClick()
        compose.waitFor(hasText("QA folder"))
        assertTrue(File(root, "QA folder").isDirectory)

        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("קובץ חדש"))
        compose.onNodeWithText("קובץ חדש").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("todo.txt")
        compose.onNodeWithText("צור").performClick()
        compose.waitFor(hasText("todo.txt"))
        assertTrue(File(root, "todo.txt").isFile)

        // שם קיים — הדיאלוג לא אמור ליצור כפילות שקטה או לדרוס.
        compose.onNodeWithContentDescription("חדש").performClick()
        compose.waitFor(hasText("קובץ חדש"))
        compose.onNodeWithText("קובץ חדש").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("readme.md")
        compose.onNodeWithText("צור").performClick()
        Thread.sleep(800)
        Qa.screenshot(compose, "19-create-existing-name")
        assertTrue("הקובץ הקיים לא נדרס", File(root, "readme.md").readText().startsWith("# nexplorer QA"))
    }

    @Test
    fun t04_renameFromSelection() {
        longPress("readme.md")
        Qa.screenshot(compose, "20-selection-single")
        compose.onNodeWithContentDescription("שנה שם").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement("README-renamed.md")
        Qa.screenshot(compose, "21-rename-dialog")
        compose.onNodeWithText("שנה").performClick()
        compose.waitFor(hasText("README-renamed.md"))
        assertTrue(File(root, "README-renamed.md").isFile)
        assertFalse(File(root, "readme.md").exists())
    }

    @Test
    fun t05_copyTwoFilesIntoFolder() {
        longPress("archive.zip")
        compose.onNodeWithText("big.bin").performClick()
        Qa.screenshot(compose, "22-selection-multi")
        compose.onNodeWithContentDescription("העתק").performClick()
        compose.waitFor(hasText("הדבק כאן (2)"))
        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot-0.png"))
        Qa.screenshot(compose, "23-paste-bar")
        compose.onNodeWithText("הדבק כאן (2)").performClick()
        compose.waitForDisk {
            File(root, "Photos/archive.zip").length() == 12 * 1024L && File(root, "Photos/big.bin").length() == 3 * 1024 * 1024L
        }
        compose.waitFor(hasText("big.bin"))
        Qa.screenshot(compose, "24-after-paste")
        assertTrue("העתקה משאירה את המקור", File(root, "archive.zip").isFile && File(root, "big.bin").isFile)
    }

    @Test
    fun t06_moveIntoFolder() {
        longPress("readme.md")
        compose.onNodeWithContentDescription("גזור").performClick()
        compose.waitFor(hasText("הדבק כאן (1)"))
        compose.onNodeWithText("Documents").performClick()
        compose.waitFor(hasText("notes.txt"))
        compose.onNodeWithText("הדבק כאן (1)").performClick()
        compose.waitForDisk { File(root, "Documents/readme.md").isFile && !File(root, "readme.md").exists() }
        compose.waitFor(hasText("readme.md"))
        Qa.screenshot(compose, "25-after-move")
    }

    @Test
    fun t07_deleteWithConfirmation() {
        longPress("big.bin")
        compose.onNodeWithContentDescription("מחק").performClick()
        compose.waitFor(hasText("למחוק את הפריט?"))
        Qa.screenshot(compose, "26-confirm-delete")
        compose.onNodeWithText("מחק").performClick()
        compose.waitForDisk { !File(root, "big.bin").exists() }
        compose.waitGone(hasText("big.bin"))
        assertTrue("שאר הקבצים במקום", File(root, "archive.zip").isFile)
    }

    @Test
    fun t08_drawerAndProperties() {
        compose.onNodeWithContentDescription("פתח מגירת מקומות").performClick()
        Qa.screenshot(compose, "27-drawer")
        Qa.device.pressBack()
        Thread.sleep(400)

        longPress("Photos")
        clickDisplayed(hasContentDescription("עוד"))
        compose.waitFor(hasText("מאפיינים"))
        Qa.screenshot(compose, "28-item-menu")
        compose.onNodeWithText("מאפיינים").performClick()
        Thread.sleep(1_500)
        Qa.screenshot(compose, "29-properties")
        Qa.device.pressBack()
    }

    @Test
    fun t09_filterInList() {
        openOverflow()
        compose.onNodeWithText("סנן ברשימה").performClick()
        compose.waitFor(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("read")
        compose.waitGone(hasText("archive.zip"))
        compose.waitFor(hasText("readme.md"))
        Qa.screenshot(compose, "30-filter")
        compose.onNodeWithContentDescription("סגור סינון").performClick()
        compose.waitFor(hasText("archive.zip"))
    }

    @Test
    fun t10_rotationKeepsFolderAndBackGoesUp() {
        compose.onNodeWithText("Documents").performClick()
        compose.waitFor(hasText("notes.txt"))
        compose.waitFor(hasText("דוח שנתי 2026.txt"))
        Qa.screenshot(compose, "31-hebrew-names")
        Qa.device.setOrientationLeft()
        compose.waitFor(hasText("notes.txt"))
        Qa.screenshot(compose, "32-landscape")
        Qa.device.setOrientationNatural()
        compose.waitFor(hasText("notes.txt"))
        Qa.device.pressBack()
        compose.waitFor(hasText("readme.md"))
    }

    @Test
    fun t11_openFilesWithOtherApps() {
        // FileProvider: נתיב שלא מוגדר ב-file_paths.xml היה מפיל את האפליקציה כאן.
        compose.onNodeWithText("readme.md").performClick()
        Thread.sleep(1_500)
        Qa.screenshot(compose, "33-open-text")
        if (Qa.device.currentPackageName != Qa.packageName) Qa.device.pressBack()
        compose.waitFor(hasText("readme.md"))

        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot-1.png"))
        // תמונה נפתחת במציג המובנה (שלב 2ב), לא באפליקציה אחרת.
        compose.onNodeWithText("shot-1.png").performClick()
        compose.waitFor(hasText("/ 4", substring = true), 15_000)
        Qa.screenshot(compose, "34-open-image")
        Qa.device.pressBack()
        compose.waitFor(hasText("shot-2.png"))
    }
}
