package com.idanck.nexplorer.ui.analyzer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.HebrewDates
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.UsageCategory
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.analysis.AnalysisProgress
import com.idanck.nexplorer.data.analysis.AnalyzedItem
import com.idanck.nexplorer.data.analysis.StorageAnalysis
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.ui.browser.ConfirmDeleteDialog
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.theme.UsageFigureTextStyle
import java.time.ZoneId

/**
 * מנתח האחסון (לוח Analyzer): כרטיס סיכום עם פס פילוח, "מה אפשר לפנות" ו"תיקיות גדולות".
 * לחיצה על ממצא פותחת רשימה לבחירה ומחיקה (דרך תור ההעברות, עם אישור באדום).
 */
@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel,
    startPath: String?,
    confirmDelete: Boolean,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(startPath) { viewModel.start(startPath) }
    val finding = state.finding
    // המצב הנוכחי, לא זה שנלכד בהרכבה: "חזרה" כפולה מהירה הייתה סוגרת את הרשימה פעמיים ולא יוצאת.
    BackHandler { if (viewModel.state.value.finding != null) viewModel.close() else onBack() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (finding == null) {
                Header(state, onBack = onBack, onVolume = viewModel::scan)
                Overview(state, onRetry = { viewModel.scan() }, onFinding = viewModel::open, onOpenFolder = onOpenFolder)
            } else {
                FindingList(viewModel, state, finding, confirmDelete)
            }
        }
    }
}

@Composable
private fun Header(state: AnalyzerState, onBack: () -> Unit, onVolume: (com.idanck.nexplorer.data.files.StorageVolumeInfo) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space3, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
        Column(Modifier.weight(1f)) {
            Text("מנתח אחסון", style = MaterialTheme.typography.titleLarge)
            val volume = state.volume
            val subtitle = when {
                volume == null -> ""
                state.scanning -> "${volume.label} · סורק…"
                state.analysis != null -> "${volume.label} · נסרק ${HebrewDates.ago(state.analysis.scannedAt, System.currentTimeMillis())}"
                else -> volume.label
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (state.volumes.size > 1 && state.volume != null) {
            Box {
                Text(
                    "${state.volume.label} ▾",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, NexplorerShape.Pill)
                        .clickable { picking = true }
                        .padding(horizontal = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space2),
                )
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }, shape = NexplorerShape.Menu) {
                    state.volumes.forEach { v ->
                        DropdownMenuItem(text = { Text(v.label) }, onClick = { picking = false; onVolume(v) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Overview(
    state: AnalyzerState,
    onRetry: () -> Unit,
    onFinding: (Finding) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        val analysis = state.analysis
        when {
            state.progress != null -> ScanningCard(state.progress)
            state.error != null -> Surface(shape = NexplorerShape.Card, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(NexplorerDimens.Space5), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("נסה שוב") }
                }
            }
            analysis != null -> {
                SummaryCard(analysis)
                SectionTitle("מה אפשר לפנות")
                FindingCard(
                    icon = Icons.Outlined.ContentCopy,
                    tileColor = NexplorerTheme.colors.amberContainer,
                    iconColor = NexplorerTheme.colors.onAmberContainer,
                    title = Finding.Duplicates.title,
                    subtitle = if (analysis.duplicates.isEmpty()) "לא נמצאו"
                    else "${analysis.duplicates.size} קבוצות · ניתן לפנות ${SizeFormat.short(analysis.duplicatesReclaimable).isolated()}",
                    onClick = { onFinding(Finding.Duplicates) }.takeIf { analysis.duplicates.isNotEmpty() },
                )
                FindingCard(
                    icon = Icons.Outlined.BarChart,
                    tileColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = Finding.Largest.title,
                    subtitle = "${analysis.largest.size} הגדולים · ${SizeFormat.short(analysis.largestTotal).isolated()} יחד",
                    onClick = { onFinding(Finding.Largest) }.takeIf { analysis.largest.isNotEmpty() },
                )
                FindingCard(
                    icon = Icons.Outlined.DeleteOutline,
                    tileColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurface,
                    title = Finding.Junk.title,
                    subtitle = "תיקיות ${"cache".isolated()}, קבצי ${".tmp".isolated()}, הורדות שבורות · ${SizeFormat.short(analysis.junkTotal).isolated()}",
                    onClick = { onFinding(Finding.Junk) }.takeIf { analysis.junk.isNotEmpty() },
                )
                if (analysis.bigFolders.isNotEmpty()) {
                    SectionTitle("תיקיות גדולות")
                    BigFolders(analysis.bigFolders, onOpenFolder)
                }
            }
        }
    }
}

@Composable
private fun ScanningCard(progress: AnalysisProgress) {
    Surface(shape = NexplorerShape.Card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(NexplorerDimens.Space5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4),
        ) {
            CircularProgressIndicator(color = NexplorerTheme.colors.amber)
            Text(
                when (progress) {
                    is AnalysisProgress.Scanning -> "סורק… ${progress.files.toString().isolated()} קבצים"
                    is AnalysisProgress.Hashing -> "בודק כפילויות… ${SizeFormat.short(progress.doneBytes).isolated()} / ${SizeFormat.short(progress.totalBytes).isolated()}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun colorOf(category: UsageCategory): Color {
    val c = NexplorerTheme.colors
    return when (category) {
        UsageCategory.Video -> c.chartVideo
        UsageCategory.Images -> c.chartImages
        UsageCategory.Apps -> c.chartApps
        UsageCategory.Documents -> c.chartDocuments
        UsageCategory.Audio -> c.chartAudio
        UsageCategory.Other -> c.chartOther
    }
}

@Composable
private fun SummaryCard(analysis: StorageAnalysis) {
    val volume = analysis.volume
    val used = volume.usedBytes
    Surface(shape = NexplorerShape.Card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(NexplorerDimens.Space4 + NexplorerDimens.Space1),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.RowGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val figure = SizeFormat.short(used)
                Text(figure.substringBefore(' '), style = UsageFigureTextStyle)
                Spacer(Modifier.width(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2))
                Text(
                    "${figure.substringAfter(' ').isolated()} בשימוש מתוך ${SizeFormat.short(volume.totalBytes).isolated()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(volume.usedFraction * 100).toInt()}%".isolated(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, NexplorerShape.Pill)
                        .padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space1),
                )
            }
            val parts = analysis.byCategory.entries.filter { it.value > 0 }.sortedBy { it.key.ordinal }
            val total = parts.sumOf { it.value }.coerceAtLeast(1)
            // כמו בלוח: הפס תמיד משמאל לימין (וידאו ראשון), גם בממשק RTL.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(NexplorerDimens.AnalyzerBar)
                        // הרווחים בין הקטעים בצבע הכרטיס, כמו בלוח.
                        .clip(NexplorerShape.Pill),
                    horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.AnalyzerBarGap),
                ) {
                    parts.forEach { (category, bytes) ->
                        Box(
                            Modifier
                                .weight((bytes.toFloat() / total).coerceAtLeast(0.005f))
                                .fillMaxHeight()
                                .background(colorOf(category)),
                        )
                    }
                }
            }
            UsageCategory.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4)) {
                    row.forEach { category ->
                        Legend(category, analysis.byCategory[category] ?: 0, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(category: UsageCategory, bytes: Long, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
        Box(
            Modifier
                .size(NexplorerDimens.LegendDot)
                .background(colorOf(category), NexplorerShape.LegendDot)
                .then(
                    if (category == UsageCategory.Other) Modifier.border(NexplorerDimens.Space1 / 4, MaterialTheme.colorScheme.outline, NexplorerShape.LegendDot)
                    else Modifier,
                ),
        )
        Text(category.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(SizeFormat.short(bytes), style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space1, top = NexplorerDimens.Space1),
    )
}

@Composable
private fun FindingCard(icon: ImageVector, tileColor: Color, iconColor: Color, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Surface(
        shape = NexplorerShape.FindingCard,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.RowGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.RowGap),
        ) {
            Box(
                Modifier.size(NexplorerDimens.IconTile).background(tileColor, NexplorerShape.IconTile),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = iconColor) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BigFolders(folders: List<AnalyzedItem>, onOpenFolder: (String) -> Unit) {
    val max = folders.maxOf { it.size }.coerceAtLeast(1)
    Surface(shape = NexplorerShape.FindingCard, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = NexplorerDimens.Space1)) {
            folders.forEach { folder ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NexplorerDimens.CompactRowHeight)
                        .clickable { onOpenFolder(folder.path) }
                        .padding(horizontal = NexplorerDimens.Space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                ) {
                    Text(folder.name, style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(
                            Modifier
                                .width(NexplorerDimens.FolderBarWidth)
                                .height(NexplorerDimens.ProgressBar)
                                .background(MaterialTheme.colorScheme.surfaceVariant, NexplorerShape.Pill),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((folder.size.toFloat() / max).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary, NexplorerShape.Pill),
                            )
                        }
                    }
                    Text(
                        SizeFormat.short(folder.size),
                        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(NexplorerDimens.FolderSizeWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FindingList(viewModel: AnalyzerViewModel, state: AnalyzerState, finding: Finding, confirmDelete: Boolean) {
    val context = LocalContext.current
    val items = viewModel.items(finding)
    val selected = items.filter { it.path in state.selection }
    var confirming by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(items) { System.currentTimeMillis() }
    val rootPath = state.volume?.path.orEmpty()

    Row(
        Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space3, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
        Column(Modifier.weight(1f)) {
            Text(finding.title, style = MaterialTheme.typography.titleLarge)
            Text(
                if (selected.isEmpty()) "לא נבחר דבר" else "${selected.size} נבחרו · ${SizeFormat.short(selected.sumOf { it.size }).isolated()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // בכפילויות "בחר הכל" = כל העותקים חוץ מהישן בכל קבוצה — אף פעם לא כולם.
        val selectable = viewModel.selectableAll(finding)
        val all = selectable.isNotEmpty() && selected.size >= selectable.size
        TextButton(onClick = { viewModel.setAll(finding, !all) }) { Text(if (all) "נקה" else "בחר הכל") }
    }

    state.message?.let { text ->
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space5, vertical = NexplorerDimens.Space1),
        )
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        if (finding == Finding.Duplicates) {
            state.analysis?.duplicates.orEmpty().forEachIndexed { index, group ->
                item(key = "g:$index") {
                    Text(
                        "קבוצה ${index + 1} · ${group.size} עותקים · ${SizeFormat.short(group.first().size).isolated()} כל אחד",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = NexplorerDimens.Space5, vertical = NexplorerDimens.Space2),
                    )
                }
                items(group, key = { "d:$index:${it.path}" }) { item ->
                    ItemRow(item, rootPath, item.path in state.selection, now, zone, onToggle = { viewModel.toggle(item.path) }) {
                        FileIntents.open(context, item.path, item.name)
                    }
                }
            }
        } else {
            items(items, key = { it.path }) { item ->
                ItemRow(item, rootPath, item.path in state.selection, now, zone, onToggle = { viewModel.toggle(item.path) }) {
                    if (!item.isDirectory) FileIntents.open(context, item.path, item.name)
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space3),
    ) {
        Button(
            onClick = { if (confirmDelete) confirming = true else viewModel.deleteSelected() },
            enabled = selected.isNotEmpty(),
            shape = NexplorerShape.Pill,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.fillMaxWidth().height(NexplorerDimens.BottomBarButton),
        ) {
            Text(
                if (selected.isEmpty()) "מחק" else "מחק ${selected.size} · ${SizeFormat.short(selected.sumOf { it.size }).isolated()}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (confirming) {
        ConfirmDeleteDialog(
            selected.map { it.name },
            onConfirm = {
                confirming = false
                viewModel.deleteSelected()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun ItemRow(item: AnalyzedItem, rootPath: String, selected: Boolean, now: Long, zone: ZoneId, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) NexplorerTheme.colors.selectedRow else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = NexplorerDimens.Space2, vertical = NexplorerDimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(item.name, style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            val parent = item.path.substringBeforeLast('/').removePrefix(rootPath).trimStart('/').ifEmpty { "/" }
            Text(
                buildString {
                    append(SizeFormat.short(item.size).isolated())
                    if (item.lastModified > 0) append(" · ").append(HebrewDates.short(item.lastModified, now, zone))
                    append(" · ").append(parent.isolated())
                },
                // שורה בעברית (תאריך) עם גודל ונתיב מבודדים — לא Mono, שהיה הופך את כולה ל-LTR.
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.isDirectory) {
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "פתח", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
