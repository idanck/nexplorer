package com.idanck.nexplorer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.settings.ThemeMode
import com.idanck.nexplorer.ui.NexplorerRoot
import com.idanck.nexplorer.ui.keys.KeyShortcuts
import com.idanck.nexplorer.ui.theme.NexplorerTheme

class MainActivity : ComponentActivity() {

    /** נתיב מקיצור דרך במסך הבית, עד שהמסך הראשי פותח אותו. */
    private val pendingPath = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        val container = (application as NexplorerApp).container
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // צבע האייקונים בשורת המצב לפי הערכה שנבחרה באפליקציה, לא לפי המערכת.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            NexplorerTheme(darkTheme = dark) {
                // הממשק כתוב בעברית בלבד בשלב הזה, ולכן תמיד מימין לשמאל — גם במכשיר באנגלית (D7).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NexplorerRoot(
                        container = container,
                        pendingPath = pendingPath.value,
                        onPendingPathConsumed = { pendingPath.value = null },
                    )
                }
            }
        }
    }

    /**
     * אין תפריט אפשרויות (הכל ב-⋮). ComponentActivity מחזירה כאן true, ומקש MENU פותח חלון תפריט ריק:
     * ב-Android 10 הוא מקבל את הפוקוס בלי להצטייר, וכל קלט אחריו נתקע (נמצא ב-monkey על API 29).
     */
    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean =
        if (featureId == Window.FEATURE_OPTIONS_PANEL) false else super.onCreatePanelMenu(featureId, menu)

    /** מקשים שהפעולה שלהם בוצעה ב-ACTION_DOWN — גם ה-UP וההחזקה שלהם לא ממשיכים הלאה. */
    private val handledKeys = mutableSetOf<Int>()

    /**
     * קיצורי מקלדת לפני שהמקש מגיע לממשק: אחרת Tab ו-Del נבלעים בניווט הפוקוס של Compose.
     * שדה טקסט בפוקוס (onCheckIsTextEditor) מקבל את מקשי העריכה כרגיל.
     * RestrictedApi: זה Activity.dispatchKeyEvent, API ציבורי; lint מסמן אותו רק כי androidx.core
     * דורס אותו ב-ComponentActivity ומסמן את הדריסה שלו כפנימית.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keys = (application as NexplorerApp).container.keys
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 && event.keyCode in handledKeys) return true
                // UP שאבד (מעבר חלון באמצע) לא משאיר את המקש חסום.
                handledKeys -= event.keyCode
                val editing = currentFocus?.onCheckIsTextEditor() == true
                val command = KeyShortcuts.commandFor(event.keyCode, event.isCtrlPressed, event.isAltPressed, editing)
                if (command != null && event.repeatCount == 0 && keys.dispatch(command)) {
                    handledKeys += event.keyCode
                    return true
                }
            }
            KeyEvent.ACTION_UP -> if (handledKeys.remove(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        if (intent?.action == FileIntents.ACTION_OPEN_FOLDER) {
            pendingPath.value = intent.getStringExtra(FileIntents.EXTRA_PATH)
        }
    }
}
