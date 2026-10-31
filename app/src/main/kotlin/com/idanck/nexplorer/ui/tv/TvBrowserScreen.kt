package com.idanck.nexplorer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.ArchiveFormats
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.MediaFormats
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.TvActionPlan
import com.idanck.nexplorer.core.TvOp
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.apk.ApkInspector
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.files.TextFiles
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.settings.AppSettings
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.ui.apk.ApkSheet
import com.idanck.nexplorer.ui.browser.BrowserEvent
import com.idanck.nexplorer.ui.browser.BrowserState
import com.idanck.nexplorer.ui.browser.BrowserViewModel
import com.idanck.nexplorer.ui.browser.ConfirmDeleteDialog
import com.idanck.nexplorer.ui.browser.DecryptDialog
import com.idanck.nexplorer.ui.browser.ExtractDialog
import com.idanck.nexplorer.ui.browser.FetchPurpose
import com.idanck.nexplorer.ui.browser.FilterBar
import com.idanck.nexplorer.ui.browser.Location
import com.idanck.nexplorer.ui.browser.NameDialog
import com.idanck.nexplorer.ui.browser.UnlockVaultDialog
import com.idanck.nexplorer.ui.browser.ViewOptionsDialog
import com.idanck.nexplorer.ui.browser.placesDrawerModel
import com.idanck.nexplorer.ui.components.FileIconTile
import com.idanck.nexplorer.ui.components.FileThumbnail
import com.idanck.nexplorer.ui.components.SelectionBox
import com.idanck.nexplorer.ui.components.deviceName
import com.idanck.nexplorer.ui.keys.KeyBus
import com.idanck.nexplorer.ui.keys.KeyCommand
import com.idanck.nexplorer.ui.net.ConnectionDetailsSheet
import com.idanck.nexplorer.ui.net.FetchDialog
import com.idanck.nexplorer.ui.properties.PropertiesSheet
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.transfer.TransfersSheet
import com.idanck.nexplorer.ui.update.UpdateActions
import com.idanck.nexplorer.ui.update.UpdateBanner
import com.idanck.nexplorer.update.UpdateState
import kotlinx.coroutines.launch

/** החלונות של מסך הטלוויזיה (חלונות המערכת של מסך הטלפון, בלי מה שלא נפתח בשלט). */
private sealed interface TvDialog {
    data object NewFolder : TvDialog
    data object NewFile : TvDialog
    data object NewMenu : TvDialog
    data object ViewOptions : TvDialog
    data class Rename(val entry: Entry) : TvDialog
    data class Delete(val entries: List<Entry>) : TvDialog
    data class Extract(val entry: Entry) : TvDialog
    data class OpenEncrypted(val entry: Entry, val inVault: Boolean) : TvDialog
    data class UnlockVault(val vault: Entry, val then: String) : TvDialog
    data class Fetch(val entries: List<Entry>, val purpose: FetchPurpose) : TvDialog
}

/** סימון "הפריט הראשון" בבקשת פוקוס אחרי טעינה. */
private const val FIRST = ""

/**
 * מסך הקבצים של Google TV (שפת העיצוב 1.15, לוחות T1–T4): מסילת מקומות שנפתחת בפוקוס, ראש עם הנתיב והכותרת,
 * רשת של כרטיסים לשלט, פאנל פעולות בהחזקת OK (או MENU), ומצב בחירה עם סרגל פעולות. אותו [BrowserViewModel]
 * של הטלפון — כל לוגיקת הקבצים משם.
 *
 * הרחבות (העברה בין מכשירים): [nearbyContent] — מדור "מכשירים קרובים" במסילה (מקבל אם המסילה פתוחה;
 * לשורות — [TvRailItem]); [extraActions] — פעולות נוספות בפאנל ובסרגל הבחירה (אחרי "גזור").
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvBrowserScreen(
    vm: BrowserViewModel,
    transfers: TransferManager,
    settings: AppSettings,
    updateState: UpdateState,
    updateActions: UpdateActions,
    keys: KeyBus,
    remote: RemoteFiles,
    onOpenSearch: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onEditText: (String) -> Unit,
    onOpenMedia: (Entry) -> Unit,
    onOpenConnect: (BrowserViewModel, NetConnection?) -> Unit,
    nearbyContent: @Composable ColumnScope.(expanded: Boolean) -> Unit = {},
    extraActions: List<TvAction> = emptyList(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transferList by transfers.transfers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val device = remember(context) { deviceName(context) }

    var dialog by remember { mutableStateOf<TvDialog?>(null) }
    var panelFor by remember { mutableStateOf<List<Entry>?>(null) }
    var properties by remember { mutableStateOf<List<Entry>?>(null) }
    var apk by remember { mutableStateOf<Entry?>(null) }
    var showTransfers by remember { mutableStateOf(false) }
    var railFocused by remember { mutableStateOf(false) }
    var focusedPath by remember { mutableStateOf<String?>(null) }
    // אחרי שהרשימה נטענת: לאיזה פריט להעביר את הפוקוס ([FIRST] — הראשון; נתיב — התיקייה שממנה עלינו).
    var focusAfterLoad by remember { mutableStateOf<String?>(FIRST) }
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val headerFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BrowserEvent.Message -> launch { snackbar.showSnackbar(event.text) }
                is BrowserEvent.RouteToRelay -> launch {
                    val result = snackbar.showSnackbar("יצאת מהרשת הביתית — ה-NAS עכשיו דרך הממסר", actionLabel = "פרטים")
                    if (result == SnackbarResult.ActionPerformed) vm.openConnectionDetails(event.connectionId)
                }
                is BrowserEvent.OpenFile -> if (MediaFormats.opensInApp(event.entry)) {
                    onOpenMedia(event.entry)
                } else if (!FileIntents.open(context, event.entry.path, event.entry.name)) {
                    if (event.entry.canEditAsText() && !NetPaths.isRemote(event.entry.path)) onEditText(event.entry.path)
                    else launch { snackbar.showSnackbar("אין אפליקציה שפותחת קבצים מסוג זה") }
                }
                is BrowserEvent.OpenArchive -> dialog = TvDialog.Extract(event.entry)
                is BrowserEvent.OpenApk -> apk = event.entry
                is BrowserEvent.OpenEncrypted -> dialog = TvDialog.OpenEncrypted(event.entry, event.inVault)
                is BrowserEvent.UnlockVault -> dialog = TvDialog.UnlockVault(event.vault, event.then)
                is BrowserEvent.Fetch -> dialog = TvDialog.Fetch(event.entries, event.purpose)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onResume()
        vm.refreshVolumes()
    }

    // כל מעבר מקום: הפוקוס לרשת כשהיא נטענת (אלא אם כבר נקבע — "למעלה" מסמן את התיקייה שממנה באנו).
    LaunchedEffect(state.location) { if (focusAfterLoad == null) focusAfterLoad = FIRST }
    LaunchedEffect(state.loading, state.entries, focusAfterLoad) {
        val target = focusAfterLoad ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        if (state.entries.isEmpty()) {
            runCatching { headerFocus.requestFocus() }
            focusAfterLoad = null
            return@LaunchedEffect
        }
        val index = state.entries.indexOfFirst { it.path == target }.takeIf { it >= 0 } ?: 0
        gridState.scrollToItem(index)
        withFrameNanos { }
        runCatching { requesters[state.entries[index].path]?.requestFocus() }
        focusAfterLoad = null
    }

    fun goUp() {
        val from = state.folderPath
        focusAfterLoad = from ?: FIRST
        if (!vm.goUp()) focusAfterLoad = null
    }

    fun confirmDelete(entries: List<Entry>) {
        if (entries.isEmpty()) return
        if (settings.confirmDelete) dialog = TvDialog.Delete(entries) else vm.delete(entries.map { it.path })
    }

    fun openWith(entry: Entry) {
        if (state.isRemote && entry.size <= 0) dialog = TvDialog.Fetch(listOf(entry), FetchPurpose.OpenWith)
        else if (!FileIntents.openWith(context, entry.path, entry.name)) message("אין אפליקציה מתאימה")
    }

    fun openPanel() {
        val targets = if (state.inSelection) vm.selectedEntries() else state.entries.filter { it.path == focusedPath }
        if (targets.isNotEmpty()) panelFor = targets
    }

    BackHandler(enabled = state.inSelection || railFocused || state.filter != null || !state.isAtRoot || state.isRemote) {
        when {
            state.inSelection -> vm.clearSelection()
            railFocused -> focusAfterLoad = focusedPath ?: FIRST
            state.filter != null -> vm.setFilter(null)
            else -> goUp()
        }
    }

    // MENU בשלט (KeyCommand.Menu) — פאנל הפעולות; ושאר הקיצורים כמו בטלפון.
    val onKey by rememberUpdatedState<(KeyCommand) -> Boolean> { command ->
        if (dialog != null || panelFor != null || properties != null || apk != null || showTransfers) return@rememberUpdatedState false
        when (command) {
            KeyCommand.Menu -> true.also { openPanel() }
            KeyCommand.SelectAll -> true.also { vm.selectAll() }
            KeyCommand.ClearSelection -> state.inSelection.also { if (it) vm.clearSelection() }
            KeyCommand.Delete -> true.also { confirmDelete(if (state.inSelection) vm.selectedEntries() else state.entries.filter { it.path == focusedPath }) }
            KeyCommand.GoUp -> true.also { goUp() }
            KeyCommand.Refresh -> true.also { vm.refresh() }
            KeyCommand.Search -> true.also { onOpenSearch(state.folderPath) }
            else -> false
        }
    }
    DisposableEffect(keys) {
        val unregister = keys.register { onKey(it) }
        onDispose { unregister() }
    }

    val activeTransfers = transferList.filter { it.isActive }
    val selected = state.entries.filter { it.path in state.selection }
    val search: () -> Unit = {
        if (state.isRemote && state.folderPath?.let(NetPaths::protocolOf) != NetProtocol.Synology) vm.setFilter("") else onOpenSearch(state.folderPath)
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = NexplorerDimens.TvOverscanHorizontal + NexplorerDimens.TvRailCollapsed + NexplorerDimens.TvContentGap,
                    end = NexplorerDimens.TvOverscanHorizontal,
                    top = NexplorerDimens.TvOverscanVertical,
                    bottom = NexplorerDimens.TvOverscanVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            if (state.inSelection) {
                SelectionPill(count = state.selection.size, size = selectedSize(selected), onSelectAll = vm::selectAll)
            } else {
                TvHeader(
                    state = state,
                    activeTransfers = activeTransfers.size,
                    firstButton = headerFocus,
                    onSearch = search,
                    onView = { dialog = TvDialog.ViewOptions },
                    onNew = { dialog = TvDialog.NewMenu },
                    onTransfers = { showTransfers = true },
                )
            }
            UpdateBanner(updateState, updateActions)
            if (state.filter != null) {
                FilterBar(state.filter.orEmpty(), onChange = vm::setFilter, onClose = { vm.setFilter(null) })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> CenterText(state.error.orEmpty())
                    state.entries.isEmpty() -> CenterText(if (state.filter.isNullOrEmpty()) "התיקייה ריקה" else "אין התאמות")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(NexplorerDimens.TvGridColumns),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.TvGridGapHorizontal),
                        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.TvGridGapVertical),
                        // מקום לטבעת ולהגדלה של הכרטיס שבפוקוס — הרשת חותכת בגבולות שלה.
                        contentPadding = PaddingValues(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space4),
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                    ) {
                        items(state.entries, key = { it.path }) { entry ->
                            val requester = remember(entry.path) { FocusRequester() }
                            DisposableEffect(entry.path) {
                                requesters[entry.path] = requester
                                onDispose { if (requesters[entry.path] === requester) requesters.remove(entry.path) }
                            }
                            TvFileCard(
                                entry = entry,
                                inSelection = state.inSelection,
                                selected = entry.path in state.selection,
                                meta = meta(entry, state),
                                modifier = Modifier.focusRequester(requester),
                                onFocused = { focusedPath = entry.path },
                                onClick = {
                                    if (entry.isDirectory && !state.inSelection) focusAfterLoad = FIRST
                                    vm.open(entry)
                                },
                                onLongClick = {
                                    focusedPath = entry.path
                                    panelFor = if (state.inSelection) vm.selectedEntries().ifEmpty { listOf(entry) } else listOf(entry)
                                },
                            )
                        }
                    }
                }
            }
            when {
                state.inSelection -> SelectionToolbar(
                    extraActions = extraActions,
                    onCopy = { vm.copyToClipboard(selected.map { it.path }, cut = false) },
                    onCut = { vm.copyToClipboard(selected.map { it.path }, cut = true) },
                    onExtra = { action -> action.onClick(selected) },
                    onDelete = { confirmDelete(selected) },
                    onCancel = vm::clearSelection,
                )
                state.clipboard != null -> PasteBar(
                    count = state.clipboard?.paths?.size ?: 0,
                    blockedReason = when {
                        state.location !is Location.Folder -> "פתח תיקייה כדי להדביק"
                        state.atShareList -> "פתח שיתוף כדי להדביק"
                        state.volume?.writable == false -> "אין הרשאת כתיבה כאן"
                        else -> null
                    },
                    onPaste = vm::paste,
                    onNewFolder = { dialog = TvDialog.NewFolder },
                    onCancel = vm::cancelClipboard,
                )
                else -> KeyHints()
            }
        }

        // המסילה פתוחה: התוכן מתעמעם מאחוריה.
        if (railFocused) Box(Modifier.fillMaxSize().background(NexplorerTheme.colors.scrim))
        TvRail(
            model = placesDrawerModel(
                state = state,
                vm = vm,
                activeTransfers = activeTransfers.size,
                // אין מגירה לסגור: מעבר מקום מחזיר את הפוקוס לרשת לבד (LaunchedEffect(state.location)).
                close = {},
                onTransfers = { showTransfers = true },
                onSettings = onOpenSettings,
                onAnalyzer = {},
                onOpenConnect = { c -> onOpenConnect(vm, c) },
                message = ::message,
            ),
            deviceName = device,
            expanded = railFocused,
            nearbyContent = nearbyContent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .onFocusChanged { railFocused = it.hasFocus }
                .focusRestorer(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = NexplorerDimens.TvOverscanVertical))
    }

    panelFor?.let { targets ->
        val single = targets.singleOrNull()
        val ops = TvActionPlan.ops(targets, state.inSelection)
        val items = buildList {
            ops.forEachIndexed { i, op ->
                val divider = i > 0 && ops[i - 1].group != op.group
                when (op) {
                    TvOp.Open -> single?.let { e ->
                        add(TvMenuItem(TvActionPlan.openLabel(e), if (MediaFormats.playable(e)) Icons.Outlined.PlayArrow else if (e.isDirectory) Icons.Outlined.FolderOpen else Icons.AutoMirrored.Outlined.OpenInNew, dividerBefore = divider) {
                            if (state.inSelection) vm.clearSelection()
                            if (e.isDirectory) focusAfterLoad = FIRST
                            vm.open(e)
                        })
                    }
                    TvOp.OpenWith -> single?.let { e -> add(TvMenuItem("פתיחה באמצעות", Icons.AutoMirrored.Outlined.OpenInNew, dividerBefore = divider) { openWith(e) }) }
                    TvOp.Select -> single?.let { e -> add(TvMenuItem("בחר כמה", Icons.Outlined.CheckBox, dividerBefore = divider) { vm.toggle(e.path) }) }
                    TvOp.Copy -> add(TvMenuItem("העתק", Icons.Outlined.ContentCopy, dividerBefore = divider) { vm.copyToClipboard(targets.map { it.path }, cut = false) })
                    TvOp.Cut -> {
                        add(TvMenuItem("גזור", Icons.Outlined.ContentCut, dividerBefore = divider) { vm.copyToClipboard(targets.map { it.path }, cut = true) })
                        extraActions.forEach { a -> add(TvMenuItem(a.label, a.icon, a.trailing, a.destructive) { a.onClick(targets) }) }
                    }
                    TvOp.Rename -> single?.let { e -> add(TvMenuItem("שנה שם", Icons.Outlined.DriveFileRenameOutline, dividerBefore = divider) { dialog = TvDialog.Rename(e) }) }
                    TvOp.Properties -> add(TvMenuItem("מאפיינים", Icons.Outlined.Info, dividerBefore = divider) { properties = targets })
                    TvOp.Delete -> add(TvMenuItem("מחק", Icons.Outlined.Delete, destructive = true, dividerBefore = divider) { confirmDelete(targets) })
                }
            }
        }
        TvActionsPanel(
            header = { PanelHeader(targets) },
            items = items,
            onDismiss = { panelFor = null },
        )
    }

    when (val d = dialog) {
        null -> Unit
        TvDialog.NewMenu -> TvActionsPanel(
            header = { Text("חדש", style = MaterialTheme.typography.titleLarge) },
            items = listOf(
                TvMenuItem("תיקייה חדשה", Icons.Outlined.CreateNewFolder) { dialog = if (state.atShareList) null.also { message("פתח שיתוף כדי ליצור בו") } else TvDialog.NewFolder },
                TvMenuItem("קובץ חדש", Icons.AutoMirrored.Outlined.NoteAdd) { dialog = if (state.atShareList) null.also { message("פתח שיתוף כדי ליצור בו") } else TvDialog.NewFile },
                TvMenuItem("חיבור רשת / ענן", Icons.Outlined.Lan, dividerBefore = true) { onOpenConnect(vm, null) },
            ),
            // TvActionsPanel קורא ל-onDismiss לפני הפעולה: הפעולה קובעת את החלון הבא.
            onDismiss = { if (dialog == TvDialog.NewMenu) dialog = null },
        )
        TvDialog.NewFolder -> NameDialog("תיקייה חדשה", "צור", "", onConfirm = { vm.createFolder(it); dialog = null }, onDismiss = { dialog = null })
        TvDialog.NewFile -> NameDialog("קובץ חדש", "צור", "", onConfirm = { vm.createFile(it); dialog = null }, onDismiss = { dialog = null })
        is TvDialog.Rename -> NameDialog("שינוי שם", "שנה", d.entry.name, onConfirm = { vm.rename(d.entry.path, it); dialog = null }, onDismiss = { dialog = null })
        is TvDialog.Delete -> ConfirmDeleteDialog(
            d.entries.map { it.name },
            onConfirm = {
                vm.delete(d.entries.map { it.path })
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        TvDialog.ViewOptions -> ViewOptionsDialog(
            initial = state.view,
            initialOnlyThisFolder = state.viewIsFolderScoped,
            folderName = state.folderPath?.let { state.title },
            onChange = { view, onlyHere -> vm.setView(view, onlyHere) },
            onDismiss = { dialog = null },
        )
        is TvDialog.Extract -> ExtractDialog(
            entry = d.entry,
            inspect = { vm.inspectArchive(d.entry.path) },
            onExtract = { intoFolder, password ->
                vm.extract(d.entry, intoFolder, password)
                dialog = null
            },
            onOpenWith = {
                if (!FileIntents.openWith(context, d.entry.path, d.entry.name)) message("אין אפליקציה מתאימה")
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is TvDialog.OpenEncrypted -> DecryptDialog(
            title = d.entry.name,
            subtitle = "קובץ מוצפן · ${SizeFormat.short(d.entry.size).isolated()}",
            titleIsFileName = true,
            onOpen = { password, onProgress -> vm.openEncrypted(d.entry, password, onProgress) },
            onDecrypt = { password ->
                vm.decrypt(listOf(d.entry.path), password)
                dialog = null
            },
            onDismiss = { dialog = null },
            passwordless = d.inVault,
        )
        is TvDialog.UnlockVault -> UnlockVaultDialog(
            name = d.vault.name,
            onUnlock = { password -> vm.unlockVault(d.vault, d.then, password) },
            onDismiss = { dialog = null },
        )
        is TvDialog.Fetch -> FetchDialog(
            entries = d.entries,
            fetch = { onProgress -> vm.fetch(d.entries, onProgress) },
            onFetched = { local ->
                when (d.purpose) {
                    FetchPurpose.Open -> local.singleOrNull()?.let { copy ->
                        when {
                            MediaFormats.opensInApp(copy) -> onOpenMedia(copy)
                            copy.extension == "apk" -> apk = copy
                            !FileIntents.open(context, copy.path, copy.name) -> message("אין אפליקציה שפותחת קבצים מסוג זה")
                        }
                    }
                    FetchPurpose.OpenWith -> local.singleOrNull()?.let { if (!FileIntents.openWith(context, it.path, it.name)) message("אין אפליקציה מתאימה") }
                    FetchPurpose.Share -> if (FileIntents.share(context, local.map { it.path to it.name }) == 0) message("אין אפליקציה לשיתוף")
                }
            },
            onDismiss = { dialog = null },
        )
    }

    if (showTransfers) {
        TransfersSheet(
            transfers = transferList,
            onPause = transfers::togglePause,
            onCancel = transfers::cancel,
            onClear = transfers::clearFinished,
            onDismiss = { showTransfers = false },
        )
    }
    apk?.let { entry ->
        ApkSheet(
            entry = entry,
            onInstall = { if (!FileIntents.open(context, entry.path, entry.name)) message("אין במכשיר מתקין חבילות") },
            onLaunch = { pkg ->
                val launch = ApkInspector.launchIntent(context, pkg)
                if (launch == null || !runCatching { context.startActivity(launch) }.isSuccess) message("לא ניתן לפתוח את האפליקציה")
            },
            onOpenWith = { if (!FileIntents.openWith(context, entry.path, entry.name)) message("אין אפליקציה מתאימה") },
            onCopy = { text ->
                clipboardManager.setText(AnnotatedString(text))
                message("הועתק")
            },
            onDismiss = { apk = null },
        )
    }
    state.connectionDetails?.let { id -> state.connections.firstOrNull { it.id == id } }?.let { connection ->
        ConnectionDetailsSheet(
            connection = connection,
            status = vm.connectionStatus(connection.id),
            checking = state.recheckingConnection,
            onEdit = {
                vm.closeConnectionDetails()
                onOpenConnect(vm, connection)
            },
            onRecheck = { vm.recheckConnection(connection) },
            onDismiss = vm::closeConnectionDetails,
        )
    }
    properties?.let { list ->
        PropertiesSheet(
            entries = list,
            remote = remote,
            connection = state.connection,
            onCopy = { text ->
                clipboardManager.setText(AnnotatedString(text))
                message("הועתק")
            },
            onDismiss = { properties = null },
        )
    }
}

/** הראש (T1): הנתיב מעל, שם התיקייה גדול, שורת הסיכום; ובצד הסוף — חיפוש, תצוגה, חדש (והעברות, כשיש). */
@Composable
private fun TvHeader(
    state: BrowserState,
    activeTransfers: Int,
    firstButton: FocusRequester,
    onSearch: () -> Unit,
    onView: () -> Unit,
    onNew: () -> Unit,
    onTransfers: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4)) {
        Column(Modifier.weight(1f)) {
            val parents = state.crumbs.dropLast(1)
            if (parents.isNotEmpty()) {
                Text(
                    parents.joinToString(" ‹ ") { if (it.isLtr) it.label.isolated() else it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (state.titleIsLtr) state.title.isolated() else state.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(summary(state), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
            if (activeTransfers > 0) {
                TvPillButton(
                    "העברות · $activeTransfers",
                    Icons.Outlined.SwapVert,
                    onTransfers,
                    height = NexplorerDimens.TvHeaderButton,
                    container = NexplorerTheme.colors.amberContainer,
                )
            }
            TvPillButton("חיפוש", Icons.Outlined.Search, onSearch, Modifier.focusRequester(firstButton), height = NexplorerDimens.TvHeaderButton)
            TvPillButton("תצוגה", Icons.Outlined.GridView, onView, height = NexplorerDimens.TvHeaderButton)
            TvPillButton("חדש", Icons.Outlined.Add, onNew, height = NexplorerDimens.TvHeaderButton, enabled = state.location is Location.Folder)
        }
    }
}

/** כרטיס ברשת (T1): תמונה ממוזערת או אריח הסוג, השם והגודל; בפוקוס — הגדלה וטבעת; בבחירה — תיבת סימון ומסגרת. */
@Composable
private fun TvFileCard(
    entry: Entry,
    inSelection: Boolean,
    selected: Boolean,
    meta: String,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) NexplorerDimens.TvFocusScale else 1f
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(NexplorerDimens.TvCardThumbHeight)
                .tvFocusRing(focused, scheme.onSurface, NexplorerDimens.Space4)
                .clip(NexplorerShape.IconTile)
                .then(if (selected) Modifier.border(NexplorerDimens.TvFocusRing, scheme.primary, NexplorerShape.IconTile) else Modifier),
        ) {
            FileThumbnail(entry, Modifier.fillMaxSize())
            if (inSelection) {
                SelectionBox(selected, Modifier.align(Alignment.TopStart).padding(NexplorerDimens.Space2))
            }
        }
        Text(
            entry.name,
            style = FileNameTextStyle.copy(fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = if (entry.isDirectory) MaterialTheme.typography.bodySmall else MonoTextStyle,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** כותרת הבחירה (T4): "נבחרו N · גודל", ו"בחר הכל". */
@Composable
private fun SelectionPill(count: Int, size: String, onSelectAll: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(NexplorerDimens.TvSelectionPill)
            .clip(NexplorerShape.Pill)
            .background(scheme.primaryContainer)
            .padding(start = NexplorerDimens.Space5 - NexplorerDimens.Space1, end = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        Icon(Icons.Outlined.CheckBox, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon - NexplorerDimens.Space1 / 2))
        Text(Counts.selected(count), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        if (size.isNotEmpty()) Text("· ${size.isolated()}", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
        TvPillButton("בחר הכל", null, onSelectAll, height = NexplorerDimens.TvHeaderButton - NexplorerDimens.Space1, container = scheme.primaryContainer)
    }
}

/** סרגל הפעולות של הבחירה (T4): כפתורים עם תוויות; הפעולות הנוספות (שלח למכשיר) בולטות. */
@Composable
private fun ColumnScope.SelectionToolbar(
    extraActions: List<TvAction>,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onExtra: (TvAction) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .align(Alignment.CenterHorizontally)
            .clip(NexplorerShape.Pill)
            .background(scheme.surfaceVariant)
            .padding(NexplorerDimens.Space2),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
    ) {
        TvPillButton("העתק", Icons.Outlined.ContentCopy, onCopy, container = scheme.surfaceVariant)
        TvPillButton("גזור", Icons.Outlined.ContentCut, onCut, container = scheme.surfaceVariant)
        extraActions.forEach { a -> TvPillButton(a.label, a.icon, { onExtra(a) }, primary = true) }
        TvPillButton("מחק", Icons.Outlined.Delete, onDelete, destructive = true, container = scheme.surfaceVariant)
        TvPillButton("בטל", Icons.Outlined.Close, onCancel, container = scheme.surfaceVariant)
    }
    Text(
        "OK מסמן ומבטל · ↓ לסרגל הפעולות",
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "הדבק כאן (N)" לשלט: הדבקה, תיקייה חדשה ליעד, ביטול. */
@Composable
private fun ColumnScope.PasteBar(count: Int, blockedReason: String?, onPaste: () -> Unit, onNewFolder: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        TvPillButton(blockedReason ?: "הדבק כאן ($count)", Icons.Outlined.ContentPaste, onPaste, primary = blockedReason == null, enabled = blockedReason == null)
        TvPillButton("תיקייה חדשה", Icons.Outlined.CreateNewFolder, onNewFolder, enabled = blockedReason == null)
        TvPillButton("ביטול", Icons.Outlined.Close, onCancel)
    }
}

/** רמזי המקשים בתחתית (T1). */
@Composable
private fun KeyHints() {
    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space5 - NexplorerDimens.Space1 / 2), verticalAlignment = Alignment.CenterVertically) {
        TvKeyHint("OK", "פתיחה")
        TvKeyHint("OK ארוך", "פעולות")
        TvKeyHint("→", "מקומות")
        TvKeyHint("חזור", "תיקייה למעלה")
    }
}

/** כותרת פאנל הפעולות: הקובץ (אריח, שם, גודל) או הבחירה. */
@Composable
private fun PanelHeader(targets: List<Entry>) {
    val single = targets.singleOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
        if (single != null) FileIconTile(single, size = NexplorerDimens.TvRailLogo)
        Column {
            Text(
                single?.name ?: Counts.selected(targets.size),
                style = if (single != null) FileNameTextStyle.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val size = selectedSize(targets)
            if (size.isNotEmpty()) Text(size.isolated(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenterText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
}

private fun meta(entry: Entry, state: BrowserState): String = when {
    entry.isDirectory -> if (entry.childCount >= 0) Counts.items(entry.childCount) else state.folderSizes[entry.path]?.let(SizeFormat::short).orEmpty()
    else -> SizeFormat.short(entry.size)
}

private fun selectedSize(selected: List<Entry>): String =
    selected.filter { !it.isDirectory }.sumOf { it.size }.takeIf { it > 0 }?.let(SizeFormat::short).orEmpty()

private fun summary(state: BrowserState): String = when (state.location) {
    is Location.Media -> Counts.items(state.listed.size)
    is Location.Folder -> if (state.atShareList) Counts.items(state.listed.size) else {
        val folders = state.listed.count { it.isDirectory }
        val files = state.listed.size - folders
        val freeBytes = if (state.isRemote) state.remoteSpace?.free else state.volume?.freeBytes
        val free = freeBytes?.let { " · ${SizeFormat.short(it).isolated()} פנויים" }.orEmpty()
        "${Counts.folders(folders)} · ${Counts.files(files)}$free"
    }
}

/** כמו בטלפון: קובץ טקסט/קוד או קטן — לעורך המובנה כשאין אפליקציה; לא קובץ מוצפן ולא ארכיון. */
private fun Entry.canEditAsText(): Boolean =
    !isDirectory && size <= TextFiles.MAX_BYTES && (kind == FileKind.Text || kind == FileKind.Other) &&
        !EncryptedFiles.hasEncryptedName(name) && ArchiveFormats.of(name) == null
