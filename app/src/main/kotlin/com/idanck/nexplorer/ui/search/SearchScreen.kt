package com.idanck.nexplorer.ui.search

import com.idanck.nexplorer.ui.tv.screenEntry
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.SearchQuery
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.ui.components.FileIconTile
import com.idanck.nexplorer.ui.components.SectionHeader
import com.idanck.nexplorer.ui.components.highlighted
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import java.util.Locale

/** מסך החיפוש (לוח "חיפוש"). הקשה על תיקייה נכנסת אליה; על קובץ — פותחת; לחיצה ארוכה — מציגה בתיקייה. */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    root: String?,
    onBack: () -> Unit,
    onOpen: (Entry) -> Unit,
    onShowInFolder: (Entry) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(root) { viewModel.start(root) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    val query = remember(state.text) { SearchQuery.parse(state.text) }
    val c = NexplorerTheme.colors
    val highlight = SpanStyle(background = c.amberContainer, color = c.onAmberContainer)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            SearchField(state.text, viewModel::setText, onBack)
            FilterChips(state, viewModel)
            if (state.scanning) {
                LinearProgressIndicator(color = c.amber, modifier = Modifier.fillMaxWidth().padding(horizontal = NexplorerDimens.Space4))
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = NexplorerShape.ContentSheet,
                modifier = Modifier.weight(1f).padding(top = NexplorerDimens.Space2),
            ) {
                LazyColumn(contentPadding = PaddingValues(top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space4)) {
                    state.groups.forEachIndexed { index, group ->
                        item(key = "g:${group.label}") {
                            SectionHeader(
                                "${group.label} · ${group.hits.size}",
                                trailing = if (index == 0) statusLine(state) else null,
                                first = index == 0,
                            )
                        }
                        items(group.hits, key = { it.entry.path }) { hit ->
                            ResultRow(hit, query, highlight, onOpen, onShowInFolder)
                        }
                    }
                    item(key = "footer") { Footer(state) }
                }
            }
        }
    }
}

private fun statusLine(state: SearchState): String {
    val seconds = String.format(Locale.US, "%.1f", state.elapsedMs / 1000.0)
    return when {
        // Synology (7ב): ה-NAS מחפש — אין תיקיות שנסרקו בטלפון.
        state.remote -> if (state.scanning) "ה-NAS מחפש…" else "חיפוש ב-NAS · $seconds שנ׳"
        state.scanning -> "סורק… ${state.scannedFolders} תיקיות"
        else -> "סריקה · $seconds שנ׳"
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space3, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.screenEntry()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = NexplorerDimens.FloatingBarHeight - NexplorerDimens.Space3)
                .border(NexplorerDimens.Space1 / 2, MaterialTheme.colorScheme.primary, NexplorerShape.Pill)
                .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(NexplorerDimens.Space2))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text("חיפוש — router* !old", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    singleLine = true,
                    // תיקון אוטומטי היה משכתב "router*" ומפעיל סריקה חדשה בכל רווח.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    textStyle = MonoTextStyle.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        textDirection = TextDirection.Content,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (text.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "נקה") }
            }
        }
    }
}

@Composable
private fun FilterChips(state: SearchState, viewModel: SearchViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = NexplorerDimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        item {
            MenuChip(
                label = if (state.allPlaces) (if (state.remote) "בכל ה-NAS" else "בכל המקומות") else "בתיקייה הנוכחית",
                selected = true,
                options = listOf("בתיקייה הנוכחית" to false, (if (state.remote) "בכל ה-NAS" else "בכל המקומות") to true),
                onPick = viewModel::setAllPlaces,
            )
        }
        item {
            MenuChip(
                label = state.kind?.label ?: "סוג",
                selected = state.kind != null,
                options = listOf<Pair<String, FileKind?>>("כל הסוגים" to null) + FileKind.entries.map { it.label to it },
                onPick = viewModel::setKind,
            )
        }
        item {
            MenuChip(
                label = if (state.size == SizeFilter.Any) "גודל" else state.size.label,
                selected = state.size != SizeFilter.Any,
                options = SizeFilter.entries.map { it.label to it },
                onPick = viewModel::setSize,
            )
        }
        item {
            MenuChip(
                label = if (state.date == DateFilter.Any) "תאריך" else state.date.label,
                selected = state.date != DateFilter.Any,
                options = DateFilter.entries.map { it.label to it },
                onPick = viewModel::setDate,
            )
        }
    }
}

@Composable
private fun <T> MenuChip(label: String, selected: Boolean, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = NexplorerShape.Pill,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = NexplorerShape.Menu) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    hit: SearchHit,
    query: SearchQuery,
    highlight: SpanStyle,
    onOpen: (Entry) -> Unit,
    onShowInFolder: (Entry) -> Unit,
) {
    val entry = hit.entry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpen(entry) }, onLongClick = { onShowInFolder(entry) })
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.RowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIconTile(entry)
        Spacer(Modifier.width(NexplorerDimens.RowGap))
        // ברוחב הטבעי (לא fillMaxWidth): טקסט LTR נצמד לצד ההתחלה, ליד האריח.
        Column(Modifier.weight(1f)) {
            Text(
                highlighted(entry.name, query.highlights(entry.name), highlight),
                style = FileNameTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.parentLabel.isolated() + if (entry.isDirectory) "" else " · ${SizeFormat.short(entry.size).isolated()}",
                style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onShowInFolder(entry) }) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "הצג בתיקייה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Footer(state: SearchState) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        when {
            !state.hasCriteria -> Text("הקלד שם, או בחר סוג / גודל / תאריך.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            state.scanning && state.total == 0 && state.remote -> Text("ה-NAS מחפש…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            !state.scanning && state.total == 0 -> Text("לא נמצא כלום.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.truncated -> Text("מוצגות ${state.total} התוצאות הראשונות — צמצם את החיפוש.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2)) {
            Text("תחביר:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SyntaxToken("*")
            Hint("כל רצף")
            SyntaxToken("?")
            Hint("תו אחד")
            SyntaxToken("a | b")
            Hint("או")
            SyntaxToken("!x")
            Hint("בלי")
        }
    }
}

@Composable
private fun SyntaxToken(token: String) {
    Text(
        token,
        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, NexplorerShape.Chip)
            .padding(horizontal = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space1 / 2),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
