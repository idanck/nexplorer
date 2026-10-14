package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.settings.AppSettings
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.ui.drawer.PlacesDrawer
import com.idanck.nexplorer.ui.keys.KeyBus
import com.idanck.nexplorer.ui.keys.KeyCommand
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.transfer.TransfersSheet
import com.idanck.nexplorer.ui.update.UpdateActions
import com.idanck.nexplorer.ui.update.UpdateBanner
import com.idanck.nexplorer.update.UpdateState
import kotlinx.coroutines.launch

/**
 * שני חלונות (לוח Tablet): מסילה בצד ההתחלה, ושני חלונות קבצים — הפעיל במסגרת כחולה.
 * כל חלון הוא מסך הקבצים הרגיל ([BrowserScreen] עם [PaneConfig]); המגירה והמסילה פועלות על החלון הפעיל.
 */
@Composable
fun DualPaneScreen(
    panes: List<BrowserViewModel>,
    transfers: TransferManager,
    settings: AppSettings,
    updateState: UpdateState,
    updateActions: UpdateActions,
    keys: KeyBus,
    onOpenSearch: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAnalyzer: (String?) -> Unit,
    onEditText: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    require(panes.size == 2)
    var active by rememberSaveable { mutableIntStateOf(0) }
    val first by panes[0].state.collectAsStateWithLifecycle()
    val second by panes[1].state.collectAsStateWithLifecycle()
    val states = listOf(first, second)
    val transferList by transfers.transfers.collectAsStateWithLifecycle()
    val activeTransfers = transferList.filter { it.isActive }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showTransfers by rememberSaveable { mutableStateOf(false) }

    // בפעם הראשונה החלון השני נפתח באותה תיקייה — משם בוחרים לאן להעתיק.
    LaunchedEffect(Unit) {
        if (!panes[1].pairedOnce) {
            panes[1].pairedOnce = true
            first.folderPath?.let { panes[1].navigate(Location.Folder(it)) }
        }
    }

    DisposableEffect(keys) {
        val unregister = keys.register { command ->
            if (command == KeyCommand.SwitchPane) {
                active = 1 - active
                true
            } else {
                false
            }
        }
        onDispose { unregister() }
    }

    // הגרירה מתבצעת ע"י החלון שממנו גררו: הוא שולח ומנקה את הבחירה שלו.
    val onDrop: (DragPayload, String) -> Unit = { payload, folder ->
        panes[payload.pane].sendTo(payload.paths, folder, move = false)
    }

    val activeState = states[active]
    val activeVm = panes[active]
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PlacesDrawer(
                placesDrawerModel(
                    state = activeState,
                    vm = activeVm,
                    activeTransfers = activeTransfers.size,
                    close = { scope.launch { drawerState.close() } },
                    onTransfers = { showTransfers = true },
                    onSettings = onOpenSettings,
                    onAnalyzer = onOpenAnalyzer,
                    message = activeVm::emit,
                ),
            )
        },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            PaneRail(
                volumes = activeState.volumes,
                activeVolumePath = if (activeState.location is Location.Folder) activeState.volume?.path else null,
                transfers = activeTransfers.size,
                onMenu = { scope.launch { drawerState.open() } },
                onVolume = { volume -> activeVm.navigate(Location.Folder(volume.path)) },
                onTransfers = { showTransfers = true },
                onSettings = onOpenSettings,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.weight(1f)) {
                UpdateBanner(updateState, updateActions)
                Row(
                    Modifier
                        .weight(1f)
                        .padding(NexplorerDimens.Space4),
                    horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.PaneGap),
                ) {
                    panes.forEachIndexed { index, vm ->
                        val isActive = index == active
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(NexplorerShape.Pane)
                                .border(
                                    NexplorerDimens.PaneBorder,
                                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    NexplorerShape.Pane,
                                )
                                // נגיעה בחלון הופכת אותו לפעיל, בלי לצרוך אותה — הלחיצה עצמה ממשיכה לשורה.
                                .pointerInput(index) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        active = index
                                    }
                                }
                                // selected: קורא המסך (והבדיקות) יודעים איזה חלון פעיל — המסגרת לבדה לא נקראת.
                                .semantics {
                                    contentDescription = if (index == 0) "חלון ראשון" else "חלון שני"
                                    selected = isActive
                                },
                        ) {
                            BrowserScreen(
                                vm = vm,
                                transfers = transfers,
                                settings = settings,
                                updateState = updateState,
                                updateActions = updateActions,
                                onOpenSearch = onOpenSearch,
                                onOpenSettings = onOpenSettings,
                                onOpenAnalyzer = onOpenAnalyzer,
                                onEditText = onEditText,
                                onOpenImage = onOpenImage,
                                keys = keys,
                                pane = PaneConfig(
                                    index = index,
                                    active = isActive,
                                    otherFolder = states[1 - index].folderPath,
                                    onDrop = onDrop,
                                ),
                            )
                        }
                    }
                }
            }
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
}

/** מסילת הניווט: ☰ (המגירה), אחסונים, ובתחתית העברות והגדרות. */
@Composable
private fun PaneRail(
    volumes: List<StorageVolumeInfo>,
    activeVolumePath: String?,
    transfers: Int,
    onMenu: () -> Unit,
    onVolume: (StorageVolumeInfo) -> Unit,
    onTransfers: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(NexplorerDimens.RailWidth)
            .fillMaxHeight()
            .padding(vertical = NexplorerDimens.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.RailGap),
    ) {
        RailButton(Icons.Filled.Menu, "פתח מגירת מקומות", tint = MaterialTheme.colorScheme.primary, onClick = onMenu)
        volumes.forEach { volume ->
            RailVolume(volume, selected = volume.path == activeVolumePath, onClick = { onVolume(volume) })
        }
        Spacer(Modifier.weight(1f))
        RailTransfers(count = transfers, onClick = onTransfers)
        RailButton(Icons.Outlined.Settings, "הגדרות", tint = MaterialTheme.colorScheme.onSurface, onClick = onSettings)
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = NexplorerShape.RailItem,
        color = Color.Transparent,
        contentColor = tint,
        modifier = Modifier
            .size(NexplorerDimens.RailItem)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(NexplorerDimens.RailItemIcon))
        }
    }
}

@Composable
private fun RailVolume(volume: StorageVolumeInfo, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = NexplorerShape.RailItem,
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier
            .width(NexplorerDimens.RailItemLabeledWidth)
            .height(NexplorerDimens.RailItem),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 - NexplorerDimens.Space1 / 4, Alignment.CenterVertically),
        ) {
            Icon(
                when {
                    volume.isPrimary -> Icons.Outlined.PhoneAndroid
                    volume.label.contains("USB", ignoreCase = true) -> Icons.Outlined.Usb
                    else -> Icons.Outlined.SdCard
                },
                contentDescription = null,
                modifier = Modifier.size(NexplorerDimens.RailItemIcon),
            )
            Text(
                if (volume.isPrimary) "פנימי" else volume.label,
                style = MaterialTheme.typography.labelSmall.let { if (selected) it.copy(fontWeight = FontWeight.SemiBold) else it },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** העברות: בזמן העברה — ענבר עם תג ספירה (כמו בלוח); אחרת אייקון רגיל. */
@Composable
private fun RailTransfers(count: Int, onClick: () -> Unit) {
    if (count == 0) {
        RailButton(Icons.Outlined.SwapVert, "העברות", tint = MaterialTheme.colorScheme.onSurface, onClick = onClick)
        return
    }
    val colors = NexplorerTheme.colors
    Box {
        Surface(
            onClick = onClick,
            shape = NexplorerShape.RailItem,
            color = colors.amberContainer,
            contentColor = colors.onAmberContainer,
            modifier = Modifier
                .size(NexplorerDimens.RailItem)
                .semantics { contentDescription = "העברות: $count" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(NexplorerDimens.RailItemIcon))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2)
                .size(NexplorerDimens.RailBadge)
                .background(colors.amber, NexplorerShape.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.surface,
                maxLines = 1,
            )
        }
    }
}
