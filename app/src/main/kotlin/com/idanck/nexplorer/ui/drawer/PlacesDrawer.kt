package com.idanck.nexplorer.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.data.files.MediaCollection
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.ui.browser.AmberBadge
import com.idanck.nexplorer.ui.components.DrawerBanner
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

class DrawerModel(
    val volumes: List<StorageVolumeInfo>,
    val activeVolumePath: String?,
    val activeCollection: MediaCollection?,
    val favorites: List<String>,
    val activeTransfers: Int,
    val onVolume: (StorageVolumeInfo) -> Unit,
    val onFavorite: (String) -> Unit,
    val onRemoveFavorite: (String) -> Unit,
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
            model.volumes.forEach { volume -> PlaceRow(volume, active = volume.path == model.activeVolumePath) { model.onVolume(volume) } }
            AddPlaceRow(model.onAddPlace)

            DrawerSection("מועדפים")
            Favorites(model)

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
            ToolRow("העברות", Icons.Outlined.SwapVert, model.onTransfers) {
                if (model.activeTransfers > 0) AmberBadge("${model.activeTransfers} פעילות")
            }
            ToolRow("מנתח אחסון", Icons.Outlined.DonutLarge, model.onAnalyzer)
            ToolRow("הגדרות", Icons.Outlined.Settings, model.onSettings)
        }
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NexplorerDimens.Space2, end = NexplorerDimens.Space2, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
    )
}

@Composable
private fun PlaceRow(volume: StorageVolumeInfo, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (active) scheme.onPrimaryContainer else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (active) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
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

@Composable
private fun AddPlaceRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .alpha(0.6f)
            .padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        Box(
            Modifier
                .size(NexplorerDimens.PlaceTile)
                .border(NexplorerDimens.Space1 / 2, scheme.primary, NexplorerShape.PlaceTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = scheme.primary)
        }
        Text("הוסף מקום", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.weight(1f))
        Text("בקרוב", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
                path.substringAfterLast('/').ifEmpty { path },
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                maxLines = 1,
                modifier = Modifier
                    .clip(NexplorerShape.Pill)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(NexplorerDimens.Space1 / 4, MaterialTheme.colorScheme.outline, NexplorerShape.Pill)
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
            .clip(NexplorerShape.IconTile)
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
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NexplorerDimens.Space3))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}
