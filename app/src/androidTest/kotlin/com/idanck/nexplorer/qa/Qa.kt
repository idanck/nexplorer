package com.idanck.nexplorer.qa

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.data.files.FileIntents
import java.io.File

/**
 * כלים משותפים לבדיקות ה-QA על האמולטור: פקודות shell (בהרשאות shell, לא של האפליקציה),
 * צילומי מסך מלאים (כולל חלונות מערכת ודיאלוגים) ותיקיית נתונים קבועה.
 */
object Qa {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val packageName: String get() = instrumentation.targetContext.packageName
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    /** צילומי המסך נאספים מכאן ע"י `.github/qa/instrumented.sh`. */
    private const val SHOTS = "/sdcard/Download/nexplorer-qa"

    /** לקבצי אבחון שנאספים יחד עם הצילומים. */
    val shotsDir: File get() = File(SHOTS).apply { mkdirs() }

    /** `executeShellCommand` לא מריץ shell — אין הפניות ומרכאות, רק פקודה ופרמטרים. */
    fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** מתן הרשאה לא הורג את התהליך (שלילה כן) — ולכן מותר כאן, באמצע בדיקה. */
    fun grantStorage() {
        shell("appops set --uid $packageName MANAGE_EXTERNAL_STORAGE allow")
        // העברה ראשונה מבקשת הרשאת התראות; הדיאלוג של המערכת משהה את הפעילות והבדיקה מאבדת את המסך.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
        }
    }

    /**
     * כשל — כל הטקסטים שבמסך, ללוג: הודעת שגיאה יכולה לשבת מתחת למקלדת, ואז הצילום לא מראה אותה.
     * בלי לחכות שהממשק יירגע — הכשל עצמו יכול להיות שהוא לא נרגע.
     */
    fun logTexts(compose: ComposeTestRule, tag: String) {
        runCatching {
            val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            Log.e(tag, "on screen: ${texts.joinToString(" | ")}")
        }.onFailure { Log.e(tag, "could not read the screen", it) }
    }

    /** [settle] = false — באמצע מחווה (גרירה): בלי לחכות שהממשק יירגע. */
    fun screenshot(compose: ComposeTestRule, name: String, settle: Boolean = true) {
        if (settle) {
            compose.waitForIdle()
            // הבהוב הסמן, אדוות ואנימציות כניסה של חלונות — שהצילום לא יתפוס אותם באמצע.
            Thread.sleep(400)
        }
        shell("mkdir -p $SHOTS")
        shell("screencap -p $SHOTS/$name.png")
    }

    /**
     * סיבוב ונעילה, כמו `UiDevice.setOrientationLeft/Natural` — בלי ההמתנה שלו: UiAutomator 2.4 בודק את הסיבוב
     * דרך `getDisplayRotation`, שקורא קודם ל-`waitForIdle` של UiAutomation. ב-API 30 זה נתקע לפעמים אחרי שהמסך
     * כבר הסתובב (‏BrowserFlowTest ‏t10, ב-QA ‏116 וב-121: "Waiting 2000ms for Condition[displayRotation=1…]",
     * ואז 9 דקות בלי תשובה). כאן בודקים את ה-`Display` ישירות, עם תקרת זמן — כשל ולא תקיעה. סיבוב בונה את
     * הפעילות מחדש — ראה [recreating].
     */
    fun setOrientationLeft() = rotate(UiAutomation.ROTATION_FREEZE_90, Surface.ROTATION_90)

    fun setOrientationNatural() = rotate(UiAutomation.ROTATION_FREEZE_0, Surface.ROTATION_0)

    private fun rotate(freeze: Int, expected: Int) {
        val display = checkNotNull(instrumentation.targetContext.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY))
        val turn = {
            instrumentation.uiAutomation.setRotation(freeze)
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (display.rotation != expected) {
                check(SystemClock.uptimeMillis() < deadline) { "the display did not rotate to $expected (now ${display.rotation})" }
                Thread.sleep(50)
            }
        }
        // כבר בכיוון הזה — רק נעילה, והפעילות לא נבנית מחדש.
        if (display.rotation == expected) turn() else recreating(turn)
    }

    /**
     * [change] משנה את התצורה (סיבוב, ‏wm size) והפעילות נבנית מחדש; חוזר רק כשהחדשה RESUMED, והישנה כבר
     * הוסרה מהמסך (באותה הודעה של ה-main). עד אז — בלי אף קריאה ל-Compose: ‏`waitForIdle` בוחר מחוט הבדיקה
     * root שמחובר לחלון ומבקש ממנו פריים, ושם `view.post` שמסמן שהפריים עבר. אם החלון הוסר באמצע, ה-`post`
     * נשאר בתור של view מנותק ולא רץ לעולם, ו-Compose מחכה לו בלולאה בלי תקרה (‏`waitForNextChoreographerFrame`;
     * ‏QA ‏133: ה-main רץ כל הזמן בלולאה של Espresso — 267 שניות CPU — והבדיקה ב-`waitFor` אחרי הסיבוב).
     * בלי פעילות פעילה — אין מה לחכות לו.
     */
    fun recreating(change: () -> Unit) {
        val before = resumedActivity()
        change()
        if (before == null) return
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (true) {
            val now = resumedActivity()
            if (now != null && now !== before) return
            check(SystemClock.uptimeMillis() < deadline) { "the activity was not recreated" }
            Thread.sleep(50)
        }
    }

    private fun resumedActivity(): Activity? {
        var resumed: Activity? = null
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return resumed
    }

    fun launch(path: String? = null): ActivityScenario<MainActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        if (path != null) intent.setAction(FileIntents.ACTION_OPEN_FOLDER).putExtra(FileIntents.EXTRA_PATH, path)
        return ActivityScenario.launch(intent)
    }

    fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        waitUntil(timeoutMs) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    fun ComposeTestRule.waitGone(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
        waitUntil(timeoutMs) { onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    fun ComposeTestRule.exists(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** מחכה לתנאי על הדיסק (העברה רצה ברקע, בתור משלה). */
    fun ComposeTestRule.waitForDisk(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        waitUntil(timeoutMs) { condition() }
    }
}

/** עץ קבוע שכל בדיקה מתחילה ממנו מחדש. */
object QaData {

    val root: File get() = File(Environment.getExternalStorageDirectory(), "nexplorer-qa")

    fun seed() {
        root.deleteRecursively()
        val docs = File(root, "Documents").apply { mkdirs() }
        File(docs, "notes.txt").writeText("hello from the emulator\n")
        File(docs, "דוח שנתי 2026.txt").writeText("שלום\n")
        File(docs, "report.pdf").writeBytes(ByteArray(48 * 1024))
        val photos = File(root, "Photos").apply { mkdirs() }
        listOf(Color.rgb(0x1E, 0x63, 0xE9), Color.rgb(0xE9, 0x9A, 0x1E), Color.rgb(0x2E, 0x9E, 0x5B), Color.rgb(0xC8, 0x3B, 0x3B))
            .forEachIndexed { i, color -> png(File(photos, "shot-$i.png"), color) }
        File(root, "Music").mkdirs()
        File(root, "Music/song.mp3").writeBytes(ByteArray(320 * 1024))
        File(root, "Empty").mkdirs()
        File(root, "readme.md").writeText("# nexplorer QA\n")
        File(root, "archive.zip").writeBytes(ByteArray(12 * 1024))
        File(root, "big.bin").writeBytes(ByteArray(3 * 1024 * 1024))
        File(root, ".hidden").writeText("hidden\n")
    }

    private fun png(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        canvas.drawCircle(160f, 120f, 70f, Paint().apply { this.color = Color.WHITE; isAntiAlias = true })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
