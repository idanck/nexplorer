package com.idanck.nexplorer.ui.net

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.net.FoundServer
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.NetDiscovery
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import kotlinx.coroutines.launch

/** סוג במסך "הוסף מקום" — לפי הלוח. רק SMB פעיל בשלב 5א; לשאר — הסבר למה עוד לא. */
private enum class PlaceType(val label: String, val icon: ImageVector, val unavailable: String?) {
    Smb("SMB", Icons.Outlined.Dns, null),
    Sftp("SFTP", Icons.Outlined.Terminal, "SFTP — בשלב 5ב, אחרי SMB"),
    WebDav("WebDAV", Icons.Outlined.Language, "WebDAV — בשלב 5ג"),
    Cloud("ענן", Icons.Outlined.Cloud, "ענן — מחכה להחלטה D4"),
    Ftp("FTP/S", Icons.Outlined.Download, "FTP/S לא בתוכנית: ב-D3 נבחרו SMB, SFTP ו-WebDAV"),
    S3("S3", Icons.Outlined.Storage, "S3 לא בתוכנית: ב-D3 נבחרו SMB, SFTP ו-WebDAV"),
    Vault("כספת", Icons.Outlined.Lock, "תיקייה מוצפנת נוצרת מהכפתור הצף: חדש ← תיקייה מוצפנת"),
    Archive("ארכיון", Icons.Outlined.Archive, "ארכיון נפתח בהקשה עליו ברשימת הקבצים"),
}

/**
 * "הוסף מקום" (לוח "חיבור חדש"): סוג, שרתים שנמצאו ברשת, ופרטי החיבור. "בדוק והתחבר" מתחבר באמת
 * ([onConnect]) — רק חיבור שעבד נשמר. [editing] — "ערוך" מהמגירה: אותו מסך עם הפרטים השמורים, ושדה
 * סיסמה ריק משאיר את השמורה.
 */
@Composable
fun ConnectScreen(
    editing: NetConnection?,
    discovery: NetDiscovery,
    /** null — הצליח ונשמר; אחרת ההודעה. הסיסמה נמחקת אחרי הקריאה. */
    onConnect: suspend (NetConnection, CharArray?) -> String?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val found by discovery.found.collectAsStateWithLifecycle()
    val searching by discovery.searching.collectAsStateWithLifecycle()

    var host by rememberSaveable { mutableStateOf(editing?.host.orEmpty()) }
    var share by rememberSaveable { mutableStateOf(editing?.share.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(editing?.user.orEmpty()) }
    // הסיסמה לא נשמרת ב-Bundle: סיבוב מסך מנקה אותה, וזה בסדר.
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(editing?.port?.toString().orEmpty()) }
    var domain by rememberSaveable { mutableStateOf(editing?.domain.orEmpty()) }
    var advanced by rememberSaveable { mutableStateOf(editing?.let { it.port != null || it.domain.isNotBlank() || it.name.isNotBlank() } ?: false) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val portValue = port.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val problem = when {
        host.isBlank() -> "צריך כתובת של שרת"
        host.trim().any { it.isWhitespace() || it == '/' || it == '\\' } -> "כתובת השרת בלי רווחים ובלי / — רק השם או ה-IP"
        port.isNotBlank() && (portValue == null || portValue !in 1..65535) -> "פורט — מספר בין 1 ל-65535"
        share.isNotBlank() -> NetPaths.validateName(NetProtocol.Smb, share.trim())
        else -> null
    }

    fun connect() {
        val draft = NetConnection(
            id = editing?.id ?: NetConnection.newId(),
            protocol = NetProtocol.Smb,
            name = name.trim(),
            host = host.trim(),
            port = portValue?.takeIf { it != NetProtocol.Smb.defaultPort },
            share = share.trim().trim('/'),
            user = user.trim(),
            domain = domain.trim(),
            secret = editing?.secret,
        )
        val secret = password.takeIf { it.isNotEmpty() }?.toCharArray()
        busy = true
        error = null
        scope.launch {
            error = try {
                onConnect(draft, secret)
            } finally {
                secret?.fill('\u0000')
                busy = false
            }
            if (error == null) onClose()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
                modifier = Modifier.padding(start = NexplorerDimens.Space3, end = NexplorerDimens.Space3, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space2),
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(NexplorerDimens.TouchTarget)) {
                    Icon(Icons.Filled.Close, contentDescription = "סגור")
                }
                Text(if (editing == null) "הוסף מקום" else "ערוך מקום", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space4),
                verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
            ) {
                SectionLabel("סוג")
                PlaceType.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                        row.forEach { type ->
                            TypeTile(type, selected = type == PlaceType.Smb, modifier = Modifier.weight(1f)) {
                                note = type.unavailable
                            }
                        }
                    }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                SectionLabel("נמצאו ברשת")
                FoundCard(found, searching, selectedHost = host.trim()) { server ->
                    host = server.host
                    if (server.port != NetProtocol.Smb.defaultPort) {
                        port = server.port.toString()
                        advanced = true
                    }
                    if (name.isBlank()) name = server.name
                }

                SectionLabel("פרטי חיבור")
                Card {
                    Field("שרת", host, { host = it; error = null }, keyboard = KeyboardType.Uri)
                    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                        Field("שיתוף", share, { share = it; error = null }, placeholder = "כל השיתופים", modifier = Modifier.weight(1f))
                        Field("משתמש", user, { user = it; error = null }, placeholder = "אורח", modifier = Modifier.weight(1f))
                    }
                    Field(
                        "סיסמה",
                        password,
                        { password = it; error = null },
                        keyboard = KeyboardType.Password,
                        secret = true,
                        placeholder = if (editing?.secret != null) "ללא שינוי" else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                        Text(
                            "הסיסמה נשמרת מוצפנת ב-${"Android Keystore".isolated()}, ולא יוצאת מהמכשיר.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (advanced) {
                    SectionLabel("מתקדם")
                    Card {
                        Field("שם במגירה", name, { name = it }, placeholder = host.trim().ifEmpty { null }, mono = false)
                        Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                            Field("פורט", port, { port = it.filter(Char::isDigit); error = null }, keyboard = KeyboardType.Number, placeholder = NetProtocol.Smb.defaultPort.toString(), modifier = Modifier.weight(1f))
                            Field("דומיין", domain, { domain = it }, placeholder = "—", modifier = Modifier.weight(1f))
                        }
                    }
                }

                (error ?: problem?.takeIf { host.isNotBlank() })?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, top = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, bottom = NexplorerDimens.Space4),
                ) {
                    Button(
                        onClick = ::connect,
                        enabled = problem == null && !busy,
                        shape = NexplorerShape.Pill,
                        modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton),
                    ) { Text(if (busy) "מתחבר…" else "בדוק והתחבר", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(
                        onClick = { advanced = !advanced },
                        shape = NexplorerShape.Pill,
                        modifier = Modifier.height(NexplorerDimens.BottomBarButton),
                    ) { Text("מתקדם") }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space1, top = NexplorerDimens.Space2),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NexplorerShape.ConnectCard)
            .background(MaterialTheme.colorScheme.surface)
            .padding(NexplorerDimens.Space4),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
    ) { content() }
}

@Composable
private fun TypeTile(type: PlaceType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        modifier = modifier
            // השקיפות על כל האריח — גם הרקע והמסגרת.
            .alpha(if (type.unavailable == null) 1f else UNAVAILABLE_ALPHA)
            .clip(NexplorerShape.ConnectTypeTile)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                if (selected) NexplorerDimens.ConnectTypeBorderSelected else NexplorerDimens.ConnectTypeBorder,
                if (selected) scheme.primary else scheme.outline,
                NexplorerShape.ConnectTypeTile,
            )
            .clickable(onClick = onClick)
            .padding(vertical = NexplorerDimens.Space3, horizontal = NexplorerDimens.Space1),
    ) {
        val color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
        Icon(type.icon, contentDescription = null, tint = color, modifier = Modifier.size(NexplorerDimens.ConnectTypeIcon))
        Text(
            type.label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun FoundCard(found: List<FoundServer>, searching: Boolean, selectedHost: String, onChoose: (FoundServer) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NexplorerShape.ConnectCard)
            .background(scheme.surface)
            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
    ) {
        if (found.isEmpty()) {
            Row(Modifier.heightIn(min = NexplorerDimens.ConnectFoundRow), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (searching) "מחפש שרתים ברשת…" else "לא נמצאו שרתים שמפרסמים את עצמם — הזן כתובת",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        found.forEachIndexed { index, server ->
            if (index > 0) HorizontalDivider(color = scheme.surfaceVariant)
            val chosen = server.host == selectedHost
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3),
                modifier = Modifier.heightIn(min = NexplorerDimens.ConnectFoundRow),
            ) {
                Box(Modifier.size(NexplorerDimens.ConnectStatusDot).background(NexplorerTheme.colors.success, NexplorerShape.Pill))
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${server.host} · ${server.protocol.label}",
                        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(NexplorerDimens.ConnectChooseButton)
                        .clip(NexplorerShape.Pill)
                        .background(if (chosen) scheme.primaryContainer else scheme.surfaceVariant)
                        .clickable { onChoose(server) }
                        .padding(horizontal = NexplorerDimens.Space3),
                ) {
                    Text("בחר", style = MaterialTheme.typography.labelLarge, color = if (chosen) scheme.onPrimaryContainer else scheme.onSurface)
                }
            }
        }
    }
}

/** שדה בלוח: תווית מעליו, מסגרת 1 בפינות 12, ערכים טכניים ב-Mono משמאל לימין. */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    placeholder: String? = null,
    mono: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    val style = (if (mono) MonoTextStyle else MaterialTheme.typography.bodyMedium).copy(color = scheme.onSurface)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = scheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            // התווית לקוראי מסך ולבדיקות — בלעדיה השדות זהים.
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NexplorerDimens.ConnectField)
                        .border(NexplorerDimens.ConnectTypeBorder, scheme.outline, NexplorerShape.ConnectField)
                        .padding(start = NexplorerDimens.ConnectFieldPadding, end = if (secret) NexplorerDimens.Space1 else NexplorerDimens.ConnectFieldPadding),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = scheme.onSurfaceVariant), maxLines = 1)
                        inner()
                    }
                    if (secret) {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "הסתר סיסמה" else "הצג סיסמה")
                        }
                    }
                }
            },
        )
    }
}

private const val UNAVAILABLE_ALPHA = 0.55f
