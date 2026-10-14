package com.idanck.nexplorer.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.Section
import com.idanck.nexplorer.core.ViewOptions
import com.idanck.nexplorer.core.sectionsFor
import com.idanck.nexplorer.core.sortedFor
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.MediaCollection
import com.idanck.nexplorer.data.files.MediaCollections
import com.idanck.nexplorer.data.files.MediaScan
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.files.StorageVolumes
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.data.settings.relocated
import com.idanck.nexplorer.data.transfer.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

sealed interface Location {
    data class Folder(val path: String) : Location
    data class Media(val collection: MediaCollection) : Location
}

data class Clipboard(val paths: List<String>, val cut: Boolean)

data class Crumb(val label: String, val path: String, val isLtr: Boolean)

data class BrowserState(
    val location: Location,
    val volumes: List<StorageVolumeInfo> = emptyList(),
    /** כל מה שנרשם, לפני סינון ומיון. */
    val listed: List<Entry> = emptyList(),
    /** אחרי סינון ומיון — מה שמוצג. */
    val entries: List<Entry> = emptyList(),
    val sections: List<Section> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val view: ViewOptions = ViewOptions(),
    val viewIsFolderScoped: Boolean = false,
    val selection: Set<String> = emptySet(),
    /** null = שורת הסינון סגורה. */
    val filter: String? = null,
    val clipboard: Clipboard? = null,
    val favorites: List<String> = emptyList(),
) {
    val folderPath: String? get() = (location as? Location.Folder)?.path
    val volume: StorageVolumeInfo? get() = folderPath?.let { StorageVolumes.volumeOf(it, volumes) }
    val inSelection: Boolean get() = selection.isNotEmpty()
    val isFavorite: Boolean get() = folderPath != null && folderPath in favorites
    val isAtRoot: Boolean
        get() = when (val loc = location) {
            is Location.Media -> false
            is Location.Folder -> volume?.path == loc.path || loc.path == "/"
        }

    val title: String
        get() = when (val loc = location) {
            is Location.Media -> loc.collection.label
            is Location.Folder -> if (isAtRoot) volume?.label ?: "/" else File(loc.path).name
        }

    val titleIsLtr: Boolean get() = location is Location.Folder && !isAtRoot

    val crumbs: List<Crumb>
        get() = when (val loc = location) {
            is Location.Media -> listOf(Crumb(loc.collection.label, "", isLtr = false))
            is Location.Folder -> {
                val root = volume
                val rootPath = root?.path ?: ""
                val rest = loc.path.removePrefix(rootPath).trim('/').split('/').filter { it.isNotEmpty() }
                buildList {
                    add(Crumb(root?.label ?: "/", rootPath.ifEmpty { "/" }, isLtr = root == null))
                    var acc = rootPath
                    rest.forEach { segment ->
                        acc = "$acc/$segment"
                        add(Crumb(segment, acc, isLtr = true))
                    }
                }
            }
        }
}

sealed interface BrowserEvent {
    data class Message(val text: String) : BrowserEvent
    data class OpenFile(val entry: Entry) : BrowserEvent
}

/**
 * המצב של חלון הקבצים: מיקום, רשימה, בחירה, לוח גזירים ומסנן.
 * חי ברמת ה-Activity, כך שחזרה מההגדרות או מהחיפוש מוצאת את החלון כפי שהיה.
 */
class BrowserViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val container = (app as NexplorerApp).container
    private val settings = container.settings
    private val transfers = container.transfers

    // הנפחים נטענים מייד (StatFs זול): בלעדיהם הפריימים הראשונים מציגים "/ › storage › emulated › 0",
    // ו"חזרה" מיידית יוצאת מהשורש. המיקום ולוח הגזירים שורדים הריגת תהליך ברקע.
    private val _state = MutableStateFlow(
        BrowserState(location = initialLocation(), volumes = StorageVolumes.list(app), clipboard = savedClipboard()),
    )
    val state: StateFlow<BrowserState> = _state

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var selectionAnchor: String? = null
    private var resumedOnce = false

    /** מיקום הגלילה של כל תיקייה שביקרנו בה — "למעלה" חוזר לאותה שורה. */
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    init {
        applySettings(reload = false)
        load()
        viewModelScope.launch {
            // שינוי בהעדפות (מכאן או מההגדרות) — מיון מחדש; "הצג מוסתרים" — רישום מחדש.
            settings.state.drop(1).map { it.viewFor(_state.value.folderPath) to it.favorites }
                .distinctUntilChanged()
                .collect { applySettings(reload = true) }
        }
        viewModelScope.launch {
            transfers.finished.collect { transfer ->
                refresh()
                refreshVolumes()
                // מועדפים ותצוגה "רק בתיקייה זו" עוברים עם התיקייה.
                if (transfer.moved.isNotEmpty()) settings.update { s -> transfer.moved.fold(s) { acc, (from, to) -> acc.relocated(from, to) } }
                if (transfer.status == TransferStatus.Failed) {
                    emit("${transfer.kind.verb} נכשלה: ${transfer.error}")
                }
            }
        }
    }

    private fun initialLocation(): Location {
        saved.get<String>(KEY_LOCATION)?.let { key ->
            if (key.startsWith(MEDIA_PREFIX)) {
                MediaCollection.entries.firstOrNull { it.name == key.removePrefix(MEDIA_PREFIX) }?.let { return Location.Media(it) }
            } else if (File(key).isDirectory) {
                return Location.Folder(key)
            }
        }
        val s = settings.state.value
        val last = s.lastFolder?.takeIf { s.reopenLastFolder && File(it).isDirectory }
        return Location.Folder(last ?: StorageVolumes.primaryPath())
    }

    private fun savedClipboard(): Clipboard? {
        val paths = saved.get<ArrayList<String>>(KEY_CLIP_PATHS) ?: return null
        return Clipboard(paths, saved.get<Boolean>(KEY_CLIP_CUT) ?: false)
    }

    private fun saveClipboard(clip: Clipboard?) {
        // לוח ענק (בחר הכל ב-DCIM) לא נכנס ל-Bundle — עדיף לאבד אותו בהריגת תהליך מאשר לקרוס בשמירה.
        if (clip == null || clip.paths.size > MAX_SAVED_CLIP) {
            saved.remove<ArrayList<String>>(KEY_CLIP_PATHS)
            saved.remove<Boolean>(KEY_CLIP_CUT)
        } else {
            saved[KEY_CLIP_PATHS] = ArrayList(clip.paths)
            saved[KEY_CLIP_CUT] = clip.cut
        }
    }

    /** חזרה לאפליקציה: קבצים שהורדו או נערכו באפליקציה אחרת, ומקום פנוי עדכני. */
    fun onResume() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        refresh()
        refreshVolumes()
    }

    fun scrollFor(location: Location): Pair<Int, Int> = scrollPositions[keyOf(location)] ?: (0 to 0)

    fun saveScroll(location: Location, index: Int, offset: Int) {
        scrollPositions[keyOf(location)] = index to offset
    }

    private fun keyOf(location: Location): String = when (location) {
        is Location.Folder -> location.path
        is Location.Media -> MEDIA_PREFIX + location.collection.name
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) { StorageVolumes.list(getApplication()) }
            _state.update { it.copy(volumes = volumes) }
        }
    }

    // ---------- ניווט ----------

    fun open(entry: Entry) {
        if (_state.value.inSelection) {
            toggle(entry.path)
            return
        }
        if (entry.isDirectory) navigate(Location.Folder(entry.path)) else emitEvent(BrowserEvent.OpenFile(entry))
    }

    fun navigate(location: Location) {
        // בלי הרשימה הקודמת: אחרת שורות של התיקייה הקודמת מוצגות (ולחיצות) מתחת לכותרת החדשה עד שהרישום מסתיים.
        _state.update {
            it.copy(
                location = location, selection = emptySet(), filter = null, error = null,
                listed = emptyList(), entries = emptyList(), sections = emptyList(), loading = true,
            )
        }
        saved[KEY_LOCATION] = keyOf(location)
        selectionAnchor = null
        applySettings(reload = false)
        load()
        if (location is Location.Folder) {
            viewModelScope.launch { settings.update { it.copy(lastFolder = location.path) } }
        }
    }

    fun openPath(path: String) {
        val file = File(path)
        when {
            file.isDirectory -> navigate(Location.Folder(file.absolutePath))
            file.isFile -> navigate(Location.Folder(file.parentFile?.absolutePath ?: StorageVolumes.primaryPath()))
            else -> emit("הנתיב לא קיים")
        }
    }

    /** false בשורש — אז "חזרה" יוצאת מהאפליקציה. */
    fun goUp(): Boolean {
        val s = _state.value
        when (val loc = s.location) {
            is Location.Media -> navigate(Location.Folder(s.volumes.firstOrNull()?.path ?: StorageVolumes.primaryPath()))
            is Location.Folder -> {
                if (s.isAtRoot) return false
                val parent = File(loc.path).parentFile?.absolutePath ?: return false
                navigate(Location.Folder(parent))
            }
        }
        return true
    }

    fun refresh() = load()

    private fun load() {
        loadJob?.cancel()
        val location = _state.value.location
        val showHidden = _state.value.view.showHidden
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val result = when (location) {
                is Location.Folder -> LocalFiles.list(location.path, showHidden)
                is Location.Media -> MediaCollections.list(getApplication(), location.collection)
                    .map { list -> if (showHidden) list else list.filter { !it.isHidden } }
            }
            result
                .onSuccess { listed ->
                    // הרשימה, המיון והמדורים מתפרסמים יחד: בלי הבהוב של "התיקייה ריקה" לפני המיון.
                    val s = _state.value.copy(listed = listed)
                    val (entries, sections) = withContext(Dispatchers.Default) { arranged(s) }
                    _state.update {
                        if (it.location != location) it
                        else it.copy(
                            listed = listed, entries = entries, sections = sections, loading = false, error = null,
                            selection = it.selection.intersect(entries.map { e -> e.path }.toSet()),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(listed = emptyList(), entries = emptyList(), sections = emptyList(), loading = false, error = e.message)
                    }
                }
        }
    }

    private fun applySettings(reload: Boolean) {
        val s = settings.state.value
        val path = _state.value.folderPath
        val view = s.viewFor(path)
        val hiddenChanged = view.showHidden != _state.value.view.showHidden
        _state.update {
            it.copy(view = view, viewIsFolderScoped = path != null && path in s.folderViews, favorites = s.favorites)
        }
        if (reload && hiddenChanged) load() else arrange()
    }

    /** סינון, מיון וקיבוץ — מחוץ ל-main thread כי תיקייה יכולה להכיל אלפי פריטים. */
    private fun arranged(s: BrowserState): Pair<List<Entry>, List<Section>> {
        val filtered = s.filter?.takeIf { it.isNotBlank() }?.let { f ->
            s.listed.filter { it.name.contains(f, ignoreCase = true) }
        } ?: s.listed
        val sorted = filtered.sortedFor(s.view.sort)
        return sorted to if (s.view.showSections) sectionsFor(sorted, s.view.sort, System.currentTimeMillis(), ZoneId.systemDefault()) else emptyList()
    }

    private fun arrange() {
        viewModelScope.launch {
            val s = _state.value
            val (entries, sections) = withContext(Dispatchers.Default) { arranged(s) }
            _state.update { current ->
                // תוצאה של רשימה ישנה (ניווט באמצע) לא דורסת את החדשה.
                if (current.listed !== s.listed || current.filter != s.filter || current.view != s.view) current
                else current.copy(entries = entries, sections = sections, selection = current.selection.intersect(entries.map { it.path }.toSet()))
            }
        }
    }

    // ---------- תצוגה וסינון ----------

    fun setFilter(text: String?) {
        _state.update { it.copy(filter = text) }
        arrange()
    }

    fun setView(view: ViewOptions, onlyThisFolder: Boolean) {
        val path = _state.value.folderPath
        viewModelScope.launch {
            settings.update { s ->
                if (onlyThisFolder && path != null) s.copy(folderViews = s.folderViews + (path to view))
                else s.copy(defaultView = view, folderViews = if (path != null) s.folderViews - path else s.folderViews)
            }
        }
    }

    fun toggleFavorite(path: String? = _state.value.folderPath) {
        if (path == null) return
        viewModelScope.launch {
            settings.update { s -> s.copy(favorites = if (path in s.favorites) s.favorites - path else s.favorites + path) }
        }
    }

    fun removeFavorite(path: String) {
        viewModelScope.launch { settings.update { s -> s.copy(favorites = s.favorites - path) } }
    }

    // ---------- בחירה ----------

    fun toggle(path: String) {
        selectionAnchor = path
        _state.update { s -> s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path) }
    }

    /** לחיצה ארוכה: מתחילה בחירה, ובמצב בחירה בוחרת טווח מהעוגן (F3). */
    fun longPress(path: String) {
        val s = _state.value
        val anchor = selectionAnchor
        if (!s.inSelection || anchor == null) {
            toggle(path)
            return
        }
        val order = s.entries.map { it.path }
        val from = order.indexOf(anchor)
        val to = order.indexOf(path)
        if (from < 0 || to < 0) {
            toggle(path)
            return
        }
        val range = order.subList(minOf(from, to), maxOf(from, to) + 1)
        _state.update { it.copy(selection = it.selection + range) }
        selectionAnchor = path
    }

    fun selectAll() = _state.update { s -> s.copy(selection = s.entries.map { it.path }.toSet()) }

    fun invertSelection() = _state.update { s -> s.copy(selection = s.entries.map { it.path }.toSet() - s.selection) }

    fun clearSelection() {
        selectionAnchor = null
        _state.update { it.copy(selection = emptySet()) }
    }

    fun selectedEntries(): List<Entry> {
        val s = _state.value
        return s.entries.filter { it.path in s.selection }
    }

    // ---------- פעולות ----------

    fun copyToClipboard(paths: List<String>, cut: Boolean) {
        if (paths.isEmpty()) return
        val clip = Clipboard(paths, cut)
        _state.update { it.copy(clipboard = clip, selection = emptySet()) }
        saveClipboard(clip)
    }

    fun cancelClipboard() {
        _state.update { it.copy(clipboard = null) }
        saveClipboard(null)
    }

    fun paste() {
        val s = _state.value
        val clip = s.clipboard ?: return
        val target = s.folderPath ?: return
        transfers.enqueue(if (clip.cut) TransferKind.Move else TransferKind.Copy, clip.paths, target)
        _state.update { it.copy(clipboard = null) }
        saveClipboard(null)
    }

    fun delete(paths: List<String>) {
        if (paths.isEmpty()) return
        transfers.enqueue(TransferKind.Delete, paths, null)
        clearSelection()
    }

    fun createFolder(name: String) = runOp { LocalFiles.createFolder(requireNotNull(_state.value.folderPath), name) }

    fun createFile(name: String) = runOp { LocalFiles.createFile(requireNotNull(_state.value.folderPath), name) }

    fun rename(path: String, newName: String) = runOp {
        LocalFiles.rename(path, newName).onSuccess { newPath ->
            clearSelection()
            if (newPath != path) {
                MediaScan.changed(getApplication(), listOf(path, newPath))
                settings.update { it.relocated(path, newPath) }
            }
        }
    }

    private fun runOp(block: suspend () -> Result<String>) {
        viewModelScope.launch {
            block().onFailure { emit(it.message ?: "הפעולה נכשלה") }
            load()
        }
    }

    fun emit(text: String) = emitEvent(BrowserEvent.Message(text))

    private companion object {
        const val KEY_LOCATION = "location"
        const val KEY_CLIP_PATHS = "clipboard_paths"
        const val KEY_CLIP_CUT = "clipboard_cut"
        const val MEDIA_PREFIX = "media:"
        const val MAX_SAVED_CLIP = 500
    }

    private fun emitEvent(event: BrowserEvent) {
        _events.trySend(event)
    }
}
