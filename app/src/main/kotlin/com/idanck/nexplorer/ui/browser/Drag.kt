package com.idanck.nexplorer.ui.browser

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.ui.theme.NexplorerColors
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/** מה שנגרר: הנתיבים, ומאיזה חלון (0 או 1) — כדי שהחלון הזה יבצע את ההעתקה וינקה את הבחירה שלו. */
data class DragPayload(val pane: Int, val paths: List<String>)

/**
 * גרירה בשני חלונות (לוח Tablet). לחיצה ארוכה על פריט שכבר נבחר גוררת את כל הבחירה (במקום בחירת טווח);
 * שחרור על תיקייה — העתקה לתוכה, ועל החלון השני — לתיקייה שהוא מציג.
 */
class FileDrag(
    val pane: Int,
    val selectedPaths: () -> List<String>,
    val onDrop: (DragPayload, String) -> Unit,
    /** צבע הצל של הגרירה (primary שקוף). */
    val decoration: Color,
)

private fun DragAndDropEvent.payload(): DragPayload? = toAndroidDragEvent().localState as? DragPayload

/**
 * פריט נבחר בשני חלונות: הקשה כרגיל, לחיצה ארוכה מתחילה גרירה. גלאי מחוות אחד לשתיהן —
 * combinedClickable לצידו היה צורך את ה-down, והגרירה לא הייתה מתחילה.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.dragSource(entry: Entry, drag: FileDrag, onTap: () -> Unit): Modifier =
    semantics {
        onClick {
            onTap()
            true
        }
    }.dragAndDropSource(
        drawDragDecoration = {
            drawRoundRect(drag.decoration, cornerRadius = CornerRadius(NexplorerDimens.Space3.toPx()))
        },
        // block בשמו: מ-Compose 1.8 יש גם overload עם transferData, וגלאי ההתחלה שלו בפנים — למבדה
        // בסוף הקריאה נקשרת אליו, וההקשה והלחיצה הארוכה לא עוברות דרך הגלאי שלנו.
        block = {
            detectTapGestures(
                onTap = { onTap() },
                onLongPress = {
                    val paths = drag.selectedPaths().ifEmpty { listOf(entry.path) }
                    startTransfer(
                        DragAndDropTransferData(
                            clipData = ClipData.newPlainText("nexplorer", paths.joinToString("\n")),
                            localState = DragPayload(drag.pane, paths),
                        ),
                    )
                },
            )
        },
    )

/** מצב יעד השחרור של שורה/חלון: המודיפייר שמחבר אותו, והאם גרירה מרחפת מעליו עכשיו. */
class DropZone(val modifier: Modifier, val hovering: Boolean)

/**
 * [folder] כיעד שחרור. [accepts] — מסנן מה שאסור (תיקייה לתוך עצמה, חלון לעצמו);
 * בלי [drag] (חלון יחיד) — אין יעד.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberDropZone(folder: String?, drag: FileDrag?, accepts: (DragPayload) -> Boolean): DropZone {
    var hovering by remember(folder) { mutableStateOf(false) }
    if (drag == null || folder == null) return DropZone(Modifier, false)
    val currentAccepts by rememberUpdatedState(accepts)
    val currentDrop by rememberUpdatedState(drag.onDrop)
    val target = remember(folder) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val payload = event.payload() ?: return false
                currentDrop(payload, folder)
                return true
            }
        }
    }
    val modifier = Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.payload()?.let { it.paths.isNotEmpty() && currentAccepts(it) } == true },
        target = target,
    )
    return DropZone(modifier, hovering)
}

/** תיקייה שאפשר לשחרר עליה: לא אחת מהנגררות, ולא התיקייה שהן כבר בה (העתקה לאותו מקום). */
internal fun acceptsInto(folder: String): (DragPayload) -> Boolean = { payload ->
    folder !in payload.paths && payload.paths.none { folder.startsWith("$it/") } &&
        payload.paths.any { com.idanck.nexplorer.core.NetPaths.parentOf(it) != folder }
}

/** הדגשת יעד: מילוי dropTarget ומסגרת מקווקוות ב-amber (לוח Tablet: "שחרר כאן להעתקה"). */
internal fun Modifier.dropHighlight(active: Boolean, colors: NexplorerColors, inset: Boolean = true): Modifier {
    if (!active) return this
    return this
        .then(if (inset) Modifier.padding(horizontal = NexplorerDimens.DropTargetInset) else Modifier)
        .background(colors.dropTarget, NexplorerShape.DropTarget)
        .drawWithContent {
            drawContent()
            val width = NexplorerDimens.DropTargetBorder.toPx()
            inset(width / 2) {
                drawRoundRect(
                    color = colors.amber,
                    cornerRadius = CornerRadius(NexplorerDimens.Space3.toPx()),
                    style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(NexplorerDimens.DropTargetDash.toPx(), NexplorerDimens.DropTargetGap.toPx()),
                        ),
                    ),
                )
            }
        }
}
