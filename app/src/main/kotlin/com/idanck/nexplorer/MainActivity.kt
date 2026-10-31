package com.idanck.nexplorer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.settings.ThemeMode
import com.idanck.nexplorer.media.PlaybackService
import com.idanck.nexplorer.ui.NexplorerRoot
import com.idanck.nexplorer.ui.keys.KeyShortcuts
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.peer.LocalPeerUi
import com.idanck.nexplorer.ui.peer.PeerUi
import com.idanck.nexplorer.ui.tv.LocalTvMode
import com.idanck.nexplorer.ui.tv.TvMode

class MainActivity : ComponentActivity() {

    /** נתיב מקיצור דרך במסך הבית, עד שהמסך הראשי פותח אותו. */
    private val pendingPath = mutableStateOf<String?>(null)

    /** הקשה על התראת הנגן, עד שהמסך הראשי פותח אותו. */
    private val pendingPlayer = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        val container = (application as NexplorerApp).container
        // Google TV (1.15): מסך לשלט, וערכה כהה תמיד (מסך גדול בחדר — בלי משטחים לבנים).
        val tv = TvMode.isTv(this)
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val dark = if (tv) true else when (settings.theme) {
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
                // העברה בין מכשירים (1.16): מה שפתוח ("שלח אל", קוד) — משותף לכל המסכים.
                val peerUi = remember { PeerUi(container.peers, container.transfers, container.scope) }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTvMode provides tv, LocalPeerUi provides peerUi) {
                    NexplorerRoot(
                        container = container,
                        pendingPath = pendingPath.value,
                        onPendingPathConsumed = { pendingPath.value = null },
                        showPlayer = pendingPlayer.value,
                        onShowPlayerConsumed = { pendingPlayer.value = false },
                    )
                }
            }
        }
        anchorKeyFocus()
    }

    /** ה-View של Compose — הילד היחיד של android.R.id.content אחרי setContent. */
    private val contentRoot: ViewGroup?
        get() = findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0) as? ViewGroup

    /**
     * במצב מגע, מאז Android 9, אף View לא בפוקוס — ואז Tab הראשון אחרי נגיעה לא מגיע ל-[dispatchKeyEvent]:
     * ViewRootImpl מנצל אותו ליציאה ממצב מגע ומעביר את הפוקוס לפריט הראשון. ה-ComposeView מחזיק את
     * הפוקוס בעצמו (לפני הילדים), ולכן היציאה ממצב מגע משאירה אותו במקום ו-Tab מגיע לקיצורים.
     * אחרי המקש הזה הפוקוס עובר ל-Compose, כדי שהחצים ינווטו בין הפריטים. שדה טקסט לוקח את הפוקוס
     * כרגיל, וכשהוא משחרר אותו — הפוקוס חוזר לכאן.
     */
    private fun anchorKeyFocus() {
        val root = contentRoot ?: return
        root.isFocusableInTouchMode = true
        root.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        // לא תחנה ריקה ב-TalkBack; הילדים (Compose) נשארים נגישים.
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.defaultFocusHighlightEnabled = false
        val tree = root.viewTreeObserver
        tree.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null) root.post { if (currentFocus == null) root.requestFocus() }
        }
        // נקרא בתוך עיבוד המקש, לפני ש-ViewRootImpl מחפש למי לתת פוקוס; המעבר ל-Compose אחריו.
        tree.addOnTouchModeChangeListener { inTouchMode ->
            if (!inTouchMode && currentFocus === root) root.post { root.getChildAt(0)?.requestFocus() }
        }
    }

    /** העברה בין מכשירים (1.16): גלויים ומחפשים רק כשהאפליקציה פתוחה — ההחלטה של עידן, בלי שירות ברקע. */
    override fun onStart() {
        super.onStart()
        (application as NexplorerApp).container.peers.start()
    }

    /**
     * יציאה מהאפליקציה (לא סיבוב) נועלת את התיקיות המוצפנות — המפתח לא נשאר בזיכרון ברקע — ומפסיקה את העברה בין
     * מכשירים (העברות שכבר באמצע ממשיכות).
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val container = (application as NexplorerApp).container
            container.vaults.lockAll()
            container.peers.stop()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentFocus == null) contentRoot?.requestFocus()
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
        when (intent?.action) {
            FileIntents.ACTION_OPEN_FOLDER -> pendingPath.value = intent.getStringExtra(FileIntents.EXTRA_PATH)
            PlaybackService.ACTION_SHOW_PLAYER -> pendingPlayer.value = true
        }
    }
}
