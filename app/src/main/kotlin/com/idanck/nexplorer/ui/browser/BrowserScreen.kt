package com.idanck.nexplorer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.StorageVolumes
import com.idanck.nexplorer.data.settings.AppSettings
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.data.transfer.TransferStatus
import com.idanck.nexplorer.ui.drawer.DrawerModel
import com.idanck.nexplorer.ui.drawer.PlacesDrawer
import com.idanck.nexplorer.ui.properties.PropertiesSheet
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.transfer.TransferChip
import com.idanck.nexplorer.ui.transfer.TransfersSheet
import com.idanck.nexplorer.ui.update.UpdateActions
import com.idanck.nexplorer.ui.update.UpdateBanner
import com.idanck.nexplorer.update.UpdateState
import kotlinx.coroutines.launch
import java.io.File

private sealed interface BrowserDialog {
    data object NewFolder : BrowserDialog
    data object NewFile : BrowserDialog
    data class Rename(val entry: Entry) : BrowserDialog
    data class Delete(val entries: List<Entry>) : BrowserDialog
    data object ViewOptions : BrowserDialog
    data object Clipboard : BrowserDialog
}

/**
 * המסך הראשי: מגירה (מקומות) ‹ רשימה (תוכן) ‹ כפתור צף ותפריט ⋮ (פעולות) — עיקרון 1 בשפת העיצוב.
 */
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    transfers: TransferManager,
    settings: AppSettings,
    updateState: UpdateState,
    updateActions: UpdateActions,
    onOpenSearch: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transferList by transfers.transfers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var fabOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var itemMenuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var showTransfers by remember { mutableStateOf(false) }
    var properties by remember { mutableStateOf<List<Entry>?>(null) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BrowserEvent.Message -> snackbar.showSnackbar(event.text)
                is BrowserEvent.OpenFile -> if (!FileIntents.open(context, event.entry.path, event.entry.name)) {
                    snackbar.showSnackbar("אין אפליקציה שפותחת קבצים מסוג זה")
                }
            }
        }
    }
    LaunchedEffect(drawerState.isOpen) { if (drawerState.isOpen) vm.refreshVolumes() }

    BackHandler(enabled = fabOpen || drawerState.isOpen || state.filter != null || state.inSelection || !state.isAtRoot) {
        when {
            fabOpen -> fabOpen = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            state.inSelection -> vm.clearSelection()
            state.filter != null -> vm.setFilter(null)
            else -> vm.goUp()
        }
    }

    val selected = state.entries.filter { it.path in state.selection }
    val activeTransfers = transferList.filter { it.isActive }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PlacesDrawer(
                DrawerModel(
                    volumes = state.volumes,
                    activeVolumePath = if (state.location is Location.Folder) state.volume?.path else null,
                    activeCollection = (state.location as? Location.Media)?.collection,
                    favorites = state.favorites,
                    activeTransfers = activeTransfers.size,
                    onVolume = { v -> scope.launch { drawerState.close() }; vm.navigate(Location.Folder(v.path)) },
                    onFavorite = { path -> scope.launch { drawerState.close() }; vm.openPath(path) },
                    onRemoveFavorite = { path -> vm.removeFavorite(path); message("הוסר מהמועדפים") },
                    onCollection = { c -> scope.launch { drawerState.close() }; vm.navigate(Location.Media(c)) },
                    onDownloads = {
                        scope.launch { drawerState.close() }
                        vm.openPath(File(StorageVolumes.primaryPath(), android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath)
                    },
                    onAddPlace = { message("חיבורי רשת וענן יגיעו בשלב הבא (החלטות D3, D4)") },
                    onTransfers = { scope.launch { drawerState.close() }; showTransfers = true },
                    onSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                ),
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { scaffoldPadding ->
            Box(Modifier.fillMaxSize().padding(scaffoldPadding)) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    if (state.inSelection) {
                        SelectionHeader(
                            count = state.selection.size,
                            sizeText = selected.filter { !it.isDirectory }.sumOf { it.size }.takeIf { it > 0 }?.let(SizeFormat::precise).orEmpty(),
                            onSelectAll = vm::selectAll,
                            onInvert = vm::invertSelection,
                            onClose = vm::clearSelection,
                        )
                    } else {
                        BrowserTopBar(
                            title = state.title,
                            titleIsLtr = state.titleIsLtr,
                            summary = summary(state),
                            clipboardCount = state.clipboard?.paths?.size ?: 0,
                            onMenu = { scope.launch { drawerState.open() } },
                            onSearch = { onOpenSearch(state.folderPath) },
                            onMore = { overflowOpen = true },
                            onClipboard = { dialog = BrowserDialog.Clipboard },
                            moreMenu = {
                                OverflowMenu(
                                    expanded = overflowOpen,
                                    onDismiss = { overflowOpen = false },
                                    model = overflowModel(
                                        state = state,
                                        vm = vm,
                                        activeTransfers = activeTransfers,
                                        canPin = FileIntents.canPinShortcut(context),
                                        onProperties = { state.folderPath?.let { properties = listOf(LocalFiles.entryOf(File(it))) } },
                                        onViewOptions = { dialog = BrowserDialog.ViewOptions },
                                        onPinShortcut = {
                                            state.folderPath?.let { path ->
                                                if (!FileIntents.pinFolderShortcut(context, path, state.title)) message("המשגר לא תומך בקיצורי דרך")
                                            }
                                        },
                                        onTransfers = { showTransfers = true },
                                        onSettings = onOpenSettings,
                                    ),
                                )
                            },
                        )
                    }
                    UpdateBanner(updateState, updateActions)
                    Breadcrumbs(state.crumbs) { crumb ->
                        if (crumb.path.isEmpty() || crumb == state.crumbs.lastOrNull()) vm.refresh()
                        else vm.navigate(Location.Folder(crumb.path))
                    }
                    if (state.filter != null) {
                        FilterBar(state.filter.orEmpty(), onChange = vm::setFilter, onClose = { vm.setFilter(null) })
                    }
                    Surface(
                        shape = NexplorerShape.ContentSheet,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        FileList(
                            state = state,
                            actions = FileListActions(
                                onClick = vm::open,
                                onLongClick = { vm.longPress(it.path) },
                                onIconClick = { vm.toggle(it.path) },
                            ),
                            contentPadding = PaddingValues(
                                top = NexplorerDimens.Space2,
                                bottom = if (state.clipboard != null) NexplorerDimens.Space4 else navBottom + NexplorerDimens.Fab + NexplorerDimens.FabMargin * 2,
                            ),
                        )
                    }
                    if (state.clipboard != null && !state.inSelection) {
                        PasteBar(
                            count = state.clipboard?.paths?.size ?: 0,
                            canPaste = state.location is Location.Folder,
                            onPaste = vm::paste,
                            onNewFolder = { dialog = BrowserDialog.NewFolder },
                            onCancel = vm::cancelClipboard,
                        )
                    }
                }

                if (state.inSelection) {
                    val single = selected.singleOrNull()
                    SelectionActionBar(
                        onMore = { itemMenuOpen = true },
                        onRename = single?.let { entry -> { dialog = BrowserDialog.Rename(entry) } },
                        onShare = {
                            val files = selected.filter { !it.isDirectory }
                            when {
                                files.isEmpty() -> message("אי אפשר לשתף תיקיות — בחר קבצים")
                                FileIntents.share(context, files.map { it.path to it.name }) == 0 -> message("אין אפליקציה לשיתוף")
                            }
                        },
                        onDelete = {
                            if (settings.confirmDelete) dialog = BrowserDialog.Delete(selected) else vm.delete(selected.map { it.path })
                        },
                        onCopy = { vm.copyToClipboard(selected.map { it.path }, cut = false) },
                        onCut = { vm.copyToClipboard(selected.map { it.path }, cut = true) },
                        moreMenu = {
                            ItemMenu(
                                expanded = itemMenuOpen,
                                onDismiss = { itemMenuOpen = false },
                                model = ItemMenuModel(
                                    single = single != null,
                                    isFolder = single?.isDirectory == true,
                                    isFavorite = single != null && single.path in state.favorites,
                                    canPinShortcut = FileIntents.canPinShortcut(context),
                                    onOpenWith = {
                                        single?.let { if (!FileIntents.openWith(context, it.path, it.name)) message("אין אפליקציה מתאימה") }
                                    },
                                    onMove = { vm.copyToClipboard(selected.map { it.path }, cut = true) },
                                    onCopyPath = {
                                        single?.let {
                                            clipboardManager.setText(AnnotatedString(it.path))
                                            message("הנתיב הועתק")
                                        }
                                    },
                                    onPinShortcut = {
                                        single?.let { if (!FileIntents.pinFolderShortcut(context, it.path, it.name)) message("המשגר לא תומך בקיצורי דרך") }
                                    },
                                    onToggleFavorite = { single?.let { vm.toggleFavorite(it.path) } },
                                    onProperties = { properties = selected },
                                ),
                            )
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    )
                } else if (state.clipboard == null) {
                    TransferChip(
                        activeTransfers,
                        onClick = { showTransfers = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = NexplorerDimens.FabMargin + NexplorerDimens.Space2),
                    )
                    if (state.location is Location.Folder) {
                        NewFab(
                            open = fabOpen,
                            onToggle = { fabOpen = !fabOpen },
                            onNewFolder = { dialog = BrowserDialog.NewFolder },
                            onNewFile = { dialog = BrowserDialog.NewFile },
                            onUnavailable = { what -> message("$what — יגיע בשלב הבא") },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        BrowserDialog.NewFolder -> NameDialog("תיקייה חדשה", "צור", "", onConfirm = { vm.createFolder(it); dialog = null }, onDismiss = { dialog = null })
        BrowserDialog.NewFile -> NameDialog("קובץ חדש", "צור", "", onConfirm = { vm.createFile(it); dialog = null }, onDismiss = { dialog = null })
        is BrowserDialog.Rename -> NameDialog("שינוי שם", "שנה", d.entry.name, onConfirm = { vm.rename(d.entry.path, it); dialog = null }, onDismiss = { dialog = null })
        is BrowserDialog.Delete -> ConfirmDeleteDialog(
            d.entries.map { it.name },
            onConfirm = { vm.delete(d.entries.map { it.path }); dialog = null },
            onDismiss = { dialog = null },
        )
        BrowserDialog.ViewOptions -> ViewOptionsDialog(
            initial = state.view,
            initialOnlyThisFolder = state.viewIsFolderScoped,
            folderName = state.folderPath?.let { File(it).name.ifEmpty { it } },
            onChange = { view, onlyHere -> vm.setView(view, onlyHere) },
            onDismiss = { dialog = null },
        )
        BrowserDialog.Clipboard -> state.clipboard?.let { clip ->
            ClipboardDialog(clip, onClear = vm::cancelClipboard, onDismiss = { dialog = null })
        }
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
    properties?.let { list ->
        PropertiesSheet(
            entries = list,
            onCopy = { text ->
                clipboardManager.setText(AnnotatedString(text))
                message("הועתק")
            },
            onDismiss = { properties = null },
        )
    }
}

private fun summary(state: BrowserState): String = when (state.location) {
    is Location.Media -> Counts.items(state.listed.size)
    is Location.Folder -> {
        val folders = state.listed.count { it.isDirectory }
        val files = state.listed.size - folders
        val free = state.volume?.let { " · ${SizeFormat.short(it.freeBytes)} פנויים" }.orEmpty()
        "${Counts.folders(folders)} · ${Counts.files(files)}$free"
    }
}

private fun overflowModel(
    state: BrowserState,
    vm: BrowserViewModel,
    activeTransfers: List<com.idanck.nexplorer.data.transfer.Transfer>,
    canPin: Boolean,
    onProperties: () -> Unit,
    onViewOptions: () -> Unit,
    onPinShortcut: () -> Unit,
    onTransfers: () -> Unit,
    onSettings: () -> Unit,
): OverflowMenuModel {
    val running = activeTransfers.firstOrNull { it.status == TransferStatus.Running }
    return OverflowMenuModel(
        count = state.entries.size,
        viewSummary = "${state.view.mode.label} · ${state.view.sort.key.label}",
        showSections = state.view.showSections,
        showHidden = state.view.showHidden,
        isFolder = state.location is Location.Folder,
        isFavorite = state.isFavorite,
        canPinShortcut = canPin,
        transfersLabel = if (activeTransfers.isEmpty()) null
        else (running?.let { "${(it.fraction * 100).toInt()}% · " }.orEmpty()) + activeTransfers.size,
        onSelectAll = vm::selectAll,
        onProperties = onProperties,
        onViewOptions = onViewOptions,
        onFilter = { vm.setFilter("") },
        onToggleSections = { vm.setView(state.view.copy(showSections = !state.view.showSections), state.viewIsFolderScoped) },
        onToggleHidden = { vm.setView(state.view.copy(showHidden = !state.view.showHidden), state.viewIsFolderScoped) },
        onToggleFavorite = { vm.toggleFavorite() },
        onPinShortcut = onPinShortcut,
        onTransfers = onTransfers,
        onRefresh = vm::refresh,
        onSettings = onSettings,
    )
}

/** סרגל "הדבק כאן (N)" (לוח "הדבקה"): הדבקה, תיקייה חדשה ליעד, ביטול. */
@Composable
private fun PasteBar(count: Int, canPaste: Boolean, onPaste: () -> Unit, onNewFolder: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, top = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, bottom = NexplorerDimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
        ) {
            Button(
                onClick = onPaste,
                enabled = canPaste,
                shape = NexplorerShape.Pill,
                modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton),
            ) {
                Text(if (canPaste) "הדבק כאן ($count)" else "פתח תיקייה כדי להדביק", style = MaterialTheme.typography.labelLarge)
                if (canPaste) {
                    Spacer(Modifier.width(NexplorerDimens.Space2))
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(NexplorerDimens.Space5 - NexplorerDimens.Space1))
                }
            }
            OutlinedIconButton(onClick = onNewFolder, enabled = canPaste, modifier = Modifier.size(NexplorerDimens.BottomBarButton)) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = "תיקייה חדשה")
            }
            OutlinedButton(onClick = onCancel, shape = NexplorerShape.Pill, modifier = Modifier.height(NexplorerDimens.BottomBarButton)) {
                Text("ביטול")
            }
        }
    }
}
