package com.idanck.nexplorer.ui.apk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.idanck.nexplorer.core.ApkInstallState
import com.idanck.nexplorer.core.ApkStatus
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.apk.ApkDetails
import com.idanck.nexplorer.data.apk.ApkInspector
import com.idanck.nexplorer.ui.components.FileIconTile
import com.idanck.nexplorer.ui.properties.Detail
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * הקשה על ‎.apk: מה האפליקציה, ומה תעשה ההתקנה מול מה שמותקן. ההתקנה עצמה במתקין של המערכת —
 * הוא מבקש אישור, ובפעם הראשונה גם הרשאה להתקין מכאן. בחזרה ממנו המצב נקרא מחדש.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApkSheet(
    entry: Entry,
    onInstall: () -> Unit,
    onLaunch: (String) -> Unit,
    onOpenWith: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(entry.path) { mutableStateOf<Result<ApkDetails>?>(null) }
    // גם ב-ON_RESUME הראשון: הקריאה הראשונה, ואחריה כל חזרה מהמתקין.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            details = withContext(Dispatchers.IO) { runCatching { ApkInspector.read(context, entry.path) } }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NexplorerShape.ContentSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val loaded = details?.getOrNull()
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexplorerDimens.Space5)
                .navigationBarsPadding()
                .padding(bottom = NexplorerDimens.Space5),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = loaded?.icon
                if (icon != null) {
                    Image(
                        icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(NexplorerDimens.GridIconTile).clip(NexplorerShape.IconTile),
                    )
                } else {
                    FileIconTile(entry)
                }
                Spacer(Modifier.width(NexplorerDimens.RowGap))
                Column(Modifier.weight(1f)) {
                    Text(loaded?.label ?: entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(loaded?.packageName ?: entry.name, style = if (loaded != null) MonoTextStyle else FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            val failure = details?.exceptionOrNull()
            when {
                details == null -> Text("קורא את ה-APK…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                failure != null || loaded == null -> Text(
                    failure?.message ?: "לא ניתן לקרוא את ה-APK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> ApkDetailRows(loaded, entry, onCopy)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                val action = loaded?.state?.action
                if (loaded != null && action != null && ApkInspector.runsHere(loaded) && loaded.signers.isNotEmpty()) {
                    Button(onClick = onInstall, shape = NexplorerShape.Pill) { Text(action) }
                }
                if (loaded?.installed?.launchable == true) {
                    OutlinedButton(onClick = { onLaunch(loaded.packageName) }, shape = NexplorerShape.Pill) { Text("פתח") }
                }
                TextButton(onClick = onOpenWith) { Text("פתיחה באמצעות") }
            }
        }
    }
}

@Composable
private fun ApkDetailRows(details: ApkDetails, entry: Entry, onCopy: (String) -> Unit) {
    Detail("גרסה", "${details.versionName.orEmpty().ifBlank { "—" }.isolated()} (${details.versionCode})")
    val installed = details.installed
    Detail(
        "מצב",
        if (installed != null && details.state != ApkInstallState.Same) {
            "${details.state.label} · מותקנת ${installed.versionName.orEmpty().isolated()} (${installed.versionCode})"
        } else {
            details.state.label
        },
    )
    Detail("Android", "מינימום ${ApkStatus.sdkLabel(details.minSdk).isolated()} · יעד ${ApkStatus.sdkLabel(details.targetSdk).isolated()}")
    if (!ApkInspector.runsHere(details)) {
        Text(
            "דורש גרסת Android חדשה יותר מזו שבמכשיר",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Detail("הרשאות", if (details.permissions == 0) "אין" else "${details.permissions}")
    Detail("גודל", SizeFormat.short(entry.size).isolated())
    val signer = details.signers.firstOrNull()
    if (signer == null) {
        Detail("חתימה", "לא חתום — המערכת לא תתקין אותו")
    } else {
        Detail("חתימה (SHA-256)", signer, mono = true, onCopy = onCopy)
    }
}
