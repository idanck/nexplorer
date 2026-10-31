package com.idanck.nexplorer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/**
 * פעולה שהמסך מוסיף לפאנל הפעולות ולסרגל הבחירה של הטלוויזיה (למשל "שלח למכשיר"). [onClick] מקבל את הפריטים —
 * הפריט שבפוקוס, או הבחירה כולה. [trailing] — טקסט קטן בסוף השורה (שמות המכשירים).
 */
class TvAction(
    val label: String,
    val icon: ImageVector,
    val trailing: String? = null,
    val destructive: Boolean = false,
    val onClick: (List<Entry>) -> Unit,
)

/**
 * הקשה בשלט: OK / Enter — [onClick] כשהמקש משתחרר; החזקה — [onLongClick] פעם אחת. מקש שלא נלחץ כאן (השחרור של
 * החזקה שפתחה חלון) לא מפעיל כלום. במגע — כרגיל (combinedClickable).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    this
        .onPreviewKeyEvent { e ->
            if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    val held = e.nativeKeyEvent.repeatCount > 0 || e.nativeKeyEvent.isLongPress
                    if (!held) {
                        down = true
                        longDone = false
                    } else if (down && !longDone && onLongClick != null) {
                        longDone = true
                        onLongClick()
                    }
                }
                KeyEventType.KeyUp -> {
                    if (down && !longDone) onClick()
                    down = false
                    longDone = false
                }
            }
            true
        }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
}

/** טבעת הפוקוס (1.15): קו בהיר מחוץ לגבולות, במרחק [NexplorerDimens.TvFocusRingGap], בפינות של [cornerRadius]. */
fun Modifier.tvFocusRing(visible: Boolean, color: Color, cornerRadius: Dp): Modifier = drawWithContent {
    drawContent()
    if (visible) {
        val ring = NexplorerDimens.TvFocusRing.toPx()
        val out = NexplorerDimens.TvFocusRingGap.toPx() + ring / 2
        val r = cornerRadius.toPx() + out
        drawRoundRect(
            color = color,
            topLeft = Offset(-out, -out),
            size = Size(size.width + out * 2, size.height + out * 2),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(ring),
        )
    }
}

/**
 * שורה במסילת המקומות (T1–T2): במסילה סגורה — אייקון בלבד; פתוחה — אייקון, שם ושורת משנה ([subtitle]), ו-[trailing]
 * בסוף. נבחר — primaryContainer; בפוקוס — רקע בהיר (onSurface) וטקסט כהה, מוגדל מעט. [dot] — נקודה קטנה על האייקון
 * (מכשיר זמין).
 */
@Composable
fun TvRailItem(
    icon: @Composable (tint: Color) -> Unit,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    trailing: String? = null,
    dot: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> scheme.onSurface
        selected -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val content = when {
        focused -> scheme.background
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val iconTint = if (focused || selected) content else scheme.onSurfaceVariant
    val shape: Shape = NexplorerShape.IconTile
    Box(
        modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(NexplorerDimens.TvRailButton))
            .heightIn(min = if (expanded) NexplorerDimens.TvRailRow else NexplorerDimens.TvRailButton)
            .scale(if (focused) NexplorerDimens.TvRowFocusScale else 1f)
            .clip(shape)
            .background(container)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) {
        if (!expanded) {
            Box(contentAlignment = Alignment.Center) {
                icon(iconTint)
                if (dot != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(NexplorerDimens.ConnectStatusDot)
                            .clip(NexplorerShape.Pill)
                            .background(dot),
                    )
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
            ) {
                icon(iconTint)
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
                            if (dot != null) Box(Modifier.size(NexplorerDimens.ConnectStatusDot).clip(NexplorerShape.Pill).background(dot))
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor ?: if (focused) content else scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
                }
            }
        }
    }
}

/**
 * כפתור גלולה לשלט: כפתורי הראש (חיפוש, תצוגה, חדש), סרגל הבחירה וסרגל ההדבקה. בפוקוס — רקע בהיר וטקסט כהה;
 * [primary] — הפעולה הראשית (primary); [destructive] — טקסט ב-error.
 */
@Composable
fun TvPillButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = NexplorerDimens.TvToolbarButton,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> scheme.onSurface
        primary -> scheme.primary
        else -> container
    }
    val fg = when {
        focused -> scheme.background
        primary -> scheme.onPrimary
        destructive -> scheme.error
        !enabled -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    Row(
        modifier
            .height(height)
            .scale(if (focused) NexplorerDimens.TvRowFocusScale else 1f)
            .clip(NexplorerShape.Pill)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = { if (enabled) onClick() })
            .padding(horizontal = NexplorerDimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon + NexplorerDimens.Space1 / 2))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (focused || primary) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** רמז מקש בתחתית המסך: "[OK] פתיחה". */
@Composable
fun TvKeyHint(key: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2)) {
        Box(
            Modifier
                .clip(NexplorerShape.Chip)
                .background(scheme.surfaceVariant)
                .padding(horizontal = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        ) {
            Text(key, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}
