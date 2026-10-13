package com.idanck.nexplorer.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.update.UpdateBlocker
import com.idanck.nexplorer.update.UpdateManifest
import com.idanck.nexplorer.update.UpdateState

/** פעולות שהבאנר וההגדרות מפעילים — אותו מנגנון משני המקומות. */
class UpdateActions(
    val onInstall: (UpdateManifest) -> Unit,
    val onLater: () -> Unit,
    val onSkip: (UpdateManifest) -> Unit,
    val onOpenInstallPermission: () -> Unit,
)

/**
 * באנר מעל פירורי הלחם כשיש גרסה חדשה. בזמן הורדה הפס בענבר (בתנועה).
 * חסימה בגלל הרשאת התקנה מקבלת כפתור שפותח את מסך ההרשאה — לא "אישור" שרק סוגר.
 */
@Composable
fun UpdateBanner(state: UpdateState, actions: UpdateActions, modifier: Modifier = Modifier) {
    val manifest = when (state) {
        is UpdateState.Available -> state.manifest
        is UpdateState.Downloading -> state.manifest
        is UpdateState.Verifying -> state.manifest
        is UpdateState.Installing -> state.manifest
        is UpdateState.Blocked -> state.manifest
        is UpdateState.Failed -> state.manifest
        else -> null
    } ?: return

    Surface(
        shape = NexplorerShape.Card,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, bottom = NexplorerDimens.Space3),
    ) {
        Column(
            Modifier.padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space2, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space1),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1),
        ) {
            Text("גרסה ${manifest.versionName} זמינה", style = MaterialTheme.typography.titleMedium)
            val detail = when (state) {
                is UpdateState.Downloading -> "מוריד… ${SizeFormat.short(state.bytes)} / ${SizeFormat.short(manifest.sizeBytes)}"
                is UpdateState.Verifying -> "מאמת את הקובץ…"
                is UpdateState.Installing -> "מתקין — אשר בחלון של המערכת אם הוא מופיע"
                is UpdateState.Blocked -> state.blocker.message
                is UpdateState.Failed -> state.message
                else -> manifest.notes ?: SizeFormat.short(manifest.sizeBytes)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { state.fraction },
                    color = NexplorerTheme.colors.amber,
                    trackColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = NexplorerDimens.Space1, end = NexplorerDimens.Space2),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    is UpdateState.Available, is UpdateState.Failed -> {
                        TextButton(onClick = { actions.onSkip(manifest) }) { Text("דלג על הגרסה") }
                        TextButton(onClick = actions.onLater) { Text("אחר כך") }
                        TextButton(onClick = { actions.onInstall(manifest) }) {
                            Text(if (state is UpdateState.Failed) "נסה שוב" else "עדכן")
                        }
                    }
                    is UpdateState.Blocked -> {
                        TextButton(onClick = actions.onLater) { Text("אחר כך") }
                        if (state.blocker == UpdateBlocker.InstallPermission) {
                            TextButton(onClick = actions.onOpenInstallPermission) { Text("פתח הגדרה") }
                        } else {
                            TextButton(onClick = { actions.onInstall(manifest) }) { Text("נסה שוב") }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
