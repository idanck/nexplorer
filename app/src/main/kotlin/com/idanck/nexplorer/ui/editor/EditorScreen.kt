package com.idanck.nexplorer.ui.editor

import com.idanck.nexplorer.ui.tv.screenEntry
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.files.TextFiles
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorState(
    val path: String? = null,
    val loading: Boolean = true,
    /** null = עדיין לא נטען, או שלא ניתן לערוך (ראה [problem]). */
    val original: String? = null,
    val text: String = "",
    val problem: String? = null,
    val saving: Boolean = false,
    val message: String? = null,
) {
    val dirty: Boolean get() = original != null && text != original
}

/** "ערוך כטקסט" (לוח SelectList, תפריט ⋮): קובץ עד 1 MB, UTF-8, שמירה אטומית. */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state
    private var loadJob: Job? = null

    /**
     * תמיד קורא מהדיסק מחדש — הקובץ יכול היה להשתנות מאז הפעם הקודמת, ושמירה של טקסט ישן הייתה דורסת אותו.
     * החריג: אותו קובץ עם שינויים שלא נשמרו (סיבוב מסך, חזרה מהרקע) — לא זורקים אותם.
     */
    fun open(path: String) {
        val current = _state.value
        if (current.path == path && (current.dirty || current.loading)) return
        loadJob?.cancel()
        _state.value = EditorState(path = path)
        loadJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { TextFiles.load(File(path)) } }
            _state.update { s ->
                if (s.path != path) return@update s
                when (val r = loaded.getOrNull()) {
                    is TextFiles.Loaded.Text -> s.copy(loading = false, original = r.text, text = r.text)
                    is TextFiles.Loaded.TooLarge -> s.copy(loading = false, problem = "הקובץ גדול מדי לעריכה כאן (${SizeFormat.short(r.bytes).isolated()}) — פתח אותו באפליקציה אחרת")
                    TextFiles.Loaded.Binary -> s.copy(loading = false, problem = "זה לא נראה כמו קובץ טקסט (UTF-8). שמירה הייתה משחיתה אותו")
                    null -> s.copy(loading = false, problem = loaded.exceptionOrNull()?.message ?: "לא ניתן לקרוא את הקובץ")
                }
            }
        }
    }

    fun edit(text: String) = _state.update { it.copy(text = text, message = null) }

    fun save(then: () -> Unit = {}) {
        val s = _state.value
        val path = s.path ?: return
        if (!s.dirty || s.saving) {
            then()
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { TextFiles.save(File(path), s.text) } }
            _state.update {
                if (it.path != path) it
                else if (result.isSuccess) it.copy(saving = false, original = s.text, message = "נשמר")
                else it.copy(saving = false, message = "השמירה נכשלה: ${result.exceptionOrNull()?.message}")
            }
            if (result.isSuccess) then()
        }
    }

    fun discard() = _state.update { it.copy(text = it.original ?: it.text) }
}

@Composable
fun EditorScreen(viewModel: EditorViewModel, path: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askLeave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(path) { viewModel.open(path) }
    BackHandler { if (state.dirty) askLeave = true else onBack() }
    val name = File(path).name

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space2, top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (state.dirty) askLeave = true else onBack() }, modifier = Modifier.screenEntry()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = FileNameTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            state.saving -> "שומר…"
                            state.message != null -> state.message.orEmpty()
                            state.dirty -> "יש שינויים שלא נשמרו"
                            else -> "UTF-8".isolated()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { viewModel.save() }, enabled = state.dirty && !state.saving) { Text("שמור") }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.problem != null -> Column(Modifier.fillMaxSize().padding(NexplorerDimens.Space5), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.problem.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { FileIntents.openWith(context, path, name) }) { Text("פתיחה באמצעות") }
                    }
                    else -> BasicTextField(
                        value = state.text,
                        onValueChange = viewModel::edit,
                        // קוד וקונפיג באנגלית, הערות בעברית: כל שורה לפי התו החזק הראשון שלה.
                        textStyle = MonoTextStyle.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(NexplorerDimens.Space4),
                    )
                }
            }
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("לשמור את השינויים?") },
            text = { Text(name, style = FileNameTextStyle) },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    viewModel.save(then = onBack)
                }) { Text("שמור") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askLeave = false
                        viewModel.discard()
                        onBack()
                    }) { Text("אל תשמור", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { askLeave = false }) { Text("המשך לערוך") }
                }
            },
        )
    }
}
