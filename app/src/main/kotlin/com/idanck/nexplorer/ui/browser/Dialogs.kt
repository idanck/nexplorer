@file:Suppress("DEPRECATION") // אייקונים שקיבלו גרסת AutoMirrored זהים ויזואלית ואין בהם כיוון שצריך שיקוף.

package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.core.SortKey
import com.idanck.nexplorer.core.SortOrder
import com.idanck.nexplorer.core.ViewMode
import com.idanck.nexplorer.core.ViewOptions
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/**
 * חלון "אפשרויות תצוגה" בסגנון Solid (לוח ViewOptions): מצב תצוגה, מיון + כיוון, צפיפות ושלוש תיבות.
 * כל שינוי חל מייד על הרשימה שמאחור.
 */
@Composable
fun ViewOptionsDialog(
    initial: ViewOptions,
    initialOnlyThisFolder: Boolean,
    folderName: String?,
    onChange: (ViewOptions, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var view by remember { mutableStateOf(initial) }
    var onlyHere by remember { mutableStateOf(initialOnlyThisFolder) }
    fun push(next: ViewOptions = view, here: Boolean = onlyHere) {
        view = next
        onlyHere = here
        onChange(next, here)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .statusBarsPadding()
                .padding(top = NexplorerDimens.Fab),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = NexplorerShape.Popover,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = NexplorerDimens.Space3,
                modifier = Modifier
                    .width(NexplorerDimens.PopoverWidth)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(
                    Modifier.padding(horizontal = NexplorerDimens.Space5 - NexplorerDimens.Space1, vertical = NexplorerDimens.Space5 - NexplorerDimens.Space1 / 2),
                    verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4),
                ) {
                    Text("מצב תצוגה", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
                        ViewMode.entries.forEach { mode ->
                            OptionTile(
                                label = mode.label,
                                selected = view.mode == mode,
                                icon = when (mode) {
                                    ViewMode.List -> Icons.Outlined.FormatListBulleted
                                    ViewMode.Grid -> Icons.Outlined.GridView
                                    ViewMode.Gallery -> Icons.Outlined.PhotoLibrary
                                    ViewMode.Compact -> Icons.Outlined.Menu
                                },
                                modifier = Modifier.weight(1f),
                            ) { push(view.copy(mode = mode)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("מיון", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        Text(
                            if (view.sort.descending) "יורד ↓" else "עולה ↑",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, NexplorerShape.Pill)
                                .clickable { push(view.copy(sort = view.sort.copy(descending = !view.sort.descending))) }
                                .padding(horizontal = NexplorerDimens.Space3, vertical = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1)) {
                        SortKey.entries.forEach { key ->
                            OptionTile(
                                label = key.label,
                                selected = view.sort.key == key,
                                icon = when (key) {
                                    SortKey.Name -> null
                                    SortKey.Date -> Icons.Outlined.CalendarToday
                                    SortKey.Size -> Icons.Outlined.Sort
                                    SortKey.Type -> Icons.Outlined.InsertDriveFile
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                // לחיצה שנייה על המיון הנבחר הופכת כיוון.
                                val sort = if (view.sort.key == key) view.sort.copy(descending = !view.sort.descending) else SortOrder.defaultFor(key)
                                push(view.copy(sort = sort))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("צפיפות", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Slider(
                        value = view.density.toFloat(),
                        onValueChange = { push(view.copy(density = it.toInt().coerceIn(0, ViewOptions.MAX_DENSITY))) },
                        valueRange = 0f..ViewOptions.MAX_DENSITY.toFloat(),
                        steps = ViewOptions.MAX_DENSITY - 1,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column {
                        CheckRow("הצג מדורים", view.showSections) { push(view.copy(showSections = it)) }
                        CheckRow("הצג קבצים מוסתרים", view.showHidden) { push(view.copy(showHidden = it)) }
                        if (folderName != null) {
                            CheckRow("רק בתיקייה זו", onlyHere, trailing = folderName) { push(here = it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionTile(label: String, selected: Boolean, icon: ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, NexplorerShape.IconTile)
            .clickable(onClick = onClick)
            .padding(top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
    ) {
        val tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint)
        } else {
            // "Aא" כמו בלוח — שם בשתי השפות.
            Text("Aא", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr), color = tint)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, trailing: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NexplorerDimens.TouchTarget)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** שם לתיקייה חדשה, קובץ חדש או שינוי שם. השם נכתב LTR (כלל BiDi); בשינוי שם הבסיס מסומן בלי הסיומת. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectionEnd = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectionEnd))) }
    val error = if (value.text.isEmpty()) null else FileNames.validate(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (value.text.isNotBlank() && error == null) onConfirm(value.text.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) { { Text(error) } } else null,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = value.text.isNotBlank() && error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** מחיקה היא בלתי הפיכה — האישור באדום. */
@Composable
fun ConfirmDeleteDialog(names: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "למחוק את הפריט?" else "למחוק ${names.size} פריטים?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2)) {
                names.take(5).forEach { Text(it, style = MonoTextStyle, maxLines = 1) }
                if (names.size > 5) Text("ועוד ${names.size - 5}…", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.size(NexplorerDimens.Space1))
                Text("הפעולה בלתי הפיכה — הקבצים לא עוברים לסל מחזור.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("מחק")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** תוכן לוח הגזירים (הקשה על הצ'יפ "N בלוח"). */
@Composable
fun ClipboardDialog(clipboard: Clipboard, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (clipboard.cut) "בלוח — להעברה" else "בלוח — להעתקה") },
        text = {
            LazyColumn(Modifier.heightIn(max = NexplorerDimens.DrawerWidth)) {
                items(clipboard.paths) { path ->
                    Text(path, style = MonoTextStyle, modifier = Modifier.padding(vertical = NexplorerDimens.Space1))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        dismissButton = { TextButton(onClick = { onClear(); onDismiss() }) { Text("נקה את הלוח") } },
    )
}
