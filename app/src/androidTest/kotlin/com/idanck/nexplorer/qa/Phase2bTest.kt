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

/** שלב 2ב: מציג התמונות, שני חלונות (טלפון לרוחב = 914dp), קיצורי מקלדת וגרירה בין החלונות. */
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
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() {
        Qa.device.setOrientationNatural()
        scenario.close()
    }

    private fun count(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** לרוחב: שני חלונות, שניהם בתיקייה של הראשון. */
    private fun enterDualPane() {
        Qa.device.setOrientationLeft()
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
        // החלון השני → Empty (שורה רביעית — לרוחב Documents, האחרונה, לא נכנסת בחלון עם שורת הרמז).
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
        Qa.device.pressKeyCode(KeyEvent.KEYCODE_TAB)
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
        // לרוחב נכנסות רק כמה שורות; היעד צריך להיות כולו על המסך.
        compose.onAllNodesWithText("Empty")[1].performScrollTo()
        val from = screenCenter(compose.onAllNodesWithText("readme.md")[0].fetchSemanticsNode().boundsInWindow.center)
        val to = screenCenter(compose.onAllNodesWithText("Empty")[1].fetchSemanticsNode().boundsInWindow.center)
        longPressDrag(from, to) { Qa.screenshot(compose, "66-dual-drag-hover", settle = false) }
        compose.waitForDisk { File(root, "Empty/readme.md").isFile }
        assertTrue("גרירה מעתיקה, לא מעבירה", File(root, "readme.md").isFile)
    }

    private fun screenCenter(inWindow: Offset): Offset {
        val location = IntArray(2)
        scenario.onActivity { it.window.decorView.getLocationOnScreen(location) }
        return Offset(inWindow.x + location[0], inWindow.y + location[1])
    }

    /**
     * לחיצה ארוכה, גרירה ושחרור — כמו אצבע: אירועי מגע אמיתיים, כי גרירה של המערכת (DragAndDrop)
     * לא מתחילה ממחוות מדומות של Compose. [hovering] רץ מעל היעד, רגע לפני השחרור.
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
        inject(MotionEvent.ACTION_DOWN, from)
        val hold = ViewConfiguration.getLongPressTimeout() * 2L
        while (SystemClock.uptimeMillis() - downTime < hold) {
            SystemClock.sleep(50)
            inject(MotionEvent.ACTION_MOVE, from)
        }
        val steps = 30
        for (i in 1..steps) {
            SystemClock.sleep(20)
            inject(MotionEvent.ACTION_MOVE, from + (to - from) * (i / steps.toFloat()))
        }
        SystemClock.sleep(400)
        inject(MotionEvent.ACTION_MOVE, to)
        hovering()
        inject(MotionEvent.ACTION_MOVE, to)
        inject(MotionEvent.ACTION_UP, to)
    }
}
