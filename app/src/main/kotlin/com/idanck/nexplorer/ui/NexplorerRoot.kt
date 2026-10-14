package com.idanck.nexplorer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idanck.nexplorer.AppContainer
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.ui.browser.BrowserScreen
import com.idanck.nexplorer.ui.browser.BrowserViewModel
import com.idanck.nexplorer.ui.browser.Location
import com.idanck.nexplorer.ui.permission.StorageAccess
import com.idanck.nexplorer.ui.permission.StorageAccessScreen
import com.idanck.nexplorer.ui.search.SearchScreen
import com.idanck.nexplorer.ui.search.SearchViewModel
import com.idanck.nexplorer.ui.settings.SettingsModel
import com.idanck.nexplorer.ui.settings.SettingsScreen
import com.idanck.nexplorer.ui.update.UpdateActions
import kotlinx.coroutines.launch

private enum class Screen { Browser, Search, Settings }

/**
 * השורש: בלי גישה לקבצים — מסך הגישה; אחרת חלון הקבצים, ומעליו חיפוש או הגדרות.
 * בכל חזרה לאפליקציה: בדיקת ההרשאות מחדש (הן ניתנות במסכי המערכת) ובדיקת עדכון יומית.
 */
@Composable
fun NexplorerRoot(container: AppContainer, pendingPath: String?, onPendingPathConsumed: () -> Unit) {
    val context = LocalContext.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    val canInstall by container.updates.canInstall.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(StorageAccess.granted(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = StorageAccess.granted(context)
        container.updates.refreshInstallPermission()
        container.scope.launch { container.updates.check(force = false) }
    }

    if (!granted) {
        StorageAccessScreen(onGrantedLegacy = { granted = StorageAccess.granted(context) })
        return
    }

    val browser: BrowserViewModel = viewModel()
    val search: SearchViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(Screen.Browser) }
    var searchRoot by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingPath) {
        if (pendingPath != null) {
            browser.openPath(pendingPath)
            screen = Screen.Browser
            onPendingPathConsumed()
        }
    }

    // ב-scope של האפליקציה: הורדת עדכון לא נעצרת כשעוברים מסך.
    val updateActions = remember(container) {
        UpdateActions(
            onInstall = { manifest -> container.scope.launch { container.updates.downloadAndInstall(manifest) } },
            onLater = container.updates::dismiss,
            onSkip = { manifest -> container.scope.launch { container.updates.skip(manifest) } },
            onOpenInstallPermission = {
                runCatching { context.startActivity(container.updates.installPermissionIntent()) }
                    .onFailure { Toast.makeText(context, "פתח: הגדרות ← אפליקציות ← גישה מיוחדת ← התקנת אפליקציות לא מוכרות", Toast.LENGTH_LONG).show() }
            },
            onContinueInstall = {
                if (!container.updates.continueInstall(context)) container.updates.dismiss()
            },
        )
    }

    fun updateSettings(transform: (com.idanck.nexplorer.data.settings.AppSettings) -> com.idanck.nexplorer.data.settings.AppSettings) {
        container.scope.launch { container.settings.update(transform) }
    }

    when (screen) {
        Screen.Browser -> BrowserScreen(
            vm = browser,
            transfers = container.transfers,
            settings = settings,
            updateState = updateState,
            updateActions = updateActions,
            onOpenSearch = { root ->
                searchRoot = root
                screen = Screen.Search
            },
            onOpenSettings = { screen = Screen.Settings },
        )

        Screen.Search -> {
            BackHandler { screen = Screen.Browser }
            SearchScreen(
                viewModel = search,
                root = searchRoot,
                onBack = { screen = Screen.Browser },
                onOpen = { entry ->
                    if (entry.isDirectory) {
                        browser.navigate(Location.Folder(entry.path))
                        screen = Screen.Browser
                    } else if (!FileIntents.open(context, entry.path, entry.name)) {
                        Toast.makeText(context, "אין אפליקציה שפותחת קבצים מסוג זה", Toast.LENGTH_SHORT).show()
                    }
                },
                onShowInFolder = { entry ->
                    browser.openPath(entry.path.substringBeforeLast('/'))
                    screen = Screen.Browser
                },
            )
        }

        Screen.Settings -> {
            BackHandler { screen = Screen.Browser }
            SettingsScreen(
                SettingsModel(
                    settings = settings,
                    storageGranted = granted,
                    updatesEnabled = container.updates.enabled,
                    update = updateState,
                    canInstall = canInstall,
                    onTheme = { mode -> updateSettings { it.copy(theme = mode) } },
                    onConfirmDelete = { v -> updateSettings { it.copy(confirmDelete = v) } },
                    onShowHidden = { v -> updateSettings { it.copy(defaultView = it.defaultView.copy(showHidden = v)) } },
                    onReopenLast = { v -> updateSettings { it.copy(reopenLastFolder = v) } },
                    onStorageAccess = { StorageAccess.openSettings(context) },
                    onCheckUpdates = { container.scope.launch { container.updates.check(force = true) } },
                    onAutoCheck = { v -> updateSettings { it.copy(updateAutoCheck = v) } },
                    onWifiOnly = { v -> updateSettings { it.copy(updateWifiOnly = v) } },
                    updateActions = updateActions,
                    onBack = { screen = Screen.Browser },
                ),
            )
        }
    }
}
