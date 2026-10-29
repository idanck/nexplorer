package com.idanck.nexplorer.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.data.files.MediaCollection
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteSpace
import com.idanck.nexplorer.ui.browser.AmberBadge
import com.idanck.nexplorer.ui.components.DrawerBanner
import androidx.compose.foundation.layout.heightIn
import com.idanck.nexplorer.ui.components.segment
import com.idanck.nexplorer.ui.components.SegmentedGroup
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.net.icon

class DrawerModel(
    val volumes: List<StorageVolumeInfo>,
    val activeVolumePath: String?,
    /** מקומות ברשת (D3), אחרי האחסונים של המכשיר. */
    val connections: List<NetConnection>,
    val activeConnectionId: String?,
    /** מקום פנוי שהשרת דיווח (לפי מזהה החיבור) — פס תפוסה כמו באחסון מקומי. */
    val spaces: Map<String, RemoteSpace>,
    val onConnection: (NetConnection) -> Unit,
    val onEditConnection: (NetConnection) -> Unit,
    val onRemoveConnection: (NetConnection) -> Unit,
    val activeCollection: MediaCollection?,
    val favorites: List<String>,
    val activeTransfers: Int,
    val onVolume: (StorageVolumeInfo) -> Unit,
    val onFavorite: (String) -> Unit,
    val onRemoveFavorite: (String) -> Unit,
    /** "סדר" בכותרת המועדפים: הסדר החדש (ובלי מה שהוסר). */
    val onSetFavorites: (List<String>) -> Unit,
    val onCollection: (MediaCollection) -> Unit,
    val onDownloads: () -> Unit,
    val onAddPlace: () -> Unit,
    val onTransfers: () -> Unit,
    val onSettings: () -> Unit,
    val onAnalyzer: () -> Unit,
)

/** מגירת המקומות (לוח "מגירת מקומות"): באנר, מקומות, מועדפים, אוספים, כלים. */
@Composable
fun PlacesDrawer(model: DrawerModel) {
    ModalDrawerSheet(
        drawerShape = NexplorerShape.Drawer,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.width(NexplorerDimens.DrawerWidth),
    ) {
        DrawerBanner(onSettings = model.onSettings, onMore = model.onSettings)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = NexplorerDimens.Space3, end = NexplorerDimens.Space3, top = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2, bottom = NexplorerDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1),
        ) {
            DrawerSection("מקומות")
            // המקומות כרטיסים צמודים (1.9): הפעיל ב-primaryContainer, השאר במשטח.
            SegmentedGroup {
                model.volumes.forEach { volume -> PlaceRow(volume, active = volume.path == model.activeVolumePath) { model.onVolume(volume) } }
                model.connections.forEach { connection ->
                    ConnectionRow(
                        connection = connection,
                        space = model.spaces[connection.id],
                        active = connection.id == model.activeConnectionId,
                        onClick = { model.onConnection(connection) },
                        onEdit = { model.onEditConnection(connection) },
                        onRemove = { model.onRemoveConnection(connection) },
                    )
                }
            }
            AddPlaceRow(model.onAddPlace)

            var managingFavorites by remember { mutableStateOf(false) }
            DrawerSection("מועדפים") {
                if (model.favorites.isNotEmpty()) {
                    TextButton(onClick = { managingFavorites = true }) { Text("סדר", style = MaterialTheme.typography.labelMedium) }
                }
            }
            Favorites(model)
            if (managingFavorites) {
                FavoritesDialog(
                    favorites = model.favorites,
                    connections = model.connections,
                    onDone = { paths ->
                        managingFavorites = false
                        if (paths != model.favorites) model.onSetFavorites(paths)
                    },
                    onDismiss = { managingFavorites = false },
                )
            }

            DrawerSection("אוספים")
            Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2), modifier = Modifier.padding(horizontal = NexplorerDimens.Space1)) {
                CollectionTile("תמונות", Icons.Outlined.Image, model.activeCollection == MediaCollection.Images, Modifier.weight(1f)) {
                    model.onCollection(MediaCollection.Images)
                }
                CollectionTile("וידאו", Icons.Outlined.VideoLibrary, model.activeCollection == MediaCollection.Video, Modifier.weight(1f)) {
                    model.onCollection(MediaCollection.Video)
                }
                CollectionTile("מוזיקה", Icons.Outlined.MusicNote, model.activeCollection == MediaCollection.Audio, Modifier.weight(1f)) {
                    model.onCollection(MediaCollection.Audio)
                }
                CollectionTile("הורדות", Icons.Outlined.Download, false, Modifier.weight(1f), model.onDownloads)
            }

            DrawerSection("כלים")
            SegmentedGroup {
                ToolRow("העברות", Icons.Outlined.SwapVert, model.onTransfers) {
                    if (model.activeTransfers > 0) AmberBadge("${model.activeTransfers} פעילות")
                }
                ToolRow("מנתח אחסון", Icons.Outlined.DonutLarge, model.onAnalyzer)
                ToolRow("הגדרות", Icons.Outlined.Settings, model.onSettings)
            }
        }
    }
}

@Composable
private fun DrawerSection(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = NexplorerDimens.Space2, end = NexplorerDimens.Space2, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        )
        action?.invoke()
    }
}

/** "סדר" מועדפים: הזזה למעלה ולמטה והסרה; נשמר ב"סיום". */
@Composable
private fun FavoritesDialog(favorites: List<String>, connections: List<NetConnection>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf(favorites) }
    fun move(from: Int, to: Int) {
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מועדפים") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) {
                    Text("אין מועדפים", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items.forEachIndexed { index, path ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(favoriteLabel(path, connections), style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                displayPath(path, connections),
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "הזז למעלה")
                        }
                        IconButton(onClick = { move(index, index + 1) }, enabled = index < items.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "הזז למטה")
                        }
                        IconButton(onClick = { items = items - path }) {
                            Icon(Icons.Filled.Close, contentDescription = "הסר מהמועדפים")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(items) }) { Text("סיום") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun PlaceRow(volume: StorageVolumeInfo, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (active) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        modifier = Modifier
            .segment(if (active) scheme.primaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .padding(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        Box(
            Modifier
                .size(NexplorerDimens.PlaceTile)
                .clip(NexplorerShape.PlaceTile)
                .background(if (active) scheme.primary else scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    volume.isPrimary -> Icons.Outlined.PhoneAndroid
                    volume.label.contains("USB", ignoreCase = true) -> Icons.Outlined.Usb
                    else -> Icons.Outlined.SdCard
                },
                contentDescription = null,
                tint = if (active) scheme.onPrimary else scheme.onSurface,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    volume.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (volume.totalBytes > 0) {
                    Text(
                        "${SizeFormat.short(volume.usedBytes)} / ${SizeFormat.short(volume.totalBytes)}",
                        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    )
                }
            }
            if (volume.totalBytes > 0) UsageBar(volume.usedFraction, active)
        }
    }
}

@Composable
private fun UsageBar(fraction: Float, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // פס תפוסה מתמלא משמאל לימין גם ב-RTL, כמו בלוח (direction: ltr).
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(NexplorerDimens.UsageBar)
                .clip(NexplorerShape.Pill)
                .background(if (active) scheme.onPrimaryContainer.copy(alpha = 0.15f) else scheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(NexplorerDimens.UsageBar)
                    .clip(NexplorerShape.Pill)
                    .background(scheme.primary),
            )
        }
    }
}

/**
 * מקום ברשת (לוח "מגירת מקומות": "NAS · SMB" עם פס תפוסה, או "Pi · SFTP" עם המשתמש והשרת).
 * לחיצה ארוכה — "ערוך" ו"הסר".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionRow(connection: NetConnection, space: RemoteSpace?, active: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (active) scheme.onPrimaryContainer else scheme.onSurface
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .segment(if (active) scheme.primaryContainer else scheme.surface)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Box(
                Modifier
                    .size(NexplorerDimens.PlaceTile)
                    .clip(NexplorerShape.PlaceTile)
                    .background(if (active) scheme.primary else scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(connection.protocol.icon, contentDescription = null, tint = if (active) scheme.onPrimary else scheme.onSurface)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${connection.label} · ${connection.protocol.label}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (space != null) {
                        Text(
                            "${SizeFormat.short(space.used)} / ${SizeFormat.short(space.total)}",
                            style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                            color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        )
                    }
                }
                if (space != null) {
                    UsageBar(space.usedFraction, active)
                } else {
                    Text(
                        connection.subtitle,
                        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = NexplorerShape.Menu) {
            DropdownMenuItem(
                text = { Text("ערוך") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = scheme.primary) },
                onClick = { menu = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("הסר מהמגירה", color = scheme.error) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = scheme.error) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}

@Composable
private fun AddPlaceRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // כפתור טונאלי ברוחב המגירה, מתחת לקבוצת המקומות (1.9).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NexplorerDimens.Space1)
            .heightIn(min = NexplorerDimens.TouchTarget)
            .clip(NexplorerShape.Pill)
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(NexplorerDimens.Space5 - NexplorerDimens.Space1))
        Text("הוסף מקום", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Favorites(model: DrawerModel) {
    if (model.favorites.isEmpty()) {
        Text(
            "אין עדיין. בתיקייה: ⋮ ← אפשרויות תיקייה ← הוסף למועדפים",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space2),
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
        modifier = Modifier.padding(horizontal = NexplorerDimens.Space1),
    ) {
        model.favorites.forEach { path ->
            Text(
                favoriteLabel(path, model.connections),
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                maxLines = 1,
                modifier = Modifier
                    .clip(NexplorerShape.Pill)
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(onClick = { model.onFavorite(path) }, onLongClick = { model.onRemoveFavorite(path) })
                    .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space2),
            )
        }
    }
}

@Composable
private fun CollectionTile(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(NexplorerShape.FindingCard)
            .background(if (active) scheme.primaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, horizontal = NexplorerDimens.Space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) scheme.onPrimaryContainer else scheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ToolRow(label: String, icon: ImageVector, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .segment(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NexplorerDimens.Space3))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** שם המועדף: התיקייה האחרונה; בשורש של חיבור ברשת — שם החיבור. */
private fun favoriteLabel(path: String, connections: List<NetConnection>): String {
    if (!NetPaths.isRemote(path)) return path.substringAfterLast('/').ifEmpty { path }
    return NetPaths.name(path).ifEmpty { connections.firstOrNull { it.id == NetPaths.connectionId(path) }?.label ?: path }
}

/** הנתיב בשורה השנייה: ברשת — "NAS › volume2 › Photos" במקום המזהה של החיבור. */
private fun displayPath(path: String, connections: List<NetConnection>): String {
    if (!NetPaths.isRemote(path)) return path
    return connections.firstOrNull { it.id == NetPaths.connectionId(path) }?.displayPath(path) ?: path
}
