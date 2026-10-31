package com.idanck.nexplorer.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.ui.drawer.DrawerModel
import com.idanck.nexplorer.ui.net.ConnectionIcon
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import java.io.File

/** אייקון רגיל בשורה של המסילה. */
fun railIcon(vector: ImageVector): @Composable (Color) -> Unit = { tint ->
    Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
}

/**
 * מסילת המקומות של הטלוויזיה (T1 סגורה, T2 פתוחה): אחסון, הורדות, מועדפים, מקומות ברשת מהמגירה, [nearbyContent]
 * ("מכשירים קרובים"), והגדרות למטה. נפתחת (מעל התוכן, שמתעמעם) כשהפוקוס בתוכה. רוחב הסגורה כולל את שולי ה-overscan.
 */
@Composable
fun TvRail(
    model: DrawerModel,
    deviceName: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    nearbyContent: @Composable ColumnScope.(expanded: Boolean) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val colors = NexplorerTheme.colors
    val width by animateDpAsState(
        if (expanded) NexplorerDimens.TvRailExpanded else NexplorerDimens.TvOverscanHorizontal + NexplorerDimens.TvRailCollapsed,
        label = "rail",
    )
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .then(if (expanded) Modifier.clip(NexplorerShape.Drawer).background(scheme.surface) else Modifier)
            .padding(
                start = if (expanded) NexplorerDimens.Space5 - NexplorerDimens.Space1 else NexplorerDimens.TvOverscanHorizontal,
                end = if (expanded) NexplorerDimens.Space4 else 0.dp,
                top = NexplorerDimens.TvOverscanVertical,
                bottom = NexplorerDimens.TvOverscanVertical,
            ),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        // הלוגו: אריח הקופסה בגווני הבאנר; בפתוחה — גם השם ושם המכשיר.
        Row(
            Modifier.padding(bottom = NexplorerDimens.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Box(
                Modifier
                    .size(NexplorerDimens.TvRailLogo)
                    .clip(NexplorerShape.PlaceTile)
                    .background(colors.bannerGradientEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = colors.bannerTitle, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
            }
            if (expanded) {
                Column {
                    Text("nexplorer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(deviceName, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            if (expanded) RailSection("אחסון")
            model.volumes.forEach { v ->
                TvRailItem(
                    icon = railIcon(if (v.isPrimary) Icons.Outlined.PhoneAndroid else Icons.Outlined.SdCard),
                    label = v.label,
                    expanded = expanded,
                    selected = model.activeVolumePath == v.path,
                    trailing = if (v.totalBytes > 0) "${SizeFormat.short(v.usedBytes)} / ${SizeFormat.short(v.totalBytes)}".isolated() else null,
                    onClick = { model.onVolume(v) },
                )
            }
            TvRailItem(
                icon = railIcon(Icons.Outlined.Download),
                label = "הורדות",
                expanded = expanded,
                selected = false,
                onClick = model.onDownloads,
            )
            model.favorites.forEach { path ->
                TvRailItem(
                    icon = railIcon(Icons.Outlined.StarOutline),
                    label = File(path).name.ifEmpty { path },
                    expanded = expanded,
                    selected = false,
                    onClick = { model.onFavorite(path) },
                )
            }
            if (model.connections.isNotEmpty() && expanded) RailSection("ברשת")
            model.connections.forEach { c ->
                TvRailItem(
                    icon = { tint -> ConnectionIcon(c.protocol, size = NexplorerDimens.ConnectTypeIcon, tint = tint) },
                    label = c.title,
                    subtitle = c.subtitle,
                    expanded = expanded,
                    selected = model.activeConnectionId == c.id,
                    onClick = { model.onConnection(c) },
                    onLongClick = { model.onConnectionDetails(c) },
                )
            }
            nearbyContent(expanded)
        }
        Spacer(Modifier.height(NexplorerDimens.Space2))
        TvRailItem(
            icon = railIcon(Icons.Outlined.Settings),
            label = "הגדרות",
            expanded = expanded,
            selected = false,
            onClick = model.onSettings,
        )
    }
}

/** כותרת מדור במסילה הפתוחה ("אחסון", "מכשירים קרובים"). */
@Composable
fun RailSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space1),
    )
}
