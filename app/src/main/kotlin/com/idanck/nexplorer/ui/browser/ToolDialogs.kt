package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.core.ArchiveFormat
import com.idanck.nexplorer.core.ArchiveFormats
import com.idanck.nexplorer.core.ArchivePaths
import com.idanck.nexplorer.core.BatchRename
import com.idanck.nexplorer.core.CaseChange
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.core.RenamePattern
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.archive.Archives
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import kotlinx.coroutines.launch

private val LtrField = TextDirection.Ltr

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = LtrField),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "הסתר סיסמה" else "הצג סיסמה")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "הוסף לארכיון": ZIP או 7z, השם, וסיסמה אופציונלית (AES-256 בשניהם). */
@Composable
fun CompressDialog(
    defaultName: (ArchiveFormat) -> String,
    count: Int,
    onConfirm: (String, ArchiveFormat, CharArray?) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.Zip) }
    var name by rememberSaveable { mutableStateOf(defaultName(ArchiveFormat.Zip)) }
    // הסיסמה לא נשמרת ב-Bundle (rememberSaveable) — רק בזיכרון של החלון.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val error = if (name.isBlank()) null else FileNames.validate(name)
    // אין שחזור לסיסמה שגויה — מקלידים פעמיים.
    val passwordOk = password.isEmpty() || password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוסף לארכיון") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Text(Counts.items(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    ArchiveFormat.entries.filter { it.creatable }.forEach { option ->
                        FilterChip(
                            selected = format == option,
                            onClick = {
                                format = option
                                name = ArchiveFormats.withExtension(name.ifBlank { defaultName(option) }, option)
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("שם הקובץ") },
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error) } } else null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = LtrField),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(password, { password = it }, "סיסמה (לא חובה)")
                if (password.isNotEmpty()) {
                    PasswordField(confirm, { confirm = it }, "הסיסמה שוב")
                    if (confirm.isNotEmpty() && !passwordOk) {
                        Text("הסיסמאות לא זהות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    when {
                        password.isNotEmpty() -> "הצפנת ${"AES-256".isolated()}. בלי הסיסמה אי אפשר לפתוח את הארכיון — אין שחזור"
                        format == ArchiveFormat.SevenZ -> "7z — קטן יותר, אבל לא כל מערכת פותחת אותו בלי תוכנה"
                        else -> "בלי סיסמה — ZIP רגיל שכל מערכת פותחת"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (password.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), format, password.takeIf { it.isNotEmpty() }?.toCharArray()) },
                enabled = name.isNotBlank() && error == null && passwordOk,
            ) { Text("צור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** "הצפן": הסיסמה פעמיים — אין שחזור. */
@Composable
fun EncryptDialog(count: Int, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    // הסיסמה לא נשמרת ב-Bundle (rememberSaveable) — רק בזיכרון של החלון.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val matches = password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הצפן") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Text(Counts.items(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(password, { password = it }, "סיסמה")
                PasswordField(confirm, { confirm = it }, "הסיסמה שוב")
                if (confirm.isNotEmpty() && !matches) {
                    Text("הסיסמאות לא זהות", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    // פותח בעברית: משפט שמתחיל ב-"AES" נפרש משמאל לימין, ו-".enc" מתהפך ל-"enc.".
                    "הצפנת ${"AES-256".isolated()}: כל קובץ מקבל את הסיומת ${".enc".isolated()}, והמקור נמחק אחרי שההצפנה נבדקה. " +
                        "בלי הסיסמה אי אפשר לפתוח את הקבצים — אין שחזור",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = password.isNotEmpty() && matches) { Text("הצפן") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/**
 * קובץ מוצפן (הקשה עליו) או "פענח" מהבחירה. "פתח" מפענח עותק זמני ופותח אותו בלי לשנות את הקובץ;
 * "פענח" מחזיר את המקור במקומו, בתור ההעברות. [onOpen] — null כשאין מה לפתוח (כמה פריטים, תיקייה);
 * מחזיר הודעת שגיאה, או null כשהקובץ נפתח. סגירת החלון באמצע הפתיחה מבטלת אותה.
 */
@Composable
fun DecryptDialog(
    title: String,
    subtitle: String,
    titleIsFileName: Boolean,
    onOpen: (suspend (password: CharArray, onProgress: (Float) -> Unit) -> String?)?,
    onDecrypt: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val opening = progress != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = if (titleIsFileName) FileNameTextStyle.merge(MaterialTheme.typography.titleLarge) else MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(password, { password = it; error = null }, "סיסמה")
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                progress?.let { done ->
                    // בהתחלה גוזרים את המפתח מהסיסמה (שנייה בערך) — עוד אין מה למדוד.
                    if (done <= 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onDecrypt(password.toCharArray()) }, enabled = password.isNotEmpty() && !opening) { Text("פענח") }
                if (onOpen != null) {
                    TextButton(
                        onClick = {
                            val secret = password.toCharArray()
                            // לפני launch: הקשה שנייה מהירה כבר רואה את הכפתור מושבת.
                            progress = 0f
                            scope.launch {
                                error = try {
                                    onOpen(secret) { progress = it }
                                } finally {
                                    secret.fill('\u0000')
                                    progress = null
                                }
                                if (error == null) onDismiss()
                            }
                        },
                        enabled = password.isNotEmpty() && !opening,
                    ) { Text("פתח") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/**
 * פתיחת ארכיון: מה יש בו, ולאן לחלץ. סיסמה — רק אם הוא מוצפן.
 * [inspect] רץ פעם אחת כשהחלון נפתח (ב-ZIP ו-7z רק הספרייה המרכזית — מהיר).
 */
@Composable
fun ExtractDialog(
    entry: Entry,
    inspect: suspend () -> Result<Archives.Info>,
    onExtract: (intoFolder: Boolean, password: CharArray?) -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit,
) {
    var info by remember { mutableStateOf<Result<Archives.Info>?>(null) }
    var password by remember { mutableStateOf("") }
    LaunchedEffect(entry.path) { info = inspect() }
    val loaded = info?.getOrNull()
    val folderName = ArchivePaths.extractFolderName(entry.name)
    val needsPassword = loaded?.encrypted == true
    val ready = loaded != null && (!needsPassword || password.isNotEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, style = FileNameTextStyle.merge(MaterialTheme.typography.titleLarge), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space3)) {
                Text(
                    when {
                        info == null -> "קורא את הארכיון…"
                        loaded == null -> info?.exceptionOrNull()?.message ?: "לא ניתן לקרוא את הארכיון"
                        else -> listOfNotNull(
                            loaded.format.label.isolated(),
                            loaded.entries?.let { Counts.files(it) },
                            loaded.totalBytes?.let { "${SizeFormat.short(it).isolated()} אחרי חילוץ" },
                            "מוגן בסיסמה".takeIf { loaded.encrypted },
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (info != null && loaded == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (needsPassword) PasswordField(password, { password = it }, "סיסמה")
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onExtract(true, password.takeIf { it.isNotEmpty() }?.toCharArray()) }, enabled = ready) {
                    Text("חלץ לתיקייה ${folderName.isolated()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { onExtract(false, password.takeIf { it.isNotEmpty() }?.toCharArray()) }, enabled = ready) {
                    Text("חלץ לכאן")
                }
                Row {
                    TextButton(onClick = onOpenWith) { Text("פתיחה באמצעות") }
                    TextButton(onClick = onDismiss) { Text("ביטול") }
                }
            }
        },
    )
}

/**
 * שינוי שם מרובה: תבנית, ותצוגה מקדימה חיה של כל השמות. שורות עם בעיה מסומנות באדום ולא מבוצעות;
 * הכפתור אומר כמה ישתנו בפועל.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchRenameDialog(
    entries: List<Entry>,
    others: suspend () -> Set<String>,
    onConfirm: (RenamePattern) -> Unit,
    onDismiss: () -> Unit,
) {
    var find by rememberSaveable { mutableStateOf("") }
    var replace by rememberSaveable { mutableStateOf("") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var suffix by rememberSaveable { mutableStateOf("") }
    var case by rememberSaveable { mutableStateOf(CaseChange.None) }
    var keepExtension by rememberSaveable { mutableStateOf(true) }
    var taken by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(entries) { taken = others() }
    val pattern = RenamePattern(find = find, replace = replace, prefix = prefix, suffix = suffix, case = case, keepExtension = keepExtension)
    val plan = remember(pattern, entries, taken) { BatchRename.plan(entries.map { it.name }, pattern, taken) }
    val changing = plan.count { it.changes }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי שם · ${Counts.items(entries.size)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    PatternField(find, { find = it }, "חפש", Modifier.weight(1f))
                    PatternField(replace, { replace = it }, "החלף ב-", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    PatternField(prefix, { prefix = it }, "בהתחלה", Modifier.weight(1f))
                    PatternField(suffix, { suffix = it }, "בסוף", Modifier.weight(1f))
                }
                Text(
                    "${BatchRename.NUMBER_TOKEN.isolated()} בכל שדה = מספר רץ (01, 02…)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // שלוש גלולות בעברית לא נכנסות בשורה אחת ברוחב של חלון בטלפון.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                    CaseChange.entries.forEach { option ->
                        FilterChip(selected = case == option, onClick = { case = option }, label = { Text(option.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepExtension, onCheckedChange = { keepExtension = it })
                    Text("שמור את הסיומת", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = NexplorerDimens.PreviewListMaxHeight)) {
                    items(plan) { step ->
                        Column(Modifier.padding(vertical = NexplorerDimens.Space1)) {
                            Text(step.from, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (step.problem != null) "${step.to.isolated()} — ${step.problem}" else "→ ${step.to}".takeIf { step.changes } ?: "ללא שינוי",
                                style = if (step.problem == null && step.changes) MonoTextStyle else MaterialTheme.typography.bodySmall,
                                color = when {
                                    step.problem != null -> MaterialTheme.colorScheme.error
                                    step.changes -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pattern) }, enabled = changing > 0) { Text(if (changing > 0) "שנה $changing" else "שנה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun PatternField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
        modifier = modifier,
    )
}
