package com.idanck.nexplorer.ui.properties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.HebrewDates
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.ui.components.FileIconTile
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Locale

/** מאפיינים לפריט אחד או לבחירה: פרטים, גודל מחושב (גם לתיקיות), ו-checksums לקובץ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesSheet(entries: List<Entry>, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val single = entries.singleOrNull()
    var stats by remember(entries) { mutableStateOf<LocalFiles.TreeStats?>(null) }
    LaunchedEffect(entries) { stats = LocalFiles.treeStats(entries.map { it.path }) }
    val sums = remember(entries) { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NexplorerShape.ContentSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexplorerDimens.Space5)
                .navigationBarsPadding()
                .padding(bottom = NexplorerDimens.Space5),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (single != null) FileIconTile(single)
                Spacer(Modifier.width(NexplorerDimens.RowGap))
                Text(
                    single?.name ?: Counts.items(entries.size),
                    style = if (single != null) FileNameTextStyle else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (single != null) {
                Detail(
                    "סוג",
                    when {
                        single.isDirectory -> "תיקייה"
                        single.extension.isEmpty() -> single.kind.label
                        else -> "${single.kind.label} · ${".${single.extension}".isolated()}"
                    },
                )
                Detail("מיקום", File(single.path).parent ?: "/", mono = true, onCopy = onCopy)
            }
            val s = stats
            Detail(
                "גודל",
                when {
                    s == null -> "מחשב…"
                    entries.any { it.isDirectory } ->
                        "${SizeFormat.short(s.bytes).isolated()} · ${Counts.files(s.files)} · ${Counts.folders(s.folders)}"
                    // מספר הבתים המדויק, מקובץ לפי אלפים.
                    else -> "${SizeFormat.short(s.bytes).isolated()} (${String.format(Locale.US, "%,d", s.bytes)} בתים)"
                },
            )
            if (single != null) {
                Detail("שונה", HebrewDates.full(single.lastModified, zone))
                val file = File(single.path)
                Detail("הרשאות", listOfNotNull("קריאה".takeIf { file.canRead() }, "כתיבה".takeIf { file.canWrite() }).joinToString(" · ").ifEmpty { "אין" })
                if (single.isHidden) Detail("מוסתר", "כן (מתחיל בנקודה)")
            }

            if (single != null && !single.isDirectory) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text("Checksums", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    listOf("MD5", "SHA-1", "SHA-256").forEach { algorithm ->
                        AssistChip(
                            onClick = {
                                if (algorithm !in sums) {
                                    sums[algorithm] = "מחשב…"
                                    scope.launch { sums[algorithm] = checksum(single.path, algorithm) }
                                }
                            },
                            label = { Text(algorithm) },
                        )
                    }
                }
                sums.forEach { (algorithm, value) -> Detail(algorithm, value, mono = true, onCopy = onCopy.takeIf { value != "מחשב…" }) }
            }
        }
    }
}

@Composable
internal fun Detail(label: String, value: String, mono: Boolean = false, onCopy: ((String) -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(NexplorerDimens.Fab + NexplorerDimens.Space5),
        )
        Text(
            value,
            // ערך בעברית: פסקה מימין לשמאל, והגדלים בתוכה מבודדים. בלי זה "3.2 MB" בתחילת השורה הופך את כולה ל-LTR.
            style = if (mono) MonoTextStyle else MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
            modifier = Modifier.weight(1f),
        )
        if (onCopy != null) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "העתק",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = NexplorerDimens.Space2)
                    .clickable { onCopy(value) },
            )
        }
    }
}

/** קובץ של כמה GB: הלולאה בודקת ביטול בכל חוצץ, כך שסגירת הגיליון עוצרת את הקריאה. */
private suspend fun checksum(path: String, algorithm: String): String = withContext(Dispatchers.IO) {
    try {
        val digest = MessageDigest.getInstance(algorithm)
        File(path).inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "שגיאה: ${e.message}"
    }
}
