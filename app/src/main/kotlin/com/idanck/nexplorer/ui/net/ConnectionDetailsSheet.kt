package com.idanck.nexplorer.ui.net

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.data.net.ConnectionFacts
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.QuickConnect
import com.idanck.nexplorer.data.net.SynologyFs
import com.idanck.nexplorer.ui.components.SegmentedGroup
import com.idanck.nexplorer.ui.components.segment
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/**
 * "פרטי חיבור" (שפת העיצוב 1.14, לוח "סטטוס חיבור", גיליון 5) — מה-ⓘ במגירה, מ"פרטי חיבור" בתפריט ⋮ ומהשבב
 * בראש המסך. כרטיס "איך מחובר עכשיו" (Synology), השורות של [ConnectionFacts], ו"ערוך" / "בדוק שוב" (QuickConnect).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsSheet(
    connection: NetConnection,
    status: SynologyFs.Status?,
    checking: Boolean,
    onEdit: () -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = NexplorerShape.ContentSheet,
        containerColor = scheme.background,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexplorerDimens.Space4)
                .navigationBarsPadding()
                .padding(bottom = NexplorerDimens.Space5),
            verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Box(
                    Modifier
                        .size(NexplorerDimens.PlaceTile)
                        .clip(NexplorerShape.PlaceTile)
                        .background(if (connection.protocol.hasOwnIcon) scheme.surface else scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    ConnectionIcon(connection.protocol, tint = scheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("פרטי חיבור", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (connection.protocol.cloud) connection.title else "${connection.label} · ${connection.protocol.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            StatusCard(connection, status)

            SegmentedGroup {
                ConnectionFacts.of(connection, status, System.currentTimeMillis()).forEach { fact ->
                    Row(
                        Modifier
                            .segment(scheme.surface)
                            .heightIn(min = NexplorerDimens.RowHeight)
                            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                    ) {
                        Text(fact.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Text(
                            fact.value,
                            style = if (fact.mono) MonoTextStyle.copy(color = scheme.onSurface) else MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                // מכשיר מחובר (1.16) לא נערך — רק "נתק" בהגדרות וחיבור מחדש בקוד.
                if (connection.protocol != NetProtocol.Peer) {
                    Button(onClick = onEdit, shape = NexplorerShape.Pill, modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton)) {
                        Text("ערוך", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (connection.isQuickConnect) {
                    OutlinedButton(onClick = onRecheck, enabled = !checking, shape = NexplorerShape.Pill, modifier = Modifier.height(NexplorerDimens.BottomBarButton)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                        Text(if (checking) " בודק…" else " בדוק שוב", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** איך מחובר עכשיו — רק ב-Synology: QuickConnect (מקומי / ממסר / עוד לא) או רשת ביתית (כתובת קבועה). */
@Composable
private fun StatusCard(connection: NetConnection, status: SynologyFs.Status?) {
    if (connection.protocol != NetProtocol.Synology) return
    val scheme = MaterialTheme.colorScheme
    val c = NexplorerTheme.colors
    val (title, body) = when {
        !connection.isQuickConnect -> "רשת ביתית — כתובת קבועה" to "עובד בבית, או מבחוץ דרך VPN. לחיבור שעובר לבד בין הבית לחוץ — הוסף מקום מסוג QuickConnect."
        status?.route == QuickConnect.Route.Local -> "מחובר ישירות ברשת הביתית" to "במהירות מלאה. מחוץ לבית החיבור יעבור לבד לממסר של Synology."
        status?.route == QuickConnect.Route.Relay -> "מחובר דרך הממסר של Synology" to "ה-NAS לא נמצא ברשת הזו, אז התעבורה עוברת דרך Synology — איטי יותר. כשתחזור הביתה החיבור יעבור ישירות."
        else -> "עוד לא התחבר" to "בפתיחה הראשונה האפליקציה בודקת: בבית — ישירות, בחוץ — דרך הממסר."
    }
    val relay = connection.isQuickConnect && status?.route == QuickConnect.Route.Relay
    val local = connection.isQuickConnect && status?.route == QuickConnect.Route.Local
    val container: Color = when {
        relay -> c.amberContainer
        local -> scheme.primaryContainer
        else -> scheme.surface
    }
    val content: Color = when {
        relay -> c.onAmberContainer
        local -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val icon: ImageVector = if (relay) Icons.Outlined.Cloud else Icons.Outlined.Home
    Row(
        Modifier
            .fillMaxWidth()
            .clip(NexplorerShape.GroupOuter)
            .background(container)
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space3),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon + NexplorerDimens.Space1))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 / 2)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(body, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}
