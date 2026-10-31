package com.idanck.nexplorer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.BuildConfig
import com.idanck.nexplorer.core.HebrewDates
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.OutlinedTextField
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.settings.AppSettings
import com.idanck.nexplorer.data.settings.ThemeMode
import com.idanck.nexplorer.ui.components.SegmentedGroup
import com.idanck.nexplorer.ui.components.segment
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import com.idanck.nexplorer.ui.update.UpdateActions
import com.idanck.nexplorer.update.UpdateBlocker
import com.idanck.nexplorer.update.UpdateState
import java.time.ZoneId

class SettingsModel(
    val settings: AppSettings,
    val storageGranted: Boolean,
    val updatesEnabled: Boolean,
    val update: UpdateState,
    val canInstall: Boolean,
    val onTheme: (ThemeMode) -> Unit,
    val onConfirmDelete: (Boolean) -> Unit,
    val onShowHidden: (Boolean) -> Unit,
    val onReopenLast: (Boolean) -> Unit,
    /** "תיקיות מוסתרות": החזרה לרשימה. */
    val onUnhideFolder: (String) -> Unit,
    val onStorageAccess: () -> Unit,
    val onCheckUpdates: () -> Unit,
    val onAutoCheck: (Boolean) -> Unit,
    val onWifiOnly: (Boolean) -> Unit,
    val updateActions: UpdateActions,
    val onBack: () -> Unit,
    /** "העברה בין מכשירים" (1.16, לוח Peer-Settings). */
    val peers: PeerSettings? = null,
)

/** המדור "העברה בין מכשירים": גלוי, שם, תיקיית קבלה, ומכשירים מחוברים עם "נתק". */
class PeerSettings(
    val name: String,
    val visible: Boolean,
    val receiveDir: String,
    val devices: List<NetConnection>,
    val onVisible: (Boolean) -> Unit,
    val onRename: (String) -> Unit,
    val onUnpair: (NetConnection) -> Unit,
)

/** מסך ההגדרות (לוח "הגדרות"): מראה · התנהגות · גישה לאחסון · עדכונים · אודות. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: SettingsModel) {
    val s = model.settings
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space4, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = model.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Text("הגדרות", style = MaterialTheme.typography.headlineLarge)
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2),
                verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
            ) {
                Group("מראה") {
                    Item("ערכת צבע") {
                        SingleChoiceSegmentedButtonRow {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = s.theme == mode,
                                    onClick = { model.onTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                    icon = {},
                                ) { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    SwitchItem("צבע דינמי מהטפט", "כבוי — נשאר הכחול של האפליקציה", checked = false, enabled = false) {}
                    Item("שפה", "עברית") {}
                }

                Group("התנהגות") {
                    SwitchItem("אישור לפני מחיקה", "מחיקה היא בלתי הפיכה", s.confirmDelete, onChange = model.onConfirmDelete)
                    SwitchItem("הצג קבצים מוסתרים", "קבצים ותיקיות שמתחילים בנקודה", s.defaultView.showHidden, onChange = model.onShowHidden)
                    SwitchItem("פתח בתיקייה האחרונה", s.lastFolder?.substringAfterLast('/'), s.reopenLastFolder, onChange = model.onReopenLast)
                    var showHiddenFolders by remember { mutableStateOf(false) }
                    Item(
                        "תיקיות מוסתרות",
                        if (s.hiddenFolders.isEmpty()) "אין. בתיקייה: ⋮ ← אפשרויות תיקייה ← הסתר תיקייה" else "לא מוצגות ברשימה; במכשיר הן נשארות",
                        onClick = if (s.hiddenFolders.isEmpty()) null else ({ showHiddenFolders = true }),
                    ) {
                        if (s.hiddenFolders.isNotEmpty()) Text(s.hiddenFolders.size.toString(), style = MonoTextStyle)
                    }
                    if (showHiddenFolders && s.hiddenFolders.isNotEmpty()) {
                        HiddenFoldersDialog(s.hiddenFolders, model.onUnhideFolder) { showHiddenFolders = false }
                    }
                }

                model.peers?.let { PeersGroup(it) }

                Group("גישה לאחסון") {
                    Item("גישה לכל הקבצים", onClick = model.onStorageAccess) {
                        StatusChip(if (model.storageGranted) "ניתנה" else "לא ניתנה", ok = model.storageGranted)
                    }
                    Item("Shizuku", "פותח Android/data ו-Android/obb") { StatusChip("בקרוב", ok = null) }
                    Item("Root") { StatusChip("לא זמין", ok = null) }
                }

                Group("עדכונים") { UpdatesSection(model) }

                Group("אודות") {
                    Item("nexplorer", "מנהל קבצים · שפת העיצוב \"סלע ואות\"") {}
                    Item("גרסה") { Text(BuildConfig.VERSION_NAME, style = MonoTextStyle) }
                    Item("חבילה") { Text(BuildConfig.APPLICATION_ID, style = MonoTextStyle) }
                }
                Spacer(Modifier.size(NexplorerDimens.Space4))
            }
        }
    }
}

@Composable
private fun UpdatesSection(model: SettingsModel) {
    val s = model.settings
    if (!model.updatesEnabled) {
        Item("עדכונים מתוך האפליקציה", "כבויים בגרסת פיתוח (debug)") {}
        return
    }
    val status = when (val u = model.update) {
        UpdateState.Idle -> if (s.updateLastCheckUtc > 0) "נבדק לאחרונה: ${HebrewDates.short(s.updateLastCheckUtc, System.currentTimeMillis(), ZoneId.systemDefault())}" else "עוד לא נבדק"
        UpdateState.Checking -> "בודק…"
        is UpdateState.UpToDate -> "מעודכן — זו הגרסה האחרונה"
        is UpdateState.Available -> "גרסה ${u.manifest.versionName} זמינה (${SizeFormat.short(u.manifest.sizeBytes).isolated()})"
        is UpdateState.Downloading -> "מוריד… ${(u.fraction * 100).toInt()}%"
        is UpdateState.Verifying -> "מאמת…"
        is UpdateState.Installing -> if (u.awaitingUser) "ממתין לאישור בחלון של המערכת" else "מתקין…"
        is UpdateState.Failed -> u.message
        is UpdateState.Blocked -> u.blocker.message
    }
    Item("גרסה מותקנת", status) { Text(BuildConfig.VERSION_NAME, style = MonoTextStyle) }
    val u = model.update
    if (u is UpdateState.Downloading) {
        LinearProgressIndicator(
            progress = { u.fraction },
            color = NexplorerTheme.colors.amber,
            modifier = Modifier
                .segment(MaterialTheme.colorScheme.surface)
                .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space3),
        )
    }
    Row(
        Modifier.segment(MaterialTheme.colorScheme.surface).padding(horizontal = NexplorerDimens.Space2),
        horizontalArrangement = Arrangement.End,
    ) {
        when (u) {
            is UpdateState.Available, is UpdateState.Failed -> {
                val manifest = (u as? UpdateState.Available)?.manifest ?: (u as UpdateState.Failed).manifest
                if (manifest != null) {
                    TextButton(onClick = { model.updateActions.onInstall(manifest) }) { Text("עדכן עכשיו") }
                } else {
                    TextButton(onClick = model.onCheckUpdates) { Text("בדוק שוב") }
                }
            }
            is UpdateState.Blocked -> {
                if (u.blocker == UpdateBlocker.InstallPermission) {
                    TextButton(onClick = model.updateActions.onOpenInstallPermission) { Text("אשר התקנה מהאפליקציה") }
                } else {
                    TextButton(onClick = { model.updateActions.onInstall(u.manifest) }) { Text("נסה שוב") }
                }
            }
            is UpdateState.Installing -> if (u.awaitingUser) {
                TextButton(onClick = model.updateActions.onContinueInstall) { Text("המשך התקנה") }
            }
            UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Verifying -> Unit
            else -> TextButton(onClick = model.onCheckUpdates) { Text("בדוק עכשיו") }
        }
    }
    if (!model.canInstall) {
        Item("התקנת עדכונים", "צריך לאשר פעם אחת ל-nexplorer להתקין אפליקציות", onClick = model.updateActions.onOpenInstallPermission) {
            StatusChip("לא אושר", ok = false)
        }
    }
    SwitchItem("בדיקה אוטומטית", "פעם ביום, ברקע", s.updateAutoCheck, onChange = model.onAutoCheck)
    SwitchItem("רק ב-Wi-Fi", "לא להוריד עדכון על חבילת גלישה", s.updateWifiOnly, onChange = model.onWifiOnly)
}

/** התיקיות שהוסתרו: "הצג" מחזיר אחת לרשימה. נסגר לבד כשאין עוד. */
@Composable
private fun HiddenFoldersDialog(hidden: Set<String>, onUnhide: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תיקיות מוסתרות") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                hidden.sorted().forEach { path ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/').ifEmpty { path }, style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(path, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { onUnhide(path) }) { Text("הצג") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
    )
}

/** מדור בהגדרות (1.9): כותרת, ומתחתיה כרטיסים צמודים — פריט לכל שורה, בלי קווים מפרידים. */
@Composable
private fun PeersGroup(p: PeerSettings) {
    var renaming by remember { mutableStateOf(false) }
    var unpairing by remember { mutableStateOf<NetConnection?>(null) }
    Group("העברה בין מכשירים") {
        SwitchItem("גלוי למכשירים ברשת", "רק כשהאפליקציה פתוחה. מכשירים מחוברים יכולים לשלוח אליו ולעיין בקבצים שלו", p.visible, onChange = p.onVisible)
        Item("שם המכשיר", p.name, onClick = { renaming = true }) {
            Icon(Icons.Outlined.Edit, contentDescription = "שנה שם", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Item("תיקיית קבלה") { Text(p.receiveDir, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (p.devices.isNotEmpty()) {
        Group("מכשירים מחוברים") {
            p.devices.forEach { c ->
                Item(c.label, if (c.autoAccept) "מקבל בלי לשאול" else "שואל לפני כל קבלה") {
                    TextButton(onClick = { unpairing = c }) { Text("נתק", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (renaming) {
        var value by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("שם המכשיר") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    p.onRename(value)
                }) { Text("שמור") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("ביטול") } },
        )
    }
    unpairing?.let { c ->
        AlertDialog(
            onDismissRequest = { unpairing = null },
            title = { Text("לנתק את ${c.label}?") },
            text = { Text("כדי לשלוח או לעיין שוב — חיבור מחדש בקוד.") },
            confirmButton = {
                TextButton(onClick = {
                    unpairing = null
                    p.onUnpair(c)
                }) { Text("נתק", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { unpairing = null }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NexplorerDimens.Space3, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space1),
    )
    SegmentedGroup(content = content)
}

@Composable
private fun Item(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .segment(MaterialTheme.colorScheme.surface)
            .heightIn(min = NexplorerDimens.SettingsRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = NexplorerDimens.Space4, vertical = NexplorerDimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(NexplorerDimens.Space3))
        trailing()
    }
}

@Composable
private fun SwitchItem(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Item(title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        // מתג M3 עם ✓ על הידית כשהוא דולק (לוח "הגדרות", 1.9).
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

/** צ'יפ מצב: ירוק = פעיל, אדום-בהיר = חסר, אפור = לא רלוונטי עדיין. */
@Composable
private fun StatusChip(text: String, ok: Boolean?) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (ok) {
        true -> NexplorerTheme.colors.success.copy(alpha = 0.14f) to NexplorerTheme.colors.success
        false -> scheme.error.copy(alpha = 0.12f) to scheme.error
        null -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Row(
        Modifier
            .background(bg, NexplorerShape.Pill)
            .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ok == true) {
            Spacer(Modifier.size(NexplorerDimens.Space2).background(fg, NexplorerShape.Pill))
            Spacer(Modifier.width(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
