package com.idanck.nexplorer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
