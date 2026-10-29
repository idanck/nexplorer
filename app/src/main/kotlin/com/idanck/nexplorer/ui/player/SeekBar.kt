package com.idanck.nexplorer.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.idanck.nexplorer.ui.theme.NexplorerDimens

/**
 * פס התקדמות דק לנגנים (1.9): קו של 3 עם ידית, ואזור לחיץ של 32 סביבו. הקשה קופצת, גרירה מזיזה
 * את הידית ומחפשת בשחרור. תמיד משמאל לימין — הזמן מתקדם ימינה גם בממשק בעברית.
 * [onScrub] — true בזמן גרירה (הנגן לא מסתיר את הבקרים באמצע).
 */
@Composable
fun ThinSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    trackColor: Color,
    progressColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    thumbSize: Dp = NexplorerDimens.SeekThumb,
    onScrub: (Boolean) -> Unit = {},
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(NexplorerDimens.SeekTouchHeight)
                .semantics {
                    contentDescription = "מיקום"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { value ->
                        if (duration <= 0) return@setProgress false
                        seek((value.coerceIn(0f, 1f) * duration).toLong())
                        true
                    }
                }
                .pointerInput(Unit) {
                    val inset = thumbSize.toPx() / 2
                    fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectTapGestures { offset -> if (duration > 0) seek((at(offset.x) * duration).toLong()) }
                }
                .pointerInput(Unit) {
                    val inset = thumbSize.toPx() / 2
                    fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = at(offset.x)
                            scrub(true)
                        },
                        onDragEnd = {
                            dragFraction?.let { if (duration > 0) seek((it * duration).toLong()) }
                            dragFraction = null
                            scrub(false)
                        },
                        onDragCancel = {
                            dragFraction = null
                            scrub(false)
                        },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = at(change.position.x)
                    }
                }
                .drawBehind {
                    val radius = thumbSize.toPx() / 2
                    val y = size.height / 2
                    val start = radius
                    val end = size.width - radius
                    val x = start + (end - start) * fraction
                    val stroke = NexplorerDimens.SeekTrack.toPx()
                    drawLine(trackColor, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                    drawLine(progressColor, Offset(start, y), Offset(x, y), stroke, StrokeCap.Round)
                    drawCircle(thumbColor, radius, Offset(x, y))
                },
        )
    }
}

/** כשבקרי הווידאו מוסתרים: קו התקדמות של 2 בקצה התחתון, בלי ידית ובלי מגע. */
@Composable
fun EdgeProgress(fraction: Float, trackColor: Color, progressColor: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(NexplorerDimens.SeekTrackHidden)
                .drawBehind {
                    drawRect(trackColor)
                    drawRect(progressColor, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)))
                },
        )
    }
}
