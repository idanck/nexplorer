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
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.net.CloudAuth
import com.idanck.nexplorer.data.net.CloudAuths
import com.idanck.nexplorer.data.net.CloudGrant
import com.idanck.nexplorer.data.net.ConnectCheck
import com.idanck.nexplorer.data.net.ConnectionSecrets
import com.idanck.nexplorer.data.net.FixedToken
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.NetDiscovery
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.net.TokenStore
import com.idanck.nexplorer.ui.net.ConnectScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.idanck.nexplorer.ui.analyzer.AnalyzerScreen
import com.idanck.nexplorer.ui.analyzer.AnalyzerViewModel
import com.idanck.nexplorer.ui.browser.BrowserScreen
import com.idanck.nexplorer.ui.browser.BrowserViewModel
import com.idanck.nexplorer.ui.browser.Location
import com.idanck.nexplorer.ui.editor.EditorScreen
import com.idanck.nexplorer.ui.editor.EditorViewModel
import com.idanck.nexplorer.ui.permission.StorageAccess
import com.idanck.nexplorer.ui.permission.StorageAccessScreen
import com.idanck.nexplorer.ui.search.SearchScreen
import com.idanck.nexplorer.ui.search.SearchViewModel
import com.idanck.nexplorer.ui.settings.SettingsModel
import com.idanck.nexplorer.ui.settings.SettingsScreen
import com.idanck.nexplorer.ui.update.UpdateActions
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.MediaFormats
import com.idanck.nexplorer.ui.browser.DualPaneScreen
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import com.idanck.nexplorer.data.peer.PeerHost
import com.idanck.nexplorer.ui.peer.LocalPeerUi
import com.idanck.nexplorer.ui.peer.PeerOverlays
import com.idanck.nexplorer.ui.peer.TvNearbyRail
import com.idanck.nexplorer.ui.settings.PeerSettings
import com.idanck.nexplorer.ui.tv.LocalTvMode
import com.idanck.nexplorer.ui.tv.TvAction
import com.idanck.nexplorer.ui.tv.TvBrowserScreen
import com.idanck.nexplorer.ui.player.AudioPlayerScreen
import com.idanck.nexplorer.ui.player.AudioPlayerViewModel
import com.idanck.nexplorer.ui.player.VideoPlayerScreen
import com.idanck.nexplorer.ui.player.VideoPlayerViewModel
import com.idanck.nexplorer.ui.viewer.ImageViewerScreen
import com.idanck.nexplorer.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

private enum class Screen { Browser, Search, Settings, Analyzer, Editor, Viewer, Audio, Video, Connect }

/**
 * השורש: בלי גישה לקבצים — מסך הגישה; אחרת חלון הקבצים, ומעליו חיפוש או הגדרות.
 * בכל חזרה לאפליקציה: בדיקת ההרשאות מחדש (הן ניתנות במסכי המערכת) ובדיקת עדכון יומית.
 */
@Composable
fun NexplorerRoot(
    container: AppContainer,
    pendingPath: String?,
    onPendingPathConsumed: () -> Unit,
    /** הקשה על התראת הנגן. */
    showPlayer: Boolean,
    onShowPlayerConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val tv = LocalTvMode.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val updateState by container.updates.state.collectAsStateWithLifecycle()
    val canInstall by container.updates.canInstall.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(StorageAccess.granted()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = StorageAccess.granted()
        container.updates.refreshInstallPermission()
        container.scope.launch { container.updates.check(force = false) }
    }

    if (!granted) {
        StorageAccessScreen()
        return
    }

    val browser: BrowserViewModel = viewModel()
    val browserState by browser.state.collectAsStateWithLifecycle()
    val peerUi = LocalPeerUi.current
    val peerDevices by container.peers.devices.collectAsStateWithLifecycle()
    val search: SearchViewModel = viewModel()
    var screen by rememberSaveable { mutableStateOf(Screen.Browser) }
    var searchRoot by rememberSaveable { mutableStateOf<String?>(null) }
    var analyzerRoot by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerPath by rememberSaveable { mutableStateOf<String?>(null) }
    val viewer: ViewerViewModel = viewModel()
    fun openImage(path: String) {
        viewer.open(path)
        viewerPath = path
        screen = Screen.Viewer
    }
    // הנגנים נוצרים רק כשנפתח קובץ שמע או וידאו (ה-ViewModel של השמע מתחבר לשירות הניגון).
    var audioPath by rememberSaveable { mutableStateOf<String?>(null) }
    var audioRequest by remember { mutableStateOf<String?>(null) }
    var videoPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun openMedia(entry: Entry) {
        when (entry.kind) {
            FileKind.Audio -> {
                audioRequest = entry.path
                audioPath = entry.path
                screen = Screen.Audio
            }
            FileKind.Video -> {
                videoPath = entry.path
                screen = Screen.Video
            }
            else -> openImage(entry.path)
        }
    }

    // "הוסף מקום" / "ערוך": החיבור שנערך (null — חדש), והחלון שבו ייפתח אחרי שהתחבר.
    var connectEditing by rememberSaveable { mutableStateOf<String?>(null) }
    var connectTarget by remember { mutableStateOf<BrowserViewModel?>(null) }
    val discovery = remember(context) { NetDiscovery(context) }
    val onOpenConnect: (BrowserViewModel, NetConnection?) -> Unit = { target, editing ->
        connectTarget = target
        connectEditing = editing?.id
        screen = Screen.Connect
    }

    LaunchedEffect(pendingPath) {
        if (pendingPath != null) {
            browser.openPath(pendingPath)
            screen = Screen.Browser
            onPendingPathConsumed()
        }
    }
    LaunchedEffect(showPlayer) {
        if (showPlayer) {
            screen = Screen.Audio
            onShowPlayerConsumed()
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

    // העברה בין מכשירים (1.16): קוד לחיבור, קבלת קבצים, "שלח אל" — מעל כל מסך.
    PeerOverlays()

    when (screen) {
        Screen.Browser -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val onOpenSearch: (String?) -> Unit = { root ->
                searchRoot = root
                screen = Screen.Search
            }
            val onOpenAnalyzer: (String?) -> Unit = { path ->
                analyzerRoot = path
                screen = Screen.Analyzer
            }
            val onEditText: (String) -> Unit = { path ->
                editorPath = path
                screen = Screen.Editor
            }
            // Google TV (1.15): מסך לשלט, על אותו ViewModel. טאבלט: שני חלונות (לוח Tablet). החלון הראשון הוא אותו
            // חלון של הטלפון.
            if (tv) {
                TvBrowserScreen(
                    vm = browser,
                    transfers = container.transfers,
                    settings = settings,
                    updateState = updateState,
                    updateActions = updateActions,
                    keys = container.keys,
                    remote = container.remote,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = { screen = Screen.Settings },
                    onEditText = onEditText,
                    onOpenMedia = ::openMedia,
                    onOpenConnect = onOpenConnect,
                    nearbyContent = { expanded ->
                        TvNearbyRail(expanded, activeConnectionId = browserState.folderPath?.let(NetPaths::connectionId)) { c ->
                            browser.navigate(Location.Folder(c.root))
                        }
                    },
                    extraActions = peerUi?.let { ui ->
                        listOf(
                            TvAction("שלח למכשיר", Icons.AutoMirrored.Outlined.Send) { entries ->
                                ui.requestSend(entries.map { it.path })
                                browser.clearSelection()
                            },
                        )
                    }.orEmpty(),
                )
            } else if (maxWidth >= NexplorerDimens.DualPaneMinWidth && maxHeight >= NexplorerDimens.DualPaneMinHeight) {
                DualPaneScreen(
                    panes = listOf(browser, viewModel<BrowserViewModel>(key = SECOND_PANE)),
                    transfers = container.transfers,
                    settings = settings,
                    updateState = updateState,
                    updateActions = updateActions,
                    keys = container.keys,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenAnalyzer = onOpenAnalyzer,
                    onEditText = onEditText,
                    onOpenMedia = ::openMedia,
                    remote = container.remote,
                    onOpenConnect = onOpenConnect,
                )
            } else {
                BrowserScreen(
                    vm = browser,
                    transfers = container.transfers,
                    settings = settings,
                    updateState = updateState,
                    updateActions = updateActions,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenAnalyzer = onOpenAnalyzer,
                    onEditText = onEditText,
                    onOpenMedia = ::openMedia,
                    keys = container.keys,
                    remote = container.remote,
                    onOpenConnect = onOpenConnect,
                )
            }
        }

        Screen.Connect -> {
            val editing = connectEditing?.let { id -> settings.connections.firstOrNull { it.id == id } }
            ConnectScreen(
                editing = editing,
                discovery = discovery,
                onConnect = { draft, password, key ->
                    val result = connectAndSave(container, draft, password, key)
                    // השורש של מה שנשמר: ב-SFTP בלי תיקייה — הבית שהבדיקה מצאה.
                    if (result is ConnectCheck.Ok) (connectTarget ?: browser).navigate(Location.Folder(result.connection.root))
                    result
                },
                onCloudConnect = { draft, grant ->
                    val result = connectCloudAndSave(container, draft, grant)
                    if (result is ConnectCheck.Ok) (connectTarget ?: browser).navigate(Location.Folder(result.connection.root))
                    result
                },
                onClose = {
                    connectEditing = null
                    screen = Screen.Browser
                },
            )
        }

        Screen.Viewer -> {
            val path = viewerPath
            if (path == null) {
                LaunchedEffect(Unit) { screen = Screen.Browser }
            } else {
                ImageViewerScreen(
                    viewModel = viewer,
                    path = path,
                    onPathChange = { viewerPath = it },
                    onBack = { screen = Screen.Browser },
                )
            }
        }

        Screen.Audio -> {
            val audio = viewModel<AudioPlayerViewModel>()
            LaunchedEffect(audioRequest) {
                audioRequest?.let {
                    audio.open(it)
                    audioRequest = null
                }
            }
            AudioPlayerScreen(viewModel = audio, path = audioPath, onBack = { screen = Screen.Browser })
        }

        Screen.Video -> {
            val path = videoPath
            if (path == null) {
                LaunchedEffect(Unit) { screen = Screen.Browser }
            } else {
                VideoPlayerScreen(
                    viewModel = viewModel<VideoPlayerViewModel>(),
                    path = path,
                    onPathChange = { videoPath = it },
                    onBack = { screen = Screen.Browser },
                )
            }
        }

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
                    } else if (MediaFormats.opensInApp(entry)) {
                        openMedia(entry)
                    } else if (EncryptedFiles.hasEncryptedName(entry.name) || NetPaths.isRemote(entry.path)) {
                        // מוצפן — חלון הסיסמה של מסך הקבצים, בתיקייה של הקובץ; מהרשת (חיפוש ב-NAS, 7ב) — "פתח" של מסך
                        // הקבצים, שמוריד עותק כשצריך.
                        browser.openPath(entry.path)
                        browser.open(entry)
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

        Screen.Analyzer -> AnalyzerScreen(
            viewModel = viewModel<AnalyzerViewModel>(),
            startPath = analyzerRoot,
            confirmDelete = settings.confirmDelete,
            onBack = { screen = Screen.Browser },
            onOpenFolder = { path ->
                browser.openPath(path)
                screen = Screen.Browser
            },
        )

        Screen.Editor -> {
            val path = editorPath
            if (path == null) {
                LaunchedEffect(Unit) { screen = Screen.Browser }
            } else {
                EditorScreen(
                    viewModel = viewModel<EditorViewModel>(),
                    path = path,
                    onBack = {
                        screen = Screen.Browser
                        browser.refresh()
                    },
                )
            }
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
                    onUnhideFolder = { path -> updateSettings { it.copy(hiddenFolders = it.hiddenFolders - path) } },
                    onStorageAccess = { StorageAccess.openSettings(context) },
                    onCheckUpdates = { container.scope.launch { container.updates.check(force = true) } },
                    onAutoCheck = { v -> updateSettings { it.copy(updateAutoCheck = v) } },
                    onWifiOnly = { v -> updateSettings { it.copy(updateWifiOnly = v) } },
                    peers = PeerSettings(
                        name = settings.peerName.ifBlank { container.peers.defaultName },
                        visible = settings.peerVisible,
                        receiveDir = PeerHost.RECEIVE_DIR,
                        devices = peerDevices,
                        onVisible = { v ->
                            container.scope.launch {
                                container.settings.update { it.copy(peerVisible = v) }
                                withContext(Dispatchers.Main) { container.peers.refresh() }
                            }
                        },
                        onRename = { name ->
                            container.scope.launch {
                                container.settings.update { it.copy(peerName = name.trim()) }
                                withContext(Dispatchers.Main) { container.peers.refresh() }
                            }
                        },
                        onUnpair = { c -> container.scope.launch { container.peers.unpair(c) } },
                    ),
                    updateActions = updateActions,
                    onBack = { screen = Screen.Browser },
                ),
            )
        }
    }
}

/** המפתח של ה-ViewModel של החלון השני (הראשון הוא ברירת המחדל, משותף עם תצוגת הטלפון). */
private const val SECOND_PANE = "pane-2"

/**
 * ענן, אחרי ההתחברות: Microsoft — הקוד מוחלף באסימונים; אחר כך החשבון ורשימת השורש ([RemoteFiles.testCloud]),
 * ורק אם עבדו — ה-refresh token (OneDrive) מוצפן ב-Keystore, והחיבור נשמר. "התחבר מחדש" בעריכה — אותו מזהה,
 * ואולי חשבון אחר.
 */
private suspend fun connectCloudAndSave(container: AppContainer, draft: NetConnection, grant: CloudGrant): ConnectCheck {
    var refresh: String? = null
    val auth: CloudAuth = when (grant) {
        is CloudGrant.Google -> FixedToken(grant.accessToken)
        is CloudGrant.Microsoft -> try {
            val memory = object : TokenStore {
                override fun get(): String? = refresh

                override fun put(token: String) {
                    refresh = token
                }
            }
            withContext(Dispatchers.IO) { CloudAuths.microsoft(memory).also { it.exchange(grant.code, grant.redirectUri, grant.pkce) } }
        } catch (e: java.io.IOException) {
            return ConnectCheck.Failed(e.message ?: "ההתחברות ל-Microsoft נכשלה")
        }
    }
    val checked = RemoteFiles.testCloud(draft, auth)
    if (checked !is ConnectCheck.Ok) return checked
    val saved = checked.connection
    refresh?.let { token ->
        try {
            withContext(Dispatchers.IO) { container.cloud.tokens.store(saved.id).put(token) }
        } catch (e: Exception) {
            return ConnectCheck.Failed("לא ניתן לשמור את ההרשאה ב-Keystore: ${e.message}")
        }
    }
    container.settings.update { s ->
        val exists = s.connections.any { it.id == saved.id }
        s.copy(connections = if (exists) s.connections.map { if (it.id == saved.id) saved else it } else s.connections + saved)
    }
    container.settings.state.first { s -> saved in s.connections }
    container.remote.forget(saved.id)
    return ConnectCheck.Ok(saved)
}

/**
 * "בדוק והתחבר": בדיקה מול השרת, ורק אם עבדה — הצפנה ב-Keystore ושמירה, ומחכה שהחיבור כבר בהעדפות,
 * כדי שהרשימה שנפתחת אחריו תמצא אותו. שדה ריק בעריכה משאיר את מה שבטיוטה ([NetConnection.secret],
 * [NetConnection.keySecret] — המסך משאיר אותם רק כשהם עדיין תקפים); [password] ו-[key] — מה שהוזן עכשיו.
 */
private suspend fun connectAndSave(container: AppContainer, draft: NetConnection, password: CharArray?, key: CharArray?): ConnectCheck {
    var keptPassword: CharArray? = null
    var keptKey: CharArray? = null
    try {
        try {
            if (password == null && draft.user.isNotBlank()) keptPassword = draft.secret?.let(ConnectionSecrets::decrypt)
            if (key == null) keptKey = draft.keySecret?.let(ConnectionSecrets::decrypt)
        } catch (e: java.io.IOException) {
            return ConnectCheck.Failed(e.message ?: "הפרטים השמורים לא נקראים")
        }
        val checked = RemoteFiles.test(draft, password ?: keptPassword, key ?: keptKey)
        if (checked !is ConnectCheck.Ok) return checked
        val resolved = checked.connection
        val secret = when {
            resolved.user.isBlank() -> null
            password != null -> try {
                ConnectionSecrets.encrypt(password)
            } catch (e: Exception) {
                return ConnectCheck.Failed("לא ניתן לשמור את הסיסמה ב-Keystore: ${e.message}")
            }
            else -> draft.secret
        }
        val keySecret = if (key != null) {
            try {
                ConnectionSecrets.encrypt(key)
            } catch (e: Exception) {
                return ConnectCheck.Failed("לא ניתן לשמור את המפתח ב-Keystore: ${e.message}")
            }
        } else {
            draft.keySecret
        }
        val saved = resolved.copy(secret = secret, keySecret = keySecret)
        container.settings.update { s ->
            val exists = s.connections.any { it.id == saved.id }
            s.copy(connections = if (exists) s.connections.map { if (it.id == saved.id) saved else it } else s.connections + saved)
        }
        container.settings.state.first { s -> saved in s.connections }
        container.remote.forget(saved.id)
        return ConnectCheck.Ok(saved)
    } finally {
        keptPassword?.fill('\u0000')
        keptKey?.fill('\u0000')
    }
}
