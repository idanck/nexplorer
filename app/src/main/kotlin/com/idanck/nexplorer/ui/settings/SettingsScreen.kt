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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.idanck.nexplorer.data.settings.AppSettings
import com.idanck.nexplorer.data.settings.ThemeMode
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
                    Divider()
                    SwitchItem("צבע דינמי מהטפט", "כבוי — נשאר הכחול של האפליקציה", checked = false, enabled = false) {}
                    Divider()
                    Item("שפה", "עברית") {}
                }

                Group("התנהגות") {
                    SwitchItem("אישור לפני מחיקה", "מחיקה היא בלתי הפיכה", s.confirmDelete, onChange = model.onConfirmDelete)
                    Divider()
                    SwitchItem("הצג קבצים מוסתרים", "קבצים ותיקיות שמתחילים בנקודה", s.defaultView.showHidden, onChange = model.onShowHidden)
                    Divider()
                    SwitchItem("פתח בתיקייה האחרונה", s.lastFolder?.substringAfterLast('/'), s.reopenLastFolder, onChange = model.onReopenLast)
                    Divider()
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

                Group("גישה לאחסון") {
                    Item("גישה לכל הקבצים", onClick = model.onStorageAccess) {
                        StatusChip(if (model.storageGranted) "ניתנה" else "לא ניתנה", ok = model.storageGranted)
                    }
                    Divider()
                    Item("Shizuku", "פותח Android/data ו-Android/obb") { StatusChip("בקרוב", ok = null) }
                    Divider()
                    Item("Root") { StatusChip("לא זמין", ok = null) }
                }

                Group("עדכונים") { UpdatesSection(model) }

                Group("אודות") {
                    Item("nexplorer", "מנהל קבצים · שפת העיצוב \"סלע ואות\"") {}
                    Divider()
                    Item("גרסה") { Text(BuildConfig.VERSION_NAME, style = MonoTextStyle) }
                    Divider()
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = NexplorerDimens.Space4),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NexplorerDimens.Space2),
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
        Divider()
        Item("התקנת עדכונים", "צריך לאשר פעם אחת ל-nexplorer להתקין אפליקציות", onClick = model.updateActions.onOpenInstallPermission) {
            StatusChip("לא אושר", ok = false)
        }
    }
    Divider()
    SwitchItem("בדיקה אוטומטית", "פעם ביום, ברקע", s.updateAutoCheck, onChange = model.onAutoCheck)
    Divider()
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

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NexplorerDimens.Space2, top = NexplorerDimens.Space3, bottom = NexplorerDimens.Space1),
    )
    Surface(shape = NexplorerShape.Card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = NexplorerDimens.Space1), content = content)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = NexplorerDimens.Space4))
}

@Composable
private fun Item(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NexplorerDimens.RowHeight - NexplorerDimens.Space2)
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
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
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
