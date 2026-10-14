@file:Suppress("DEPRECATION") // אייקון בלי כיוון (AddToHomeScreen) שאולי מסומן לטובת AutoMirrored — שיקוף לא משנה אותו. אייקונים עם חץ כבר משוקפים.

package com.idanck.nexplorer.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = trailing,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(NexplorerDimens.MenuItemHeight),
    )
}

@Composable
private fun TrailingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NexplorerMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = NexplorerShape.Menu,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(NexplorerDimens.MenuWidth),
    ) { content() }
}

/** מה שתפריט ⋮ הראשי מציג ומפעיל (לוח "תפריט ⋮"). */
class OverflowMenuModel(
    val count: Int,
    val viewSummary: String,
    val showSections: Boolean,
    val showHidden: Boolean,
    val isFolder: Boolean,
    val isFavorite: Boolean,
    val canPinShortcut: Boolean,
    val transfersLabel: String?,
    val onSelectAll: () -> Unit,
    val onProperties: () -> Unit,
    val onViewOptions: () -> Unit,
    val onFilter: () -> Unit,
    val onToggleSections: () -> Unit,
    val onToggleHidden: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onPinShortcut: () -> Unit,
    val onTransfers: () -> Unit,
    val onRefresh: () -> Unit,
    val onSettings: () -> Unit,
    val onAnalyzer: () -> Unit,
)

@Composable
fun OverflowMenu(expanded: Boolean, onDismiss: () -> Unit, model: OverflowMenuModel) {
    var folderOptions by remember(expanded) { mutableStateOf(false) }
    fun act(block: () -> Unit) = { onDismiss(); block() }
    NexplorerMenu(expanded, onDismiss) {
        if (folderOptions) {
            Text(
                "אפשרויות תיקייה",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2),
            )
            MenuRow("הצג מדורים", if (model.showSections) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, act(model.onToggleSections))
            MenuRow("הצג קבצים מוסתרים", if (model.showHidden) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, act(model.onToggleHidden))
            if (model.isFolder) {
                MenuRow(
                    if (model.isFavorite) "הסר מהמועדפים" else "הוסף למועדפים",
                    if (model.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    act(model.onToggleFavorite),
                )
                if (model.canPinShortcut) MenuRow("צור קיצור דרך", Icons.Outlined.AddToHomeScreen, act(model.onPinShortcut))
            }
            return@NexplorerMenu
        }
        MenuRow("בחר הכל", Icons.Outlined.SelectAll, act(model.onSelectAll), trailing = { TrailingText(model.count.toString()) }, enabled = model.count > 0)
        if (model.isFolder) MenuRow("מאפייני התיקייה", Icons.Outlined.Info, act(model.onProperties))
        MenuRow("רענן", Icons.Outlined.Refresh, act(model.onRefresh))
        MenuRow("מנתח אחסון", Icons.Outlined.DonutLarge, act(model.onAnalyzer))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow("אפשרויות תצוגה", Icons.Outlined.ViewAgenda, act(model.onViewOptions), trailing = { TrailingText(model.viewSummary) })
        MenuRow("סנן ברשימה", Icons.Outlined.FilterList, act(model.onFilter))
        MenuRow(
            "אפשרויות תיקייה",
            Icons.Outlined.Tune,
            onClick = { folderOptions = true },
            // "ימינה" משוקף: ב-RTL החץ מצביע שמאלה, לכיוון תת-התפריט.
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MenuRow(
            "העברות",
            Icons.Outlined.SwapVert,
            act(model.onTransfers),
            trailing = if (model.transfersLabel != null) { { AmberBadge(model.transfersLabel) } } else null,
        )
        MenuRow("הגדרות", Icons.Outlined.Settings, act(model.onSettings))
    }
}

/** תג ענבר קטן ("62% · 2") — בתנועה. */
@Composable
fun AmberBadge(text: String) {
    val c = NexplorerTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = c.onAmberContainer,
        modifier = Modifier
            .background(c.amberContainer, NexplorerShape.Chip)
            .padding(horizontal = NexplorerDimens.Space2, vertical = NexplorerDimens.Space1 / 2),
    )
}

/** תפריט ⋮ של הבחירה (לוח "בחירת קובץ ברשימה"): לפריט בודד יותר אפשרויות. */
class ItemMenuModel(
    val single: Boolean,
    val isFolder: Boolean,
    val isFavorite: Boolean,
    val canPinShortcut: Boolean,
    val onOpenWith: () -> Unit,
    val onMove: () -> Unit,
    val onCopyPath: () -> Unit,
    val onPinShortcut: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onProperties: () -> Unit,
    /** קובץ בודד עד 1 MB. */
    val canEditText: Boolean = false,
    /** ארכיון שנפתח כאן (ZIP, 7z, TAR…). */
    val isArchive: Boolean = false,
    /** הארכיון נוצר בתיקייה הנוכחית — באוסף אין כזו. */
    val canCompress: Boolean = false,
    val onEditText: () -> Unit = {},
    val onCompress: () -> Unit = {},
    val onExtract: () -> Unit = {},
)

@Composable
fun ItemMenu(expanded: Boolean, onDismiss: () -> Unit, model: ItemMenuModel) {
    fun act(block: () -> Unit) = { onDismiss(); block() }
    NexplorerMenu(expanded, onDismiss) {
        if (model.single && !model.isFolder) MenuRow("פתיחה באמצעות", Icons.AutoMirrored.Outlined.OpenInNew, act(model.onOpenWith))
        if (model.canEditText) MenuRow("ערוך כטקסט", Icons.Outlined.EditNote, act(model.onEditText))
        if (model.isArchive) MenuRow("חלץ", Icons.Outlined.Unarchive, act(model.onExtract))
        if (model.canCompress) MenuRow("הוסף לארכיון", Icons.Outlined.Archive, act(model.onCompress))
        MenuRow("העבר", Icons.AutoMirrored.Outlined.DriveFileMove, act(model.onMove))
        if (model.single) MenuRow("העתק נתיב", Icons.Outlined.Link, act(model.onCopyPath))
        if (model.single && model.isFolder) {
            MenuRow(
                if (model.isFavorite) "הסר מהמועדפים" else "הוסף למועדפים",
                if (model.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                act(model.onToggleFavorite),
            )
            if (model.canPinShortcut) MenuRow("צור קיצור דרך", Icons.Outlined.AddToHomeScreen, act(model.onPinShortcut))
        }
        MenuRow("מאפיינים", Icons.Outlined.Info, act(model.onProperties))
    }
}

/**
 * הכפתור הצף ותפריט "חדש" (לוח "תפריט חדש"): עיגול בצד הסוף; בפתיחה — עמעום, הכפתור גדל ל-64
 * ומתחלף ל-✕, והגלולות נערמות מעליו. תיקייה מוצפנת וחיבור רשת עוד לא קיימים — מוצגים מעומעמים.
 */
@Composable
fun BoxScope.NewFab(
    open: Boolean,
    onToggle: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onUnavailable: (String) -> Unit,
) {
    AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(NexplorerTheme.colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
        )
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(NexplorerDimens.FabMargin),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                SpeedDialPill("תיקייה חדשה", Icons.Outlined.CreateNewFolder) { onToggle(); onNewFolder() }
                SpeedDialPill("קובץ חדש", Icons.AutoMirrored.Outlined.NoteAdd) { onToggle(); onNewFile() }
                SpeedDialPill("תיקייה מוצפנת", Icons.Outlined.Lock, available = false) { onUnavailable("תיקייה מוצפנת") }
                SpeedDialPill("חיבור רשת / ענן", Icons.Outlined.Cloud, available = false) { onUnavailable("חיבור רשת וענן") }
                Spacer(Modifier.height(NexplorerDimens.Space1))
            }
        }
        val size by animateDpAsState(if (open) NexplorerDimens.FabOpen else NexplorerDimens.Fab, label = "fab")
        Surface(
            onClick = onToggle,
            shape = NexplorerShape.Fab,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = NexplorerDimens.Space2,
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = if (open) "סגור" else "חדש" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (open) Icons.Filled.Close else Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SpeedDialPill(label: String, icon: ImageVector, available: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = NexplorerShape.Pill,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .height(NexplorerDimens.SpeedDialItem)
            .alpha(if (available) 1f else 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space5 - NexplorerDimens.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(NexplorerDimens.Space3))
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (!available) {
                Spacer(Modifier.width(NexplorerDimens.Space2))
                Text("בקרוב", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
