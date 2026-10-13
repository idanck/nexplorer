package com.idanck.nexplorer.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.idanck.nexplorer.core.Durations
import com.idanck.nexplorer.core.HebrewDates
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.data.transfer.Transfer
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.data.transfer.TransferStatus
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/** צ'יפ ענבר קבוע בזמן העברה (לוח "מצב כהה"): פס קטן, "2 העברות · 62%". הקשה פותחת את הגיליון. */
@Composable
fun TransferChip(transfers: List<Transfer>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = transfers.filter { it.isActive }
    if (active.isEmpty()) return
    val running = active.firstOrNull { it.status == TransferStatus.Running || it.status == TransferStatus.Paused }
    val percent = ((running?.fraction ?: 0f) * 100).toInt()
    val c = NexplorerTheme.colors
    Surface(onClick = onClick, shape = NexplorerShape.Pill, color = c.amberContainer, contentColor = c.onAmberContainer, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
        ) {
            Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = c.amber, modifier = Modifier.size(NexplorerDimens.Space4 + NexplorerDimens.Space1 / 2))
            Text(
                (if (active.size == 1) "העברה 1" else "${active.size} העברות") + if (running != null) " · $percent%" else "",
                style = MaterialTheme.typography.labelLarge,
            )
            Progress(running?.fraction ?: 0f, Modifier.width(NexplorerDimens.Space6))
        }
    }
}

/** פס התקדמות בענבר — תמיד משמאל לימין, כמו בלוח. */
@Composable
private fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    val c = NexplorerTheme.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .height(NexplorerDimens.ProgressBar)
                .clip(NexplorerShape.Pill)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(NexplorerDimens.ProgressBar)
                    .clip(NexplorerShape.Pill)
                    .background(c.amber),
            )
        }
    }
}

/** גיליון ההעברות (לוח "הדבקה ומגש העברות"). הרשימה מאחור נשארת שמישה. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersSheet(
    transfers: List<Transfer>,
    onPause: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = transfers.filter { it.isActive }
    val finished = transfers.filter { !it.isActive }.sortedByDescending { it.finishedAt }
    val now = System.currentTimeMillis()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = NexplorerShape.ContentSheet,
    ) {
        Column(
            Modifier
                .padding(horizontal = NexplorerDimens.Space4)
                .navigationBarsPadding()
                .padding(bottom = NexplorerDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(NexplorerDimens.Space5 + NexplorerDimens.Space1)
                        .background(NexplorerTheme.colors.amberContainer, NexplorerShape.Pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = NexplorerTheme.colors.onAmberContainer, modifier = Modifier.size(NexplorerDimens.Space4))
                }
                Spacer(Modifier.width(NexplorerDimens.Space2))
                Text("העברות", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(NexplorerDimens.Space2))
                Text(
                    "${active.size} פעילות · ${finished.count { it.status == TransferStatus.Done }} הושלמו",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear, enabled = finished.isNotEmpty()) { Text("נקה") }
            }
            if (transfers.isEmpty()) {
                Text(
                    "אין העברות. העתקה, העברה ומחיקה יופיעו כאן.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = NexplorerDimens.Space5),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                items(active, key = { it.id }) { ActiveCard(it, onPause, onCancel) }
                items(finished, key = { it.id }) { FinishedRow(it, now) }
            }
        }
    }
}

private fun Transfer.route(): String {
    val from = sources.firstOrNull()?.substringBeforeLast('/')?.substringAfterLast('/').orEmpty()
    val to = targetDir?.substringAfterLast('/')
    return if (to == null) from else "$from → $to"
}

@Composable
private fun ActiveCard(t: Transfer, onPause: (Long) -> Unit, onCancel: (Long) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space3), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.currentName ?: t.title,
                        style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${t.kind.verb} · ${statusLabel(t)} · ${t.route()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (t.status == TransferStatus.Running || t.status == TransferStatus.Paused) {
                    FilledTonalIconButton(
                        onClick = { onPause(t.id) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(if (t.status == TransferStatus.Paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (t.status == TransferStatus.Paused) "המשך" else "השהה")
                    }
                }
                FilledTonalIconButton(
                    onClick = { onCancel(t.id) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) { Icon(Icons.Filled.Close, contentDescription = "בטל") }
            }
            Progress(if (t.status == TransferStatus.Queued) 0f else t.fraction, Modifier.fillMaxWidth())
            if (t.status == TransferStatus.Running || t.status == TransferStatus.Paused) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.fillMaxWidth()) {
                        val left = if (t.kind == TransferKind.Delete) "${t.doneItems} / ${t.totalItems}"
                        else "${SizeFormat.short(t.doneBytes)} / ${SizeFormat.short(t.totalBytes)}"
                        Text(left, style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (t.bytesPerSecond > 0) {
                            val eta = t.etaSeconds?.let { " · ${Durations.clock(it)} left" }.orEmpty()
                            Text("${SizeFormat.short(t.bytesPerSecond)}/s$eta", style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(t: Transfer): String = when (t.status) {
    TransferStatus.Queued -> "בתור"
    TransferStatus.Preparing -> "מחשב…"
    TransferStatus.Running -> "פעיל"
    TransferStatus.Paused -> "מושהה"
    TransferStatus.Done -> "הושלם"
    TransferStatus.Failed -> "נכשל"
    TransferStatus.Cancelled -> "בוטל"
}

@Composable
private fun FinishedRow(t: Transfer, now: Long) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.title}${t.targetDir?.let { " → ${it.substringAfterLast('/')}" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (t.status != TransferStatus.Done) {
                Text("${t.kind.verb} ${statusLabel(t)}${t.error?.let { ": $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = scheme.error)
            }
        }
        Spacer(Modifier.width(NexplorerDimens.Space2))
        Text(HebrewDates.ago(t.finishedAt, now), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(NexplorerDimens.Space2))
        Icon(
            if (t.status == TransferStatus.Done) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = statusLabel(t),
            tint = if (t.status == TransferStatus.Done) NexplorerTheme.colors.success else scheme.error,
            modifier = Modifier.size(NexplorerDimens.Space5 - NexplorerDimens.Space1),
        )
    }
}
