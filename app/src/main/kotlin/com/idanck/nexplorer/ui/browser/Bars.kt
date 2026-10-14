package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/** סרגל עליון (לוח "מסך ראשי"): ☰ בכחול, כותרת + סיכום, חיפוש, ⋮. */
@Composable
fun BrowserTopBar(
    title: String,
    titleIsLtr: Boolean,
    summary: String,
    clipboardCount: Int,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    onClipboard: () -> Unit,
    moreMenu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = NexplorerDimens.Space3,
                end = NexplorerDimens.Space3,
                top = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2,
                bottom = NexplorerDimens.Space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "פתח מגירת מקומות", tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 / 2)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.let { if (titleIsLtr) it.copy(textDirection = TextDirection.Ltr) else it },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (clipboardCount > 0) {
            // לוח "הדבקה": צ'יפ "3 בלוח" בצד הסוף של הסרגל.
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, NexplorerShape.Pill)
                    .clickable(onClick = onClipboard)
                    .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$clipboardCount בלוח",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2))
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(NexplorerDimens.Space4),
                )
            }
        } else {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "חיפוש") }
        }
        Box {
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, contentDescription = "עוד פעולות") }
            moreMenu()
        }
    }
}

/** פירורי לחם כצ'יפים; האחרון (המיקום הנוכחי) מודגש. הקשה עליו = רענון. */
@Composable
fun Breadcrumbs(crumbs: List<Crumb>, onCrumb: (Crumb) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs.size, crumbs.lastOrNull()?.path) {
        if (crumbs.isNotEmpty()) listState.scrollToItem(crumbs.lastIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = NexplorerDimens.Space4,
            end = NexplorerDimens.Space4,
            top = NexplorerDimens.Space1,
            bottom = NexplorerDimens.Space3,
        ),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(crumbs, key = { i, c -> "$i:${c.path}" }) { index, crumb ->
            val current = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    // "ימינה" משוקף: ב-RTL החץ מצביע שמאלה, לכיוון ההמשך.
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(end = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2)
                            .size(NexplorerDimens.Space4),
                    )
                }
                Text(
                    crumb.label,
                    style = (if (current) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
                        .let { if (crumb.isLtr) it.copy(textDirection = TextDirection.Ltr) else it },
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .background(
                            if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            NexplorerShape.Pill,
                        )
                        .clickable { onCrumb(crumb) }
                        .padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
                )
            }
        }
    }
}

/** שורת "סנן ברשימה": שדה pill מתחת לפירורי הלחם. */
@Composable
fun FilterBar(text: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, bottom = NexplorerDimens.Space3)
            .border(NexplorerDimens.Space1 / 4, MaterialTheme.colorScheme.outline, NexplorerShape.Pill)
            .padding(start = NexplorerDimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NexplorerDimens.Space2))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text("סנן לפי שם", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "סגור סינון") }
    }
}

/** כותרת הבחירה הצפה (לוח "בחירת קובץ ברשימה"): בחר הכל, הפוך, ספירה + גודל, ✕. */
@Composable
fun SelectionHeader(
    count: Int,
    sizeText: String,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space3)
            .height(NexplorerDimens.FloatingBarHeight),
        shape = NexplorerShape.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = NexplorerDimens.Space1,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onSelectAll, colors = tint) { Icon(Icons.Outlined.SelectAll, contentDescription = "בחר הכל") }
            IconButton(onClick = onInvert, colors = tint) { Icon(Icons.Outlined.Deselect, contentDescription = "הפוך בחירה") }
            Column(Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2)) {
                Text(
                    Counts.selected(count),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                if (sizeText.isNotEmpty()) {
                    Text(sizeText, style = MaterialTheme.typography.labelSmall.merge(MonoTextStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onClose, colors = tint) { Icon(Icons.Filled.Close, contentDescription = "בטל בחירה") }
        }
    }
}

/** סרגל הפעולות הצף: אייקונים בלבד, מחיקה באדום (בלתי הפיך), ⋮ בצד ההתחלה. */
@Composable
fun SelectionActionBar(
    onMore: () -> Unit,
    onRename: (() -> Unit)?,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    moreMenu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space3)
            .height(NexplorerDimens.FloatingBarHeight),
        shape = NexplorerShape.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = NexplorerDimens.Space1,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1),
        ) {
            Box {
                IconButton(
                    onClick = onMore,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Icon(Icons.Filled.MoreVert, contentDescription = "עוד") }
                moreMenu()
            }
            IconButton(onClick = { onRename?.invoke() }, enabled = onRename != null) {
                Icon(Icons.Outlined.Edit, contentDescription = "שנה שם")
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "שתף") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "מחק", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "העתק") }
            IconButton(onClick = onCut) { Icon(Icons.Outlined.ContentCut, contentDescription = "גזור") }
        }
    }
}
