package com.idanck.nexplorer.ui.peer

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.peer.IncomingOffer
import com.idanck.nexplorer.data.peer.NearbyDevice
import com.idanck.nexplorer.data.peer.OfferedFile
import com.idanck.nexplorer.data.peer.PairingClient
import com.idanck.nexplorer.data.peer.PairingRequest
import com.idanck.nexplorer.data.peer.PeerHost
import com.idanck.nexplorer.data.peer.PeerNetwork
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.ui.components.SegmentedGroup
import com.idanck.nexplorer.ui.components.segment
import com.idanck.nexplorer.ui.drawer.DrawerSection
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.theme.PeerCodeTextStyle
import com.idanck.nexplorer.ui.theme.TvPeerCodeTextStyle
import com.idanck.nexplorer.ui.tv.LocalTvMode
import com.idanck.nexplorer.ui.tv.RailSection
import com.idanck.nexplorer.ui.tv.TvPillButton
import com.idanck.nexplorer.ui.tv.TvRailItem
import com.idanck.nexplorer.ui.tv.railIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * הממשק של "העברה בין מכשירים" (שפת העיצוב 1.16, לוחות Peer-* ו-Tv-Pair / Tv-Receive): מה שפתוח עכשיו — "שלח אל",
 * הקלדת קוד, מסך ההמתנה בטלוויזיה. מסופק ב-[LocalPeerUi]; החלונות עצמם ב-[PeerOverlays], מעל כל מסך.
 */
class PeerUi(val network: PeerNetwork, val transfers: TransferManager, private val scope: CoroutineScope) {
    /** "שלח אל" פתוח, עם הנתיבים שנבחרו. */
    var send by mutableStateOf<List<String>?>(null)

    /** הקלדת קוד מול המכשיר הזה (P2). */
    var pairWith by mutableStateOf<NearbyDevice?>(null)

    /** מה לשלוח אחרי שהחיבור יצליח (נבחר מכשיר שעוד לא חובר). */
    var sendAfterPair by mutableStateOf<List<String>?>(null)

    /** "חבר מכשיר" בטלוויזיה — מסך ההמתנה (T6) גם לפני שהטלפון התחיל. */
    var waiting by mutableStateOf(false)

    /** "ממתין לאישור ב-…" — השם, כל עוד ההצעה בדרך. */
    var offering by mutableStateOf<String?>(null)

    fun requestSend(paths: List<String>) {
        if (paths.isNotEmpty()) send = paths
    }

    /** הצעה, ואם אישרו — העתקה רגילה (במגש ההעברות) לתיקיית הקבלה בצד השני. */
    fun sendTo(context: Context, connection: NetConnection, paths: List<String>) {
        offering = connection.label
        scope.launch(Dispatchers.Main) {
            try {
                val files = withContext(Dispatchers.IO) { paths.map { describe(it) } }
                val dir = network.offer(connection, files)
                if (dir == null) {
                    toast(context, "${connection.label} דחה את הקבצים")
                } else {
                    transfers.enqueue(TransferKind.Copy, paths, dir)
                    toast(context, "נשלח ל${connection.label} — ההעברה במגש ההעברות")
                }
            } catch (e: IOException) {
                toast(context, e.message ?: "השליחה נכשלה")
            } finally {
                offering = null
                send = null
            }
        }
    }

    private suspend fun describe(path: String): OfferedFile = if (NetPaths.isRemote(path)) {
        val e = network.remote.stat(path)
        OfferedFile(e?.name ?: NetPaths.nameOf(path), e?.size ?: 0, e?.isDirectory == true)
    } else {
        val f = File(path)
        OfferedFile(f.name, if (f.isDirectory) 0 else f.length(), f.isDirectory)
    }
}

val LocalPeerUi = staticCompositionLocalOf<PeerUi?> { null }

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/** שורה במדור "מכשירים קרובים": מחובר ([connection]) ו/או נראה עכשיו ([device]). */
private class PeerRow(val name: String, val connection: NetConnection?, val device: NearbyDevice?) {
    val online: Boolean get() = device != null
    val status: String
        get() = when {
            connection != null && online -> "זמין · מחובר"
            connection != null -> "לא נראה ברשת"
            else -> "נמצא ברשת · הקש לחיבור"
        }
}

@Composable
private fun rememberRows(network: PeerNetwork): List<PeerRow> {
    val nearby by network.nearby.collectAsStateWithLifecycle()
    val devices by network.devices.collectAsStateWithLifecycle()
    return remember(nearby, devices) {
        devices.map { c -> PeerRow(c.label, c, nearby.firstOrNull { it.id == c.host }) } +
            nearby.filter { d -> devices.none { it.host == d.id } }.map { PeerRow(it.name, null, it) }
    }
}

// --- המגירה (P1) והמסילה (T2) ---

/** "מכשירים קרובים" במגירה של הטלפון. */
@Composable
fun NearbyDrawerSection(activeConnectionId: String?, onBrowse: (NetConnection) -> Unit) {
    val ui = LocalPeerUi.current ?: return
    val rows = rememberRows(ui.network)
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    DrawerSection("מכשירים קרובים")
    if (rows.isEmpty()) {
        Text(
            "פתח את nexplorer במכשיר אחר באותה רשת Wi-Fi",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = NexplorerDimens.Space2),
        )
        return
    }
    SegmentedGroup {
        rows.forEach { row ->
            val active = row.connection != null && row.connection.id == activeConnectionId
            Row(
                Modifier
                    .segment(if (active) scheme.primaryContainer else scheme.surface)
                    .clickable {
                        when {
                            row.connection != null && row.online -> onBrowse(row.connection)
                            row.connection != null -> toast(context, "${row.name} לא נראה ברשת — nexplorer צריך להיות פתוח בו")
                            row.device != null -> ui.pairWith = row.device
                        }
                    }
                    .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
            ) {
                Box(
                    Modifier.size(NexplorerDimens.PlaceTile).clip(NexplorerShape.PlaceTile).background(if (active) scheme.primary else scheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Devices, contentDescription = null, tint = if (active) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
                }
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusLine(row)
                }
                if (row.connection == null) {
                    FilledTonalButton(onClick = { ui.pairWith = row.device }, modifier = Modifier.height(NexplorerDimens.ConnectChooseButton)) {
                        Text("חבר", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(row: PeerRow) {
    val color = if (row.connection != null && row.online) NexplorerTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2)) {
        if (row.connection != null && row.online) {
            Box(Modifier.size(NexplorerDimens.ConnectStatusDot).clip(NexplorerShape.Pill).background(color))
        }
        Text(row.status, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** "מכשירים קרובים" במסילה של הטלוויזיה (T2), ו"חבר מכשיר". */
@Composable
fun ColumnScope.TvNearbyRail(expanded: Boolean, activeConnectionId: String?, onBrowse: (NetConnection) -> Unit) {
    val ui = LocalPeerUi.current ?: return
    val rows = rememberRows(ui.network)
    val context = LocalContext.current
    val success = NexplorerTheme.colors.success
    if (expanded) RailSection("מכשירים קרובים")
    rows.forEach { row ->
        TvRailItem(
            icon = railIcon(Icons.Outlined.Devices),
            label = row.name,
            expanded = expanded,
            selected = row.connection != null && row.connection.id == activeConnectionId,
            subtitle = row.status,
            subtitleColor = if (row.connection != null && row.online) success else null,
            dot = if (row.connection != null && row.online) success else null,
            onClick = {
                when {
                    row.connection != null && row.online -> onBrowse(row.connection)
                    row.connection != null -> toast(context, "${row.name} לא נראה ברשת — nexplorer צריך להיות פתוח בו")
                    row.device != null -> ui.pairWith = row.device
                }
            },
        )
    }
    TvRailItem(
        icon = railIcon(Icons.Outlined.Add),
        label = "חבר מכשיר",
        expanded = expanded,
        selected = false,
        onClick = { ui.waiting = true },
    )
}

// --- החלונות ---

/** כל החלונות של העברה בין מכשירים, מעל כל מסך: קוד לחיבור, קבלת קבצים, "שלח אל" והקלדת קוד. */
@Composable
fun PeerOverlays() {
    val ui = LocalPeerUi.current ?: return
    val network = ui.network
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairing by network.pairing.collectAsStateWithLifecycle()
    val offer by network.offer.collectAsStateWithLifecycle()

    LaunchedEffect(network) {
        network.pairedEvents.collect { name ->
            ui.waiting = false
            toast(context, "חובר ל$name")
        }
    }

    val request = pairing
    if (request != null || ui.waiting) {
        IncomingPairing(request, network.identity.name) {
            request?.cancel?.invoke()
            ui.waiting = false
        }
    }

    offer?.let { o ->
        ReceiveDialog(o) { accept, always ->
            if (accept && always) scope.launch { network.setAutoAccept(o.peerId, true) }
            o.answer(accept)
        }
    }

    ui.pairWith?.let { device ->
        PairSheet(
            ui = ui,
            device = device,
            onPaired = { connection ->
                ui.pairWith = null
                toast(context, "חובר ל${connection.label}")
                ui.sendAfterPair?.let { paths ->
                    ui.sendAfterPair = null
                    ui.send = paths
                    ui.sendTo(context, connection, paths)
                }
            },
            onDismiss = {
                ui.pairWith = null
                ui.sendAfterPair = null
            },
        )
    }

    ui.send?.let { paths -> if (ui.pairWith == null) SendSheet(ui, paths) }
}

/** חלון: בטלפון — גיליון תחתון; בטלוויזיה — כרטיס במרכז (בשלט אין גיליון שנגרר). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (LocalTvMode.current) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(color = scheme.surface, shape = NexplorerShape.Popover, modifier = Modifier.width(NexplorerDimens.PeerDialogWidth)) {
                Column(
                    Modifier.padding(NexplorerDimens.Space5),
                    verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                    content = content,
                )
            }
        }
    } else {
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
                content = content,
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Devices) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
        Box(Modifier.size(NexplorerDimens.PlaceTile + NexplorerDimens.Space2).clip(NexplorerShape.IconTile).background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(NexplorerDimens.TileGlyph))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun LockHint(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(NexplorerShape.FindingCard).background(scheme.surfaceVariant).padding(horizontal = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space3),
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** שש ספרות בשתי שלשות, משמאל לימין. [code] קצר משש — תיבות ריקות. [active] — התיבה הבאה מודגשת. */
@Composable
private fun CodeDigits(code: String, tv: Boolean, active: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 6) {
                if (i == 3) Spacer(Modifier.width(NexplorerDimens.PeerCodeGroupGap - NexplorerDimens.Space1))
                val next = active && i == code.length
                Box(
                    Modifier
                        .size(NexplorerDimens.PeerCodeDigitWidth, if (tv) NexplorerDimens.TvPeerCodeDigitHeight else NexplorerDimens.PeerCodeDigitHeight)
                        .clip(NexplorerShape.ConnectField)
                        .background(if (tv) scheme.surfaceVariant else scheme.surface)
                        .then(
                            if (tv) {
                                Modifier
                            } else {
                                Modifier.border(
                                    if (next) NexplorerDimens.ConnectTypeBorderSelected else NexplorerDimens.ConnectTypeBorder,
                                    if (next) scheme.primary else scheme.outline,
                                    NexplorerShape.ConnectField,
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(code.getOrNull(i)?.toString() ?: "", style = if (tv) TvPeerCodeTextStyle else PeerCodeTextStyle, color = scheme.onSurface)
                }
            }
        }
    }
}

/** הקלדת הקוד (P2): מתחבר כשנפתח, ומאשר כשיש שש ספרות. */
@Composable
private fun PairSheet(ui: PeerUi, device: NearbyDevice, onPaired: (NetConnection) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tv = LocalTvMode.current
    val scope = rememberCoroutineScope()
    val holder = remember { arrayOfNulls<PairingClient>(1) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(device) {
        try {
            holder[0] = ui.network.beginPairing(device)
            ready = true
        } catch (e: IOException) {
            error = e.message ?: "החיבור נכשל"
        }
        busy = false
        runCatching { focus.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { holder[0]?.close() } }

    fun submit() {
        val client = holder[0] ?: return
        busy = true
        scope.launch {
            try {
                onPaired(ui.network.finishPairing(client, code))
                holder[0] = null
            } catch (e: PairingClient.WrongCode) {
                attempts++
                code = ""
                if (attempts >= 3) {
                    client.close()
                    holder[0] = null
                    ready = false
                    error = "הקוד לא תאם שלוש פעמים — התחל את החיבור מחדש"
                } else {
                    error = e.message
                }
            } catch (e: IOException) {
                holder[0] = null
                ready = false
                error = e.message ?: "החיבור נכשל"
            } finally {
                busy = false
            }
        }
    }

    PeerSurface(onDismiss) {
        SheetHeader("חיבור ל${device.name}", device.host.isolated())
        Text(
            if (ready || busy) "הקלד את הקוד שמופיע עכשיו ב${device.name}" else "לא ניתן להתחבר",
            style = MaterialTheme.typography.bodyLarge,
        )
        BasicTextField(
            value = code,
            onValueChange = { v ->
                code = v.filter(Char::isDigit).take(6)
                error = null
            },
            enabled = ready && !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { _ ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CodeDigits(code, tv = false, active = ready) }
            },
        )
        if (busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(NexplorerDimens.TileGlyph)) }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.error) }
        LockHint("הקוד מאשר פעם אחת שזה באמת המכשיר שלך. מאז שני המכשירים מזהים זה את זה לבד, וההעברות מוצפנות ועוברות רק ברשת הביתית.")
        Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
            if (tv) {
                TvPillButton("חבר", null, ::submit, Modifier.weight(1f), primary = true, enabled = ready && !busy && code.length == 6)
                TvPillButton("ביטול", null, onDismiss)
            } else {
                Button(onClick = ::submit, enabled = ready && !busy && code.length == 6, shape = NexplorerShape.Pill, modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton)) {
                    Text("חבר", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onDismiss, shape = NexplorerShape.Pill, modifier = Modifier.height(NexplorerDimens.BottomBarButton)) {
                    Text("ביטול", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** "שלח אל" (P3): המכשירים המחוברים והנראים. מכשיר שעוד לא חובר — קודם קוד, ואז השליחה. */
@Composable
private fun SendSheet(ui: PeerUi, paths: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val rows = rememberRows(ui.network)
    val waiting = ui.offering
    val dismiss = { if (ui.offering == null) ui.send = null }
    PeerSurface(onDismiss = dismiss) {
        SheetHeader("שלח אל", if (paths.size == 1) NetPaths.nameOf(paths.single()) else "${paths.size} פריטים", Icons.AutoMirrored.Outlined.Send)
        if (waiting != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3), modifier = Modifier.padding(vertical = NexplorerDimens.Space3)) {
                CircularProgressIndicator(Modifier.size(NexplorerDimens.TileGlyph))
                Text("ממתין לאישור ב$waiting…", style = MaterialTheme.typography.bodyLarge)
            }
            return@PeerSurface
        }
        if (rows.isEmpty()) {
            Text("אין מכשירים ברשת עכשיו — פתח את nexplorer במכשיר השני", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        } else {
            SegmentedGroup {
                rows.forEach { row ->
                    val enabled = row.online
                    Row(
                        Modifier
                            .segment(scheme.surface)
                            .heightIn(min = NexplorerDimens.SettingsRowHeight)
                            .clickable(enabled = enabled) {
                                if (row.connection != null) {
                                    ui.sendTo(context, row.connection, paths)
                                } else {
                                    ui.sendAfterPair = paths
                                    ui.pairWith = row.device
                                }
                            }
                            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                    ) {
                        Box(
                            Modifier.size(NexplorerDimens.IconTile).clip(NexplorerShape.IconTile).background(if (row.connection != null && enabled) scheme.primaryContainer else scheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Devices, contentDescription = null, tint = if (row.connection != null && enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyLarge, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
                            Text(
                                when {
                                    row.connection != null && enabled -> "זמין · יישמר ב-${PeerHost.RECEIVE_DIR}"
                                    row.connection != null -> "לא נראה ברשת עכשיו"
                                    else -> "לא מחובר — קוד יופיע במכשיר"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.connection != null && enabled) NexplorerTheme.colors.success else scheme.onSurfaceVariant,
                            )
                        }
                        if (row.connection != null && enabled) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
                        }
                    }
                }
            }
        }
        Text("מופיעים רק מכשירים עם nexplorer פתוח באותה רשת Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** הקוד שהמכשיר הזה מציג (T6): בטלוויזיה — מסך מלא; בטלפון — חלון. [request] null — עוד לא התחיל חיבור ("ממתין…"). */
@Composable
private fun IncomingPairing(request: PairingRequest?, myName: String, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (!LocalTvMode.current) {
        if (request == null) return
        AlertDialog(
            onDismissRequest = {},
            title = { Text("חיבור ${request.peerName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("הקלד את הקוד הזה ב${request.peerName}:", style = MaterialTheme.typography.bodyLarge)
                    CodeDigits(request.code, tv = false)
                    Text("אחרי החיבור המכשירים מזהים זה את זה לבד, וההעברות מוצפנות.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = onCancel) { Text("ביטול") } },
        )
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
                .padding(horizontal = NexplorerDimens.TvOverscanHorizontal, vertical = NexplorerDimens.TvOverscanVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.TvOverscanHorizontal),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4)) {
                Text("מכשירים קרובים", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text("חיבור מכשיר חדש", style = MaterialTheme.typography.headlineLarge)
                Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                    Text("1. פתח את nexplorer בטלפון, באותה רשת Wi-Fi.", style = MaterialTheme.typography.bodyLarge)
                    Text("2. במגירה, תחת \"מכשירים קרובים\", בחר $myName ← חבר.", style = MaterialTheme.typography.bodyLarge)
                    Text("3. הקלד את הקוד שמופיע כאן.", style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                    Text("אחרי החיבור, ההעברות מוצפנות ועוברות רק ברשת הביתית — לא דרך האינטרנט.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                TvPillButton("ביטול", null, onCancel, Modifier.focusRequester(focus))
            }
            Column(
                Modifier.width(NexplorerDimens.TvPairCardWidth).clip(NexplorerShape.Card).background(scheme.surface).padding(NexplorerDimens.Space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2),
            ) {
                Text("הקוד", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                CodeDigits(request?.code ?: "", tv = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    Box(Modifier.size(NexplorerDimens.ConnectStatusDot).clip(NexplorerShape.Pill).background(NexplorerTheme.colors.success))
                    Text(
                        if (request == null) "ממתין לטלפון…" else "הקלד את הקוד ב${request.peerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** קבלת קבצים (T5): מי שולח, מה, לאן — "קבל" / "דחה", ו"קבל מהמכשיר הזה בלי לשאול". */
@Composable
private fun ReceiveDialog(offer: IncomingOffer, onAnswer: (accept: Boolean, always: Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tv = LocalTvMode.current
    var always by remember(offer) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(offer) { runCatching { focus.requestFocus() } }
    val count = offer.files.size
    Dialog(onDismissRequest = { onAnswer(false, false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = scheme.surface,
            shape = NexplorerShape.Popover,
            modifier = Modifier.widthIn(max = NexplorerDimens.PeerDialogWidth).padding(NexplorerDimens.Space4),
        ) {
            Column(Modifier.padding(NexplorerDimens.Space5), verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2)) {
                    Box(Modifier.size(NexplorerDimens.PlaceTile + NexplorerDimens.Space3).clip(NexplorerShape.IconTile).background(NexplorerTheme.colors.amberContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = NexplorerTheme.colors.onAmberContainer, modifier = Modifier.size(NexplorerDimens.TileGlyph))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (count == 1) "${offer.peerName} שולח קובץ" else "${offer.peerName} שולח $count קבצים",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("${SizeFormat.short(offer.total).isolated()} · מכשיר מחובר", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
                SegmentedGroup {
                    offer.files.take(4).forEach { f ->
                        Row(
                            Modifier.segment(scheme.surfaceVariant).heightIn(min = NexplorerDimens.ConnectField).padding(horizontal = NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                        ) {
                            Text(f.name, style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (!f.isDirectory) Text(SizeFormat.short(f.size), style = MonoTextStyle, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                if (count > 4) Text("ועוד ${count - 4}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                    Text("יישמרו ב-", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Text(PeerHost.RECEIVE_DIR, style = MonoTextStyle)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { always = !always },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text("קבל מהמכשיר הזה בלי לשאול", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                    if (tv) {
                        TvPillButton("קבל", null, { onAnswer(true, always) }, Modifier.weight(1f).focusRequester(focus), primary = true)
                        TvPillButton("דחה", null, { onAnswer(false, false) }, Modifier.weight(1f))
                    } else {
                        Button(onClick = { onAnswer(true, always) }, shape = NexplorerShape.Pill, modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton).focusRequester(focus)) {
                            Text("קבל", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = { onAnswer(false, false) }, shape = NexplorerShape.Pill, modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton)) {
                            Text("דחה", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
