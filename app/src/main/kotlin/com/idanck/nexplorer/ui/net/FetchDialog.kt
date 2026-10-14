package com.idanck.nexplorer.ui.net

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens

/**
 * הורדה של קבצים מהרשת לעותק זמני, לפני פתיחה או שיתוף. רץ מייד כשהחלון נפתח; "ביטול" (או סגירה)
 * עוצר את ההורדה, והעותק החלקי נמחק. [onFetched] מקבל את העותקים במכשיר.
 */
@Composable
fun FetchDialog(
    entries: List<Entry>,
    fetch: suspend (onProgress: (Float) -> Unit) -> Result<List<Entry>>,
    onFetched: (List<Entry>) -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries) {
        fetch { progress = it }
            .onSuccess {
                onDismiss()
                onFetched(it)
            }
            .onFailure { error = it.message ?: "ההורדה נכשלה" }
    }
    val single = entries.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                single?.name ?: Counts.files(entries.size),
                style = if (single != null) FileNameTextStyle.merge(MaterialTheme.typography.titleLarge) else MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Text(
                    "מוריד מהרשת · ${SizeFormat.short(entries.sumOf { it.size }).isolated()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val failed = error
                if (failed != null) {
                    Text(failed, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else if (progress <= 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (error != null) "סגור" else "ביטול") } },
    )
}
