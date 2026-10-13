package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.Section
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.ViewMode
import com.idanck.nexplorer.ui.components.FileIconTile
import com.idanck.nexplorer.ui.components.FileThumbnail
import com.idanck.nexplorer.ui.components.SectionHeader
import com.idanck.nexplorer.ui.components.SelectionBox
import com.idanck.nexplorer.ui.components.metaLine
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/** מה שהשורות צריכות כדי להגיב — אחיד לכל מצבי התצוגה. */
class FileListActions(
    val onClick: (Entry) -> Unit,
    val onLongClick: (Entry) -> Unit,
    /** הקשה על האריח בוחרת (F3), גם כשלא במצב בחירה. */
    val onIconClick: (Entry) -> Unit,
    /** מיקום הגלילה האחרון בתיקייה (אינדקס, היסט) — כניסה לתיקייה מתחילה בראש, "למעלה" חוזר לשורה. */
    val scrollFor: (Location) -> Pair<Int, Int>,
    val onScrolled: (Location, Int, Int) -> Unit,
)

@Composable
fun FileList(
    state: BrowserState,
    actions: FileListActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val view = state.view
    when {
        state.error != null -> EmptyMessage(Icons.Outlined.ErrorOutline, state.error, modifier)
        state.loading && state.entries.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.entries.isEmpty() -> EmptyMessage(
            Icons.Outlined.FolderOpen,
            if (!state.filter.isNullOrBlank()) "אין התאמות ל-\"${state.filter}\"" else "התיקייה ריקה",
            modifier,
        )
        view.mode == ViewMode.List || view.mode == ViewMode.Compact -> FileColumn(state, actions, contentPadding, modifier)
        else -> FileGrid(state, actions, contentPadding, modifier)
    }
}

private fun sortLabel(state: BrowserState): String =
    "מיון: ${state.view.sort.key.label} ${if (state.view.sort.descending) "↓" else "↑"}"

/** המדורים כפי שיוצגו: עם כותרות כש"הצג מדורים" פעיל, ובלעדיהן מדור אחד בלי כותרת. */
private fun groups(state: BrowserState): List<Section> =
    if (state.view.showSections && state.sections.isNotEmpty()) state.sections else listOf(Section("", state.entries))

@Composable
private fun FileColumn(state: BrowserState, actions: FileListActions, contentPadding: PaddingValues, modifier: Modifier) {
    val now = remember(state.entries) { System.currentTimeMillis() }
    val compact = state.view.mode == ViewMode.Compact
    val listState = rememberSaveable(state.location, saver = LazyListState.Saver) {
        val (index, offset) = actions.scrollFor(state.location)
        LazyListState(index, offset)
    }
    LaunchedEffect(listState, state.location) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> actions.onScrolled(state.location, index, offset) }
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        groups(state).forEachIndexed { index, section ->
            header(section, index, sortLabel(state))
            items(section.entries, key = { it.path }) { entry ->
                if (compact) {
                    CompactRow(entry, state, actions)
                } else {
                    FileRow(entry, state, actions, now)
                }
            }
        }
    }
}

private fun LazyListScope.header(section: Section, index: Int, sort: String) {
    if (section.title.isEmpty() && index > 0) return
    item(key = "h:$index:${section.title}", contentType = "header") {
        SectionHeader(section.title, trailing = if (index == 0) sort else null, first = index == 0)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.entryClicks(entry: Entry, actions: FileListActions): Modifier =
    combinedClickable(onClick = { actions.onClick(entry) }, onLongClick = { actions.onLongClick(entry) })

@Composable
private fun FileRow(entry: Entry, state: BrowserState, actions: FileListActions, now: Long) {
    val selected = entry.path in state.selection
    val density = state.view.density
    val vertical = when (density) {
        0 -> NexplorerDimens.Space3
        2 -> NexplorerDimens.Space1
        else -> NexplorerDimens.RowPaddingVertical
    }
    val minHeight = when (density) {
        0 -> NexplorerDimens.RowHeight + NexplorerDimens.Space2
        2 -> NexplorerDimens.RowHeight - NexplorerDimens.Space2
        else -> NexplorerDimens.RowHeight
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(if (selected) NexplorerTheme.colors.selectedRow else Color.Transparent)
            .entryClicks(entry, actions)
            .padding(horizontal = NexplorerDimens.Space4, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIconTile(
            entry,
            modifier = Modifier
                .clip(NexplorerShape.IconTile)
                .clickable(onClickLabel = "בחר", role = Role.Checkbox) { actions.onIconClick(entry) },
        )
        Spacer(Modifier.width(NexplorerDimens.RowGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 / 2)) {
            Text(
                entry.name,
                style = FileNameTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                metaLine(entry, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.inSelection) {
            Spacer(Modifier.width(NexplorerDimens.Space3))
            SelectionBox(selected)
        }
    }
}

@Composable
private fun CompactRow(entry: Entry, state: BrowserState, actions: FileListActions) {
    val selected = entry.path in state.selection
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NexplorerDimens.CompactRowHeight)
            .background(if (selected) NexplorerTheme.colors.selectedRow else Color.Transparent)
            .entryClicks(entry, actions)
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIconTile(
            entry,
            size = NexplorerDimens.CompactIconTile,
            modifier = Modifier
                .clip(NexplorerShape.IconTile)
                .clickable(onClickLabel = "בחר", role = Role.Checkbox) { actions.onIconClick(entry) },
        )
        Spacer(Modifier.width(NexplorerDimens.Space3))
        Text(
            entry.name,
            style = FileNameTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(NexplorerDimens.Space2))
        Text(
            if (entry.isDirectory) (entry.childCount.takeIf { it >= 0 }?.toString() ?: "") else SizeFormat.short(entry.size),
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.inSelection) {
            Spacer(Modifier.width(NexplorerDimens.Space3))
            SelectionBox(selected)
        }
    }
}

@Composable
private fun FileGrid(state: BrowserState, actions: FileListActions, contentPadding: PaddingValues, modifier: Modifier) {
    val gallery = state.view.mode == ViewMode.Gallery
    val columns = when (state.view.density) {
        0 -> 2
        2 -> 4
        else -> 3
    }
    val gap = if (gallery) NexplorerDimens.Space1 else NexplorerDimens.Space2
    val gridState = rememberSaveable(state.location, saver = LazyGridState.Saver) {
        val (index, offset) = actions.scrollFor(state.location)
        LazyGridState(index, offset)
    }
    LaunchedEffect(gridState, state.location) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> actions.onScrolled(state.location, index, offset) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NexplorerDimens.Space3,
            end = NexplorerDimens.Space3,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        groups(state).forEachIndexed { index, section ->
            gridHeader(section, index, sortLabel(state))
            items(section.entries, key = { it.path }) { entry ->
                if (gallery) GalleryTile(entry, state, actions) else GridTile(entry, state, actions)
            }
        }
    }
}

private fun LazyGridScope.gridHeader(section: Section, index: Int, sort: String) {
    if (section.title.isEmpty() && index > 0) return
    item(key = "h:$index:${section.title}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        SectionHeader(section.title, trailing = if (index == 0) sort else null, first = index == 0)
    }
}

@Composable
private fun GridTile(entry: Entry, state: BrowserState, actions: FileListActions) {
    val selected = entry.path in state.selection
    Column(
        modifier = Modifier
            .clip(NexplorerShape.IconTile)
            .background(if (selected) NexplorerTheme.colors.selectedRow else Color.Transparent)
            .entryClicks(entry, actions)
            .padding(NexplorerDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        Box {
            if (entry.kind == FileKind.Image || entry.kind == FileKind.Video) {
                FileThumbnail(
                    entry,
                    Modifier
                        .size(NexplorerDimens.GridIconTile)
                        .clip(NexplorerShape.IconTile)
                        .clickable(onClickLabel = "בחר", role = Role.Checkbox) { actions.onIconClick(entry) },
                )
            } else {
                FileIconTile(
                    entry,
                    size = NexplorerDimens.GridIconTile,
                    modifier = Modifier
                        .clip(NexplorerShape.IconTile)
                        .clickable(onClickLabel = "בחר", role = Role.Checkbox) { actions.onIconClick(entry) },
                )
            }
            if (state.inSelection) {
                SelectionBox(selected, Modifier.align(Alignment.TopEnd).padding(NexplorerDimens.Space1 / 2))
            }
        }
        Text(
            entry.name,
            style = FileNameTextStyle.merge(MaterialTheme.typography.bodySmall),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GalleryTile(entry: Entry, state: BrowserState, actions: FileListActions) {
    val selected = entry.path in state.selection
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(NexplorerShape.IconTile)
            .then(
                if (selected) Modifier.border(NexplorerDimens.Space1 / 2, MaterialTheme.colorScheme.primary, NexplorerShape.IconTile)
                else Modifier,
            )
            .entryClicks(entry, actions)
            // לתמונה ולווידאו אין שם על המשבצת — TalkBack צריך אותו.
            .semantics { contentDescription = entry.name },
    ) {
        FileThumbnail(entry, Modifier.fillMaxSize())
        if (!(entry.kind == FileKind.Image || entry.kind == FileKind.Video)) {
            Text(
                entry.name,
                style = FileNameTextStyle.merge(MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(NexplorerDimens.Space1),
            )
        }
        if (state.inSelection) {
            SelectionBox(
                selected,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(NexplorerDimens.Space2)
                    .clickable(onClickLabel = "בחר", role = Role.Checkbox) { actions.onIconClick(entry) },
            )
        }
    }
}

@Composable
private fun EmptyMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(NexplorerDimens.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.Fab))
        Spacer(Modifier.size(NexplorerDimens.Space3))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
