package com.idanck.nexplorer.qa

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.media3.ui.PlayerView
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.media.PlaybackService
import com.idanck.nexplorer.qa.Qa.exists
import com.idanck.nexplorer.qa.Qa.waitFor
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.sin

/** שלב 3ב: נגן השמע (רשימה מהתיקייה, ניגון ברקע, חזרה מההתראה) ונגן הווידאו (סיבוב בלי לאבד מקום). */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase3bTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val root get() = QaData.root
    private val app get() = ApplicationProvider.getApplicationContext<Context>()

    private companion object {
        const val TAG = "Phase3bTest"
    }

    private fun playback(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    @Before
    fun setUp() {
        Qa.grantStorage()
        QaData.seed()
        // תיקייה בלי song.mp3 של QaData (אפסים — לא שמע אמיתי): רק שני צלילים תקינים.
        val tracks = File(root, "Tracks").apply { mkdirs() }
        // המיון ברירת המחדל: החדש קודם (ובשוויון — לפי שם, הפוך). a נכתב אחרון ושנייה אחרי b, ולכן
        // הוא ראשון ברשימה — גם במיון לפי שם.
        wav(File(tracks, "b-tone.wav"), seconds = 30, frequency = 660.0)
        Thread.sleep(1_100)
        wav(File(tracks, "a-tone.wav"), seconds = 30, frequency = 440.0)
        val videos = File(root, "Videos").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open("clip.mp4").use { input ->
            File(videos, "clip.mp4").outputStream().use { input.copyTo(it) }
        }
        scenario = Qa.launch(root.absolutePath)
        compose.waitFor(hasText("readme.md"), 15_000)
    }

    @After
    fun tearDown() {
        Qa.setOrientationNatural()
        scenario.close()
        // השירות ממשיך לנגן גם בלי מסך — זו הכוונה; בין בדיקות עוצרים אותו.
        app.stopService(Intent(app, PlaybackService::class.java))
    }

    @Test
    fun t01_audioPlaysTheFolderAndKeepsPlayingInTheBackground() {
        compose.onNodeWithText("Tracks").performClick()
        compose.waitFor(hasText("a-tone.wav"))
        compose.onNodeWithText("a-tone.wav").performClick()
        compose.waitFor(hasContentDescription("השהה"), 15_000)
        compose.waitFor(hasText("1 / 2"))
        // מנגן באמת: המיקום מתקדם.
        compose.waitFor(hasText("0:02"), 15_000)
        assertTrue("אין MediaSession פעיל", Qa.shell("dumpsys media_session").contains(Qa.packageName))
        Qa.screenshot(compose, "80-audio-player")

        compose.onNodeWithContentDescription("הבא").performClick()
        compose.waitFor(hasText("2 / 2"), 10_000)

        // יציאה מהמסך: הניגון ממשיך; ההתראה (SHOW_PLAYER) מחזירה לאותו נגן, באותו שיר.
        Qa.device.pressBack()
        compose.waitGone(hasText("נגן שמע"))
        compose.waitFor(hasText("b-tone.wav"))
        Qa.shell("am start -a ${PlaybackService.ACTION_SHOW_PLAYER} -n ${Qa.packageName}/${MainActivity::class.java.name}")
        compose.waitFor(hasText("נגן שמע"), 15_000)
        compose.waitFor(hasText("2 / 2"))
        compose.waitFor(hasContentDescription("השהה"))

        compose.onNodeWithContentDescription("השהה").performClick()
        compose.waitFor(hasContentDescription("נגן"), 10_000)
        Qa.screenshot(compose, "81-audio-paused")
    }

    @Test
    fun t02_videoKeepsItsPlaceAcrossRotation() {
        compose.onNodeWithText("Videos").performClick()
        compose.waitFor(hasText("clip.mp4"))
        compose.onNodeWithText("clip.mp4").performClick()
        compose.waitFor(playback("מנגן"), 20_000)
        Thread.sleep(1_500)
        Qa.screenshot(compose, "82-video")

        pauseWithPlayerButton()
        assertTrue("הפריים המושהה לא מוצג", waitForVideoFrame())

        // סיבוב בונה את המסך מחדש; הנגן ב-ViewModel — נשאר מושהה באותה נקודה, ולא נטען מחדש (טעינה מנגנת).
        // המשטח החדש ריק, והפריים המושהה מצויר עליו מחדש (ב-API 35 בלי זה המסך נשאר שחור).
        Qa.setOrientationLeft()
        Thread.sleep(2_000)
        compose.waitFor(playback("מושהה"), 15_000)
        assertFalse("הסרטון נטען מחדש אחרי הסיבוב", compose.exists(playback("מנגן")))
        assertTrue("אחרי הסיבוב המסך שחור — הפריים המושהה לא צויר מחדש", waitForVideoFrame())
        Qa.screenshot(compose, "83-video-rotated")

        Qa.setOrientationNatural()
        Qa.device.pressBack()
        compose.waitFor(hasText("clip.mp4"), 10_000)
        compose.waitGone(playback("מושהה"))
    }

    /**
     * עצירה בכפתור של Media3 עצמו (ה-View שלו — לא Player.pause מהקוד). UiAutomator לא מוצא את
     * ה-Views שבתוך ה-AndroidView (בדיקה ב-API 29 ו-35: "Node not found"), ולכן הלחיצה דרך ה-View.
     * לפני כן: הבקרים מוצגים, ועץ הנגישות נשמר — מה ש-TalkBack רואה מהנגן.
     */
    private fun pauseWithPlayerButton() {
        withPlayerView { it.showController() }
        val seen = Qa.device.wait(Until.hasObject(By.res(Pattern.compile(".*:id/exo_play_pause"))), 3_000)
        Log.i(TAG, "exo_play_pause in the accessibility tree: $seen")
        Qa.device.dumpWindowHierarchy(File(Qa.shotsDir, "84-video-a11y.xml"))
        var clicked = false
        withPlayerView { view -> clicked = view.findViewByEntryName("exo_play_pause")?.performClick() == true }
        assertTrue("אין כפתור נגן/השהה בבקרי הנגן", clicked)
        compose.waitFor(playback("מושהה"), 5_000)
    }

    private fun withPlayerView(action: (PlayerView) -> Unit) {
        scenario.onActivity { activity -> activity.window.decorView.findPlayerView()?.let(action) }
    }

    private fun View.findPlayerView(): PlayerView? {
        if (this is PlayerView) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findPlayerView()?.let { return it }
        return null
    }

    /** לפי שם המזהה ולא לפי R של Media3: המספרים נקבעים ב-APK של האפליקציה. */
    private fun View.findViewByEntryName(name: String): View? {
        if (id != View.NO_ID && runCatching { resources.getResourceEntryName(id) }.getOrNull() == name) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findViewByEntryName(name)?.let { return it }
        return null
    }

    /**
     * יש סרטון על המסך: בשורה ב-40% מהגובה יש פיקסלים צבעוניים (פסי הצבע של testsrc). מסך שחור,
     * הבקרים הלבנים והרקע המוחשך שלהם — אפורים, ולא נספרים.
     */
    private fun waitForVideoFrame(timeoutMs: Long = 8_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (colorfulPixelsOnScreen() > 10) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun colorfulPixelsOnScreen(): Int {
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return 0
        val pixels = if (shot.config == Bitmap.Config.HARDWARE) shot.copy(Bitmap.Config.ARGB_8888, false) else shot
        try {
            val y = pixels.height * 4 / 10
            return (0 until pixels.width step 8).count { x ->
                val c = pixels.getPixel(x, y)
                maxOf(Color.red(c), Color.green(c), Color.blue(c)) - minOf(Color.red(c), Color.green(c), Color.blue(c)) > 40
            }
        } finally {
            if (pixels !== shot) pixels.recycle()
            shot.recycle()
        }
    }

    /** WAV ‏16 ביט מונו: צליל סינוס, בלי תגיות — הכותרת בנגן היא שם הקובץ. */
    private fun wav(file: File, seconds: Int, frequency: Double) {
        val rate = 8_000
        val samples = rate * seconds
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) data.putShort((sin(2 * PI * frequency * i / rate) * 6_000).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + samples * 2)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(samples * 2)
        }
        file.outputStream().use {
            it.write(header.array())
            it.write(data.array())
        }
    }
}
