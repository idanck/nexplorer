package com.idanck.nexplorer.ui.net

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idanck.nexplorer.core.DavAddress
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.net.CloudAuths
import com.idanck.nexplorer.data.net.CloudGrant
import com.idanck.nexplorer.data.net.ConnectCheck
import com.idanck.nexplorer.data.net.DriveFs
import com.idanck.nexplorer.data.net.FoundServer
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.NetDiscovery
import com.idanck.nexplorer.data.net.OneDriveFs
import com.idanck.nexplorer.data.net.QuickConnect
import com.idanck.nexplorer.data.net.SshHostKeys
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * סוג במסך "הוסף מקום" — לפי הלוח. SMB, ‏SFTP, ‏WebDAV וענן פעילים; לשאר — הסבר למה לא. "ענן" — Google Drive
 * כברירת מחדל, והשירות נבחר בפרטי החיבור.
 */
private enum class PlaceType(val label: String, val icon: ImageVector, val protocol: NetProtocol?, val unavailable: String?) {
    Smb("SMB", Icons.Outlined.Dns, NetProtocol.Smb, null),
    Sftp("SFTP", Icons.Outlined.Terminal, NetProtocol.Sftp, null),
    WebDav("WebDAV", Icons.Outlined.Language, NetProtocol.WebDav, null),
    Synology("Synology", NasIcon, NetProtocol.Synology, null),
    Cloud("ענן", Icons.Outlined.Cloud, NetProtocol.GoogleDrive, null),
    Ftp("FTP/S", Icons.Outlined.Download, null, "FTP/S לא בתוכנית: ב-D3 נבחרו SMB, SFTP ו-WebDAV"),
    S3("S3", Icons.Outlined.Storage, null, "S3 לא בתוכנית: ב-D3 נבחרו SMB, SFTP ו-WebDAV"),
    Vault("כספת", Icons.Outlined.Lock, null, "תיקייה מוצפנת נוצרת מהכפתור הצף: חדש ← תיקייה מוצפנת"),
}

/**
 * "הוסף מקום" (לוח "חיבור חדש"): סוג, שרתים שנמצאו ברשת, ופרטי החיבור. "בדוק והתחבר" מתחבר באמת
 * ([onConnect]) — רק חיבור שעבד נשמר. [editing] — "ערוך" מהמגירה: אותו מסך עם הפרטים השמורים, ושדה
 * סיסמה ריק משאיר את השמורה. ב-SFTP: מפתח פרטי מקובץ (במקום סיסמה), ושרת חדש — אישור טביעת האצבע שלו.
 * ענן: "התחבר" — בחלון של Google או בדף של Microsoft ([rememberCloudSignIn]), ואז [onCloudConnect].
 */
@Composable
fun ConnectScreen(
    editing: NetConnection?,
    discovery: NetDiscovery,
    /** הסיסמה והמפתח (קובץ שנבחר עכשיו) נמחקים אחרי הקריאה. */
    onConnect: suspend (NetConnection, CharArray?, CharArray?) -> ConnectCheck,
    onCloudConnect: suspend (NetConnection, CloudGrant) -> ConnectCheck,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val allFound by discovery.found.collectAsStateWithLifecycle()
    val searching by discovery.searching.collectAsStateWithLifecycle()

    var protocol by rememberSaveable { mutableStateOf(editing?.protocol ?: NetProtocol.Smb) }
    val sftp = protocol == NetProtocol.Sftp
    val dav = protocol == NetProtocol.WebDav
    // Synology (שלב 7): שרת, שיתוף (או כל השיתופים), משתמש וסיסמה — ה-API של DSM ב-https.
    val syno = protocol == NetProtocol.Synology
    // Synology דרך QuickConnect (שלב 7ג): מזהה במקום שרת; הכתובת נקבעת בכל התחברות (בבית ישירות, בחוץ בממסר).
    var quick by rememberSaveable { mutableStateOf(editing?.isQuickConnect == true) }
    var quickId by rememberSaveable { mutableStateOf(editing?.quickConnect.orEmpty()) }
    val synoQuick = syno && quick
    val cloud = protocol.cloud
    var host by rememberSaveable { mutableStateOf(editing?.host.orEmpty()) }
    // WebDAV: כתובת אחת (scheme, שרת, פורט ונתיב) במקום שרת ושיתוף.
    var url by rememberSaveable { mutableStateOf(editing?.takeIf { it.protocol == NetProtocol.WebDav }?.let { it.displayUrl(it.root) }.orEmpty()) }
    // SFTP: התיקייה נשמרת בלי / בקצוות; בשדה — כנתיב מלא ("/" — השורש של השרת; ריק — הבית).
    var share by rememberSaveable { mutableStateOf(editing?.let { e -> if (e.protocol == NetProtocol.Sftp) "/${e.share}" else e.share }.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(editing?.user.orEmpty()) }
    // הסיסמה והמפתח לא נשמרים ב-Bundle: סיבוב מסך מנקה אותם, וזה בסדר.
    var password by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf<String?>(null) }
    var keyName by remember { mutableStateOf<String?>(null) }
    var keepKey by rememberSaveable { mutableStateOf(editing?.keySecret != null) }
    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(editing?.port?.toString().orEmpty()) }
    var domain by rememberSaveable { mutableStateOf(editing?.domain.orEmpty()) }
    var advanced by rememberSaveable { mutableStateOf(editing?.let { it.port != null || it.domain.isNotBlank() || it.name.isNotBlank() } ?: false) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var trust by remember { mutableStateOf<Pair<NetConnection, ConnectCheck.TrustHost>?>(null) }
    // מפתח שאושר כאן ("שרת:פורט" ← המפתח): סיסמה שגויה אחרי האישור לא שואלת שוב על אותו שרת.
    var trusted by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasKey = sftp && (keyText != null || keepKey)
    val davAddress = if (dav) DavAddress.parse(url) else null
    // סיסמה שמורה נשארת רק כשדרך ההתחברות לא השתנתה: אותה סיסמה, או אותו מפתח (לא קובץ מפתח חדש).
    val keepsPassword = editing != null && editing.secret != null && (editing.keySecret != null) == hasKey && (editing.keySecret == null || keepKey) &&
        // מ-https ל-http: הסיסמה השמורה לא נשלחת גלויה בלי שהוזנה מחדש.
        !(editing.protocol == NetProtocol.WebDav && !editing.plainHttp && davAddress?.plainHttp == true)
    // Synology: ה-NAS מפרסם את עצמו כשרת SMB — אותה כתובת, והפורט של DSM.
    val found = allFound.filter { it.protocol == protocol || (syno && it.protocol == NetProtocol.Smb) }

    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { readKeyFile(context, uri) } }
                    .onSuccess { (fileName, text) ->
                        keyText = text
                        keyName = fileName
                        keepKey = false
                        error = null
                    }
                    .onFailure { error = it.message ?: "לא ניתן לקרוא את הקובץ" }
            }
        }
    }

    val portValue = port.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val problem = when {
        protocol == NetProtocol.OneDrive && CloudAuths.microsoftClientId.isBlank() ->
            "OneDrive עוד לא מוגדר בגרסה הזו: האפליקציה צריכה להיות רשומה אצל Microsoft"
        cloud -> null
        dav && url.isBlank() -> "צריך כתובת של שרת"
        dav && davAddress == null -> "כתובת לא תקינה — למשל https://cloud.example.com/remote.php/dav/files/idan"
        dav -> null
        synoQuick && quickId.isBlank() -> "צריך מזהה QuickConnect"
        synoQuick && !QuickConnect.isValidId(quickId.trim()) -> "מזהה QuickConnect — אותיות באנגלית, ספרות ומקף (בלי .quickconnect.to)"
        synoQuick && user.isBlank() -> "צריך שם משתמש"
        synoQuick -> null
        host.isBlank() -> "צריך כתובת של שרת"
        host.trim().any { it.isWhitespace() || it == '/' || it == '\\' } -> "כתובת השרת בלי רווחים ובלי / — רק השם או ה-IP"
        // "nas:4450" — פורט בתוך הכתובת (נקודתיים אחת; IPv6 עובר). הפורט בשדה שלו.
        host.trim().count { it == ':' } == 1 -> "הפורט בשדה \"פורט\" שב\"מתקדם\", לא בכתובת"
        port.isNotBlank() && (portValue == null || portValue !in 1..65535) -> "פורט — מספר בין 1 ל-65535"
        // ל-SSH ול-DSM אין "אורח".
        (sftp || syno) && user.isBlank() -> "צריך שם משתמש"
        !sftp && share.isNotBlank() -> NetPaths.validateName(NetProtocol.Smb, share.trim())
        else -> null
    }

    fun draft(): NetConnection {
        // ענן: השרת של ה-API קבוע, והחשבון נקבע בהתחברות.
        if (cloud) {
            return NetConnection(
                id = editing?.id ?: NetConnection.newId(),
                protocol = protocol,
                name = name.trim(),
                host = if (protocol == NetProtocol.GoogleDrive) DriveFs.HOST else OneDriveFs.HOST,
                port = null,
                share = "",
                user = editing?.user.orEmpty(),
            )
        }
        val base = NetConnection(
            id = editing?.id ?: NetConnection.newId(),
            protocol = protocol,
            name = name.trim(),
            host = if (synoQuick) "" else davAddress?.host ?: host.trim(),
            port = if (dav) davAddress?.port else portValue?.takeIf { it != protocol.defaultPort && !synoQuick },
            share = when {
                dav -> davAddress?.path.orEmpty()
                synoQuick -> ""
                sftp && share.isBlank() -> NetConnection.HOME
                else -> share.trim().trim('/')
            },
            user = user.trim(),
            domain = if (sftp || dav || syno) "" else domain.trim(),
            secret = editing?.secret?.takeIf { keepsPassword },
            keySecret = editing?.keySecret?.takeIf { hasKey && keepKey },
            plainHttp = davAddress?.plainHttp ?: false,
            quickConnect = if (synoQuick) quickId.trim().lowercase() else "",
        )
        val address = addressOf(base)
        val place = editing?.takeIf { addressOf(it) == address }
        // שרת אחר (כתובת או פורט) — המפתח או התעודה שלו צריכים אישור מחדש. ב-http אין מה לאשר.
        val hostKey = (trusted?.takeIf { it.first == address }?.second ?: place?.hostKey)?.takeIf { sftp || ((dav || syno) && !base.plainHttp && !base.isQuickConnect) }
        return base.copy(hostKey = hostKey)
    }

    fun connect(draft: NetConnection) {
        val secret = password.takeIf { it.isNotEmpty() }?.toCharArray()
        val key = keyText?.takeIf { sftp }?.toCharArray()
        busy = true
        error = null
        scope.launch {
            val result = try {
                onConnect(draft, secret, key)
            } finally {
                secret?.fill('\u0000')
                key?.fill('\u0000')
                busy = false
            }
            when (result) {
                is ConnectCheck.Ok -> onClose()
                is ConnectCheck.Failed -> error = result.message
                is ConnectCheck.TrustHost -> trust = draft to result
            }
        }
    }

    val signIn = rememberCloudSignIn(
        onGrant = { grant ->
            val draft = draft()
            scope.launch {
                val result = try {
                    onCloudConnect(draft, grant)
                } finally {
                    busy = false
                }
                when (result) {
                    is ConnectCheck.Ok -> onClose()
                    is ConnectCheck.Failed -> error = result.message
                    is ConnectCheck.TrustHost -> error = "תשובה לא צפויה"
                }
            }
        },
        onFailed = { message ->
            busy = false
            error = message
        },
    )

    trust?.let { (draft, ask) ->
        HostKeyDialog(
            host = draft.host,
            ask = ask,
            onTrust = {
                trust = null
                trusted = addressOf(draft) to ask.hostKey
                connect(draft.copy(hostKey = ask.hostKey))
            },
            onDismiss = { trust = null },
        )
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
                            TypeTile(type, selected = type.protocol == protocol || (type == PlaceType.Cloud && cloud), modifier = Modifier.weight(1f)) {
                                // "ענן" — השירות שכבר נבחר, אם נבחר.
                                val chosen = if (type == PlaceType.Cloud && cloud) protocol else type.protocol
                                if (chosen == null) {
                                    note = type.unavailable
                                } else if (editing != null && chosen != protocol) {
                                    // מועדפים ותצוגות בתוך החיבור תלויים בסוג שלו.
                                    note = "בעריכה הסוג לא משתנה — כדי לעבור ל-${type.label}, הוסף מקום חדש"
                                } else {
                                    protocol = chosen
                                    note = null
                                    error = null
                                }
                            }
                        }
                    }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                if (syno) {
                    SectionLabel("איך מתחבר")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false to "רשת ביתית", true to "QuickConnect").forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = quick == value,
                                onClick = {
                                    quick = value
                                    error = null
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                icon = {},
                            ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }

                if (!cloud && !synoQuick) {
                    SectionLabel("נמצאו ברשת")
                    FoundCard(found, searching, selectedHost = host.trim()) { server ->
                        host = server.host
                        if (server.protocol == protocol && server.port != protocol.defaultPort) {
                            port = server.port.toString()
                            advanced = true
                        }
                        if (name.isBlank()) name = server.name
                    }
                }

                SectionLabel("פרטי חיבור")
                if (cloud) Card {
                    CloudServices(protocol, enabled = editing == null && !busy) {
                        protocol = it
                        error = null
                    }
                    editing?.user?.takeIf { it.isNotBlank() }?.let { CloudAccount(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
                        Text(
                            if (protocol == NetProtocol.GoogleDrive) {
                                "ההתחברות בחלון של Google — הסיסמה לא עוברת באפליקציה, וההרשאה נשמרת ב-${"Google Play services".isolated()}."
                            } else {
                                "ההתחברות בדף של Microsoft — הסיסמה לא עוברת באפליקציה, וההרשאה נשמרת מוצפנת ב-${"Android Keystore".isolated()}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else Card {
                    if (synoQuick) {
                        Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
                            Field("מזהה QuickConnect", quickId, { quickId = it.trim(); error = null }, keyboard = KeyboardType.Uri, placeholder = "my-nas")
                            Text(
                                "ב-DSM: לוח הבקרה ← גישה חיצונית ← QuickConnect",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (dav) {
                        Field("כתובת", url, { url = it; error = null }, keyboard = KeyboardType.Uri, placeholder = "https://cloud.example.com/remote.php/dav/files/…")
                    } else {
                        Field("שרת", host, { host = it; error = null }, keyboard = KeyboardType.Uri)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                        if (dav) {
                            Field("משתמש", user, { user = it; error = null }, placeholder = "בלי", modifier = Modifier.weight(1f))
                        } else if (synoQuick) {
                            Field("משתמש", user, { user = it; error = null }, modifier = Modifier.weight(1f))
                        } else if (sftp) {
                            Field("תיקייה", share, { share = it; error = null }, placeholder = "תיקיית הבית", modifier = Modifier.weight(1f))
                            Field("משתמש", user, { user = it; error = null }, modifier = Modifier.weight(1f))
                        } else {
                            Field("שיתוף", share, { share = it; error = null }, placeholder = "כל השיתופים", modifier = Modifier.weight(1f))
                            Field("משתמש", user, { user = it; error = null }, placeholder = if (syno) null else "אורח", modifier = Modifier.weight(1f))
                        }
                    }
                    Field(
                        if (hasKey) "סיסמת המפתח" else "סיסמה",
                        password,
                        { password = it; error = null },
                        keyboard = KeyboardType.Password,
                        secret = true,
                        placeholder = when {
                            keepsPassword -> "ללא שינוי"
                            hasKey -> "אם יש"
                            else -> null
                        },
                    )
                    if (sftp) {
                        KeyRow(
                            name = keyName ?: if (keepKey) "המפתח השמור" else null,
                            onPick = { pickKey.launch(arrayOf("*/*")) },
                            onRemove = {
                                keyText = null
                                keyName = null
                                keepKey = false
                                error = null
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                        Icon(
                            if (synoQuick) Icons.AutoMirrored.Outlined.ArrowForward else Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(NexplorerDimens.ConnectHintIcon),
                        )
                        Text(
                            when {
                                synoQuick -> "בבית — ישירות ל-NAS, במהירות מלאה. מחוץ לבית — דרך ממסר של Synology, וזה איטי בהרבה: וידאו באיכות גבוהה עלול לעצור."
                                sftp -> "הסיסמה והמפתח נשמרים מוצפנים ב-${"Android Keystore".isolated()}, ולא יוצאים מהמכשיר."
                                syno -> "הסיסמה נשמרת מוצפנת ב-${"Android Keystore".isolated()}. ה-NAS זמין ברשת הביתית — מבחוץ דרך VPN, בלי פורט פתוח בראוטר."
                                // http: הסיסמה מוצפנת במכשיר, אבל עוברת גלויה ברשת.
                                davAddress?.plainHttp == true -> "בלי הצפנה (${"http".isolated()}): הסיסמה והקבצים עוברים ברשת גלויים. במכשיר הסיסמה נשמרת מוצפנת."
                                else -> "הסיסמה נשמרת מוצפנת ב-${"Android Keystore".isolated()}, ולא יוצאת מהמכשיר."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (advanced) {
                    SectionLabel("מתקדם")
                    Card {
                        Field(
                            "שם במגירה",
                            name,
                            { name = it },
                            placeholder = (if (cloud) editing?.user?.ifBlank { null } ?: protocol.label else if (synoQuick) quickId.trim() else host.trim()).ifEmpty { null },
                            mono = false,
                        )
                        // WebDAV: הפורט בתוך הכתובת. בענן וב-QuickConnect אין שרת לבחור.
                        if (!dav && !cloud && !synoQuick) Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2)) {
                            Field("פורט", port, { port = it.filter(Char::isDigit); error = null }, keyboard = KeyboardType.Number, placeholder = protocol.defaultPort.toString(), modifier = Modifier.weight(1f))
                            if (!sftp && !syno) Field("דומיין", domain, { domain = it }, placeholder = "—", modifier = Modifier.weight(1f))
                        }
                    }
                }

                (error ?: problem?.takeIf { cloud || (if (dav) url else if (synoQuick) quickId else host).isNotBlank() })?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (busy && synoQuick) {
                    Text("מאתר את ה-NAS דרך QuickConnect — ברשת הזו, או דרך הממסר…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        onClick = {
                            if (cloud) {
                                busy = true
                                error = null
                                signIn.start(protocol)
                            } else {
                                connect(draft())
                            }
                        },
                        enabled = problem == null && !busy,
                        shape = NexplorerShape.Pill,
                        modifier = Modifier.weight(1f).height(NexplorerDimens.BottomBarButton),
                    ) {
                        Text(
                            when {
                                busy -> "מתחבר…"
                                cloud && editing != null -> "התחבר מחדש"
                                cloud -> "התחבר"
                                else -> "בדוק והתחבר"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
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

/** שרת אחד, לעניין אישור מפתח או תעודה: הסוג, http או לא, השרת והפורט. */
private fun addressOf(c: NetConnection): String = "${c.protocol.name}:${if (c.plainHttp) "http" else ""}//${c.host}:${c.effectivePort}"

/**
 * שרת SSH חדש, או שהמפתח שלו השתנה: טביעת האצבע (SHA256, כמו ש-ssh מציג) לפני שממשיכים. ב-WebDAV —
 * התעודה העצמית של השרת (SHA-256, כמו בפרטי התעודה בדפדפן). אחרי האישור היא נשמרת בחיבור, וחיבור
 * לשרת עם מפתח או תעודה אחרים נחסם.
 */
@Composable
private fun HostKeyDialog(host: String, ask: ConnectCheck.TrustHost, onTrust: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val certificate = ask.hostKey.startsWith(CERTIFICATE_PREFIX)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    !ask.changed -> "לסמוך על השרת?"
                    certificate -> "התעודה של השרת השתנתה"
                    else -> "המפתח של השרת השתנה"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                if (certificate) {
                    Text("טביעת האצבע של התעודה של ${host.isolated()} (${"SHA-256".isolated()}):")
                    Text(ask.hostKey.removePrefix(CERTIFICATE_PREFIX), style = MonoTextStyle, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("טביעת האצבע של ${host.isolated()} (${SshHostKeys.typeLabel(ask.hostKey).isolated()}):")
                    Text(SshHostKeys.fingerprint(ask.hostKey) ?: ask.hostKey, style = MonoTextStyle, modifier = Modifier.fillMaxWidth())
                }
                if (ask.changed) {
                    Text(
                        if (certificate) "אם לא החלפת לשרת תעודה — ייתכן שמישהו מתחזה אליו. אל תמשיך."
                        else "אם לא התקנת את השרת מחדש ולא החלפת לו מפתח — ייתכן שמישהו מתחזה אליו. אל תמשיך.",
                        color = scheme.error,
                    )
                } else if (certificate) {
                    Text(
                        "התעודה לא חתומה על ידי רשות מוכרת (תעודה עצמית, כמו בשרת בבית). בדוק שזו הטביעה שהשרת מציג — " +
                            "בדפדפן, בפרטי התעודה. היא נשמרת, וחיבור עם תעודה עצמית אחרת ייחסם.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "בדוק שזה השרת שלך: בשרת, ${"ssh-keygen -lf /etc/ssh/ssh_host_${SshHostKeys.typeLabel(ask.hostKey).lowercase()}_key.pub".isolated()} מציג את אותה טביעה. " +
                            "היא נשמרת, וחיבור לשרת עם מפתח אחר ייחסם.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onTrust,
                colors = if (ask.changed) ButtonDefaults.textButtonColors(contentColor = scheme.error) else ButtonDefaults.textButtonColors(),
            ) { Text(if (ask.changed) (if (certificate) "סמוך על התעודה החדשה" else "סמוך על המפתח החדש") else "סמוך והתחבר") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** מפתח פרטי (SFTP): הקובץ שנבחר, או השמור בעריכה; "בחר קובץ" פותח את בורר הקבצים של המערכת. */
@Composable
private fun KeyRow(name: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
        Text("מפתח פרטי", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing), color = scheme.onSurfaceVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(NexplorerDimens.ConnectField)
                .border(NexplorerDimens.ConnectTypeBorder, scheme.outline, NexplorerShape.ConnectField)
                .padding(start = NexplorerDimens.ConnectFieldPadding, end = NexplorerDimens.Space1),
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = if (name != null) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(NexplorerDimens.ConnectHintIcon))
            Text(
                name ?: "בלי — התחברות בסיסמה",
                style = if (name != null) MonoTextStyle.copy(color = scheme.onSurface) else MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2),
            )
            if (name != null) TextButton(onClick = onRemove) { Text("הסר") }
            TextButton(onClick = onPick) { Text(if (name == null) "בחר קובץ" else "החלף") }
        }
    }
}

/** קובץ מפתח: השם (לתצוגה) והתוכן. מפתח ציבורי או קובץ אחר — הודעה ברורה במקום כשל בהתחברות. */
private fun readKeyFile(context: Context, uri: Uri): Pair<String, String> {
    val resolver = context.contentResolver
    val fileName = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "מפתח"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= MAX_KEY_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: throw IOException("לא ניתן לקרוא את הקובץ")
    if (bytes.size > MAX_KEY_BYTES) throw IOException("הקובץ גדול מדי בשביל מפתח")
    val text = String(bytes, Charsets.UTF_8)
    bytes.fill(0)
    val start = text.trimStart()
    if (start.startsWith("ssh-") || start.startsWith("ecdsa-") || start.startsWith("sk-")) {
        throw IOException("זה המפתח הציבורי (.pub) — בחר את הקובץ שבלי .pub")
    }
    if (!start.startsWith("-----BEGIN") && !start.startsWith("PuTTY-User-Key-File")) throw IOException("הקובץ שנבחר אינו מפתח פרטי של SSH")
    return fileName to text
}

private const val MAX_KEY_BYTES = 64 * 1024
private const val CERTIFICATE_PREFIX = "x509-sha256 "

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
