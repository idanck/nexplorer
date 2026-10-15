package com.idanck.nexplorer.qa

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.qa.Qa.exists
import com.idanck.nexplorer.qa.Qa.waitFor
import com.idanck.nexplorer.qa.Qa.waitForDisk
import com.idanck.nexplorer.qa.Qa.waitGone
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * שלב 2ב: מציג התמונות, שני חלונות, קיצורי מקלדת וגרירה בין החלונות.
 * האמולטור (pixel_6) מדמה טאבלט: 800×1280dp לאורך (חלון אחד), 1280×800dp לרוחב (שני חלונות).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase2bTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        Qa.shell("wm size $TABLET_SIZE")
        Qa.shell("wm density $TABLET_DENSITY")
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    /** קודם סוגרים: שינוי גודל מסך עם פעילות פתוחה בונה אותה מחדש (ראה [Qa.recreating]). */
    @After
    fun tearDown() {
        scenario.close()
        Qa.setOrientationNatural()
        Qa.shell("wm size reset")
        Qa.shell("wm density reset")
    }

    private fun count(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * לרוחב: שני חלונות, שניהם בתיקייה של הראשון. הטאבלט עובר לרוחב דרך wm size בממדי רוחב, ולא
     * בסיבוב: ב-API 29 תחת עומס הסיבוב לפעמים לא קרה ("Didn't detect rotation within 2000ms"), וב-API 30
     * עם wm size התצוגה הסתובבה וחזרה לאורך לבד אחרי כשלוש שניות. הפעילות נבנית מחדש כמו בסיבוב.
     */
    private fun enterDualPane() {
        Qa.recreating { Qa.shell("wm size $TABLET_LANDSCAPE_SIZE") }
        compose.waitFor(hasText("גרירה בין החלונות", substring = true), 15_000)
        compose.waitUntil(15_000) { count("readme.md") == 2 }
    }

    @Test
    fun t01_imageViewer() {
        compose.onNodeWithText("Photos").performClick()
        compose.waitFor(hasText("shot-0.png"))
        compose.onNodeWithText("shot-0.png").performClick()
        compose.waitFor(hasText("/ 4", substring = true), 15_000)
        Qa.screenshot(compose, "60-viewer")
        // ב-RTL התמונה הבאה מגיעה משמאל; הבדיקה לא תלויה בכיוון או במיון — רק שהתמונה התחלפה.
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        if (compose.exists(hasText("shot-0.png"))) compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitGone(hasText("shot-0.png"))
        Qa.screenshot(compose, "61-viewer-swiped")
        compose.onRoot().performTouchInput { doubleClick() }
        Qa.screenshot(compose, "62-viewer-zoomed")
        Qa.device.pressBack()
        compose.waitFor(hasText("shot-0.png"))
        compose.waitFor(hasText("shot-3.png"))
    }

    @Test
    fun t02_dualPaneCopyMoveDeleteWithKeys() {
        enterDualPane()
        Qa.screenshot(compose, "63-dual-pane")
        // החלון השני → Empty.
        compose.onAllNodesWithText("Empty")[1].performClick()
        compose.waitFor(hasText("התיקייה ריקה"))
        compose.onNodeWithText("readme.md").performTouchInput { longClick() }
        compose.waitFor(hasText("העתק לחלון השני"))
        Qa.screenshot(compose, "64-dual-selection")
        compose.onNodeWithText("העתק לחלון השני").performClick()
        compose.waitForDisk { File(root, "Empty/readme.md").isFile }
        assertTrue("העתקה משאירה את המקור", File(root, "readme.md").isFile)
        compose.waitUntil(10_000) { count("readme.md") == 2 }

        // F6: העברה לחלון השני.
        compose.onNodeWithText("big.bin").performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        Qa.device.pressKeyCode(KeyEvent.KEYCODE_F6)
        compose.waitForDisk { File(root, "Empty/big.bin").isFile && !File(root, "big.bin").exists() }

        // Del: מחיקה, עם האישור הרגיל.
        compose.onNodeWithText("archive.zip").performTouchInput { longClick() }
        compose.waitFor(hasContentDescription("בטל בחירה"))
        Qa.device.pressKeyCode(KeyEvent.KEYCODE_FORWARD_DEL)
        compose.waitFor(hasText("למחוק את הפריט?"))
        compose.onNodeWithText("מחק").performClick()
        compose.waitForDisk { !File(root, "archive.zip").exists() }

        // Tab: החלון השני פעיל; Backspace שם עולה מ-Empty, ו-big.bin (שהועבר לתוכה) כבר לא מוצג.
        // המקש הולך לחלון שבפוקוס: מחכים שחלון הדיאלוג ייסגר והפוקוס יחזור לפעילות.
        waitForWindowFocus()
        Qa.device.pressKeyCode(KeyEvent.KEYCODE_TAB)
        compose.waitFor(hasContentDescription("חלון שני") and isSelected())
        // המקשים נרשמים מחדש אחרי ההרכבה — בלי זה Backspace עוד היה מגיע לחלון הראשון.
        compose.waitForIdle()
        Qa.device.pressKeyCode(KeyEvent.KEYCODE_DEL)
        compose.waitGone(hasText("big.bin"))
        Qa.screenshot(compose, "65-dual-after-keys")
    }

    @Test
    fun t03_dragToFolderInOtherPane() {
        enterDualPane()
        compose.onAllNodesWithText("readme.md")[0].performTouchInput { longClick() }
        // שני החלונות באותה תיקייה: בתחתית "שני החלונות באותה תיקייה", לא כפתורים — היעד הוא תיקייה.
        compose.waitFor(hasText("שני החלונות באותה תיקייה"))
        // היעד צריך להיות כולו על המסך.
        compose.onAllNodesWithText("Empty")[1].performScrollTo()
        val from = screenCenter(compose.onAllNodesWithText("readme.md")[0].fetchSemanticsNode().boundsInWindow.center)
        val to = screenCenter(compose.onAllNodesWithText("Empty")[1].fetchSemanticsNode().boundsInWindow.center)
        longPressDrag(from, to) {
            // הדגשת היעד היא הרכבה מחדש — בשעון של הבדיקה היא מחכה לפריים.
            compose.mainClock.advanceTimeByFrame()
            SystemClock.sleep(300)
            Qa.screenshot(compose, "66-dual-drag-hover", settle = false)
        }
        compose.waitForDisk { File(root, "Empty/readme.md").isFile }
        assertTrue("גרירה מעתיקה, לא מעבירה", File(root, "readme.md").isFile)
    }

    private fun waitForWindowFocus() {
        compose.waitUntil(10_000) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
    }

    private fun screenCenter(inWindow: Offset): Offset {
        val location = IntArray(2)
        scenario.onActivity { it.window.decorView.getLocationOnScreen(location) }
        return Offset(inWindow.x + location[0], inWindow.y + location[1])
    }

    /**
     * לחיצה ארוכה, גרירה ושחרור — כמו אצבע: אירועי מגע אמיתיים, כי גרירה של המערכת (DragAndDrop)
     * לא מתחילה ממחוות מדומות של Compose. [hovering] רץ מעל היעד, רגע לפני השחרור.
     * השעון של Compose בבדיקה מדומה ומתקדם רק ב-waitUntil/waitForIdle — לכן כל המתנה כאן מקדמת
     * אותו גם כן; אחרת ה-timeout של הלחיצה הארוכה לא נגמר, והגרירה פותחת את המגירה במקום.
     */
    private fun longPressDrag(from: Offset, to: Offset, hovering: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, at: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        fun pause(ms: Long) {
            SystemClock.sleep(ms)
            compose.mainClock.advanceTimeBy(ms)
        }
        inject(MotionEvent.ACTION_DOWN, from)
        val hold = ViewConfiguration.getLongPressTimeout() * 2L
        var held = 0L
        while (held < hold) {
            pause(HOLD_STEP_MS)
            held += HOLD_STEP_MS
            inject(MotionEvent.ACTION_MOVE, from)
        }
        val steps = 30
        for (i in 1..steps) {
            pause(MOVE_STEP_MS)
            inject(MotionEvent.ACTION_MOVE, from + (to - from) * (i / steps.toFloat()))
        }
        pause(HOVER_MS)
        inject(MotionEvent.ACTION_MOVE, to)
        hovering()
        inject(MotionEvent.ACTION_MOVE, to)
        inject(MotionEvent.ACTION_UP, to)
    }

    private companion object {
        /** 1600×2560 ב-320dpi = 800×1280dp — טאבלט 10 אינץ'. */
        const val TABLET_SIZE = "1600x2560"
        /** אותו טאבלט לרוחב: 1280×800dp. */
        const val TABLET_LANDSCAPE_SIZE = "2560x1600"
        const val TABLET_DENSITY = 320

        const val HOLD_STEP_MS = 50L
        const val MOVE_STEP_MS = 20L
        const val HOVER_MS = 400L
    }
}
