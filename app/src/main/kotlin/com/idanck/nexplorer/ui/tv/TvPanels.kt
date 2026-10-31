package com.idanck.nexplorer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/** שורה בפאנל: [dividerBefore] — קו לפניה (מעבר בין קבוצות). */
class TvMenuItem(
    val label: String,
    val icon: ImageVector,
    val trailing: String? = null,
    val destructive: Boolean = false,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * פאנל הפעולות (לוח T3): נפתח מצד הסוף (שמאל ב-RTL), מעל המסך המעומעם; הפוקוס כלוא בו (חלון), והשורה הראשונה
 * בפוקוס. "חזרה" סוגרת. [header] — הקובץ (אריח, שם, גודל) או הבחירה. כל בחירה סוגרת את הפאנל לפני הפעולה.
 */
@Composable
fun TvActionsPanel(
    header: @Composable () -> Unit,
    items: List<TvMenuItem>,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .width(NexplorerDimens.TvActionsPanelWidth)
                    .fillMaxHeight()
                    .clip(NexplorerShape.TvPanel)
                    .background(scheme.surface)
                    .padding(
                        start = NexplorerDimens.Space5,
                        end = NexplorerDimens.TvOverscanHorizontal,
                        top = NexplorerDimens.TvOverscanVertical,
                        bottom = NexplorerDimens.TvOverscanVertical,
                    ),
                verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1),
            ) {
                Box(Modifier.padding(bottom = NexplorerDimens.Space3)) { header() }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1),
                ) {
                    items.forEachIndexed { i, item ->
                        if (item.dividerBefore && i > 0) {
                            HorizontalDivider(
                                color = scheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space1),
                            )
                        }
                        TvPanelRow(
                            item = item,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = {
                                onDismiss()
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun TvPanelRow(item: TvMenuItem, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val content = when {
        focused -> scheme.background
        item.destructive -> scheme.error
        else -> scheme.onSurface
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(if (focused) NexplorerDimens.TvActionRowFocused else NexplorerDimens.TvActionRow)
            .scale(if (focused) NexplorerDimens.TvRowFocusScale else 1f)
            .clip(NexplorerShape.PlaceTile)
            .background(if (focused) scheme.onSurface else scheme.surface)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = NexplorerDimens.Space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2),
    ) {
        Icon(item.icon, contentDescription = null, tint = if (focused || item.destructive) content else scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon - NexplorerDimens.Space1 / 2))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.trailing != null) {
            Text(item.trailing, style = MaterialTheme.typography.labelMedium, color = if (focused) content else scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
