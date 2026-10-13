package com.idanck.nexplorer.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.idanck.nexplorer.ui.components.DrawerBanner
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/**
 * מסך זמני שמציג את טוקני העיצוב על המכשיר — לבדיקה שהערכה נראית כמו בלוחות.
 * יוחלף במסך הראשי כשיתחיל המימוש.
 */
@Composable
fun ThemeCatalogScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CatalogTopBar() },
        floatingActionButton = {
            // FabPosition.End (ברירת מחדל) = שמאל ב-RTL, כמו בלוח.
            FloatingActionButton(
                onClick = {},
                shape = NexplorerShape.Fab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(NexplorerDimens.Fab),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "חדש")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { SectionHeader("באנר מגירה") }
            item {
                DrawerBanner(
                    onSettings = {},
                    onMore = {},
                    modifier = Modifier
                        .padding(horizontal = NexplorerDimens.Space4)
                        .width(NexplorerDimens.DrawerWidth)
                        .clip(MaterialTheme.shapes.large),
                )
            }

            item { SectionHeader("קבצים") }
            item { FileRow(Icons.Outlined.Folder, "Download", "670 פריטים · 12 באוק׳, 10:12", tile = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            item { FileRow(Icons.Outlined.Image, "IMG_20261012_103455.jpg", "3.2 MB", tile = NexplorerTheme.colors.amberContainer, tint = NexplorerTheme.colors.onAmberContainer, mono = true) }
            item { FileRow(Icons.Outlined.Description, "router-backup-388.1.cfg", "48 KB", tile = MaterialTheme.colorScheme.surfaceVariant, tint = MaterialTheme.colorScheme.onSurface, mono = true, selected = true) }

            item { SectionHeader("צבעים") }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = NexplorerDimens.Space4),
                    horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
                ) {
                    Swatch(MaterialTheme.colorScheme.primary, "אות")
                    Swatch(MaterialTheme.colorScheme.primaryContainer, "מיכל")
                    Swatch(NexplorerTheme.colors.amber, "בתנועה")
                    Swatch(MaterialTheme.colorScheme.error, "בלתי הפיך")
                    Swatch(NexplorerTheme.colors.success, "הצלחה")
                }
            }

            item { SectionHeader("טיפוגרפיה") }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = NexplorerDimens.Space4),
                    verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
                ) {
                    Text("כותרת מסך", style = MaterialTheme.typography.headlineLarge)
                    Text("כותרת סרגל", style = MaterialTheme.typography.titleLarge)
                    Text("שם קובץ ותפריטים", style = MaterialTheme.typography.bodyLarge)
                    Text("שורה משנית — פריטים, תאריך", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("/storage/emulated/0 · 3.2 MB · a4123e18", style = MonoTextStyle)
                }
            }
        }
    }
}

@Composable
private fun CatalogTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Menu, contentDescription = "פתח מגירת מקומות", tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text("קטלוג עיצוב", style = MaterialTheme.typography.titleLarge)
            Text("סלע ואות · טוקנים חיים", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {}) { Icon(Icons.Filled.Search, contentDescription = "חיפוש") }
        IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "עוד") }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun FileRow(
    icon: ImageVector,
    name: String,
    meta: String,
    tile: Color,
    tint: Color,
    mono: Boolean = false,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NexplorerDimens.RowHeight)
            .background(if (selected) NexplorerTheme.colors.selectedRow else Color.Transparent)
            .padding(horizontal = NexplorerDimens.Space4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(NexplorerDimens.IconTile)
                .clip(NexplorerShape.IconTile)
                .background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = FileNameTextStyle, maxLines = 1, modifier = Modifier.fillMaxWidth())
            Text(
                text = meta,
                style = if (mono) MonoTextStyle else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(NexplorerShape.IconTile)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, NexplorerShape.IconTile),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Preview(name = "בהיר", locale = "iw", widthDp = 390, heightDp = 844)
@Composable
private fun CatalogLightPreview() {
    NexplorerTheme(darkTheme = false) { ThemeCatalogScreen() }
}

@Preview(name = "כהה", locale = "iw", widthDp = 390, heightDp = 844)
@Composable
private fun CatalogDarkPreview() {
    NexplorerTheme(darkTheme = true) { ThemeCatalogScreen() }
}
