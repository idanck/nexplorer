package com.idanck.nexplorer.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.ArchiveFormat
import com.idanck.nexplorer.core.ArchiveFormats
import com.idanck.nexplorer.core.ArchivePaths
import com.idanck.nexplorer.core.BatchRename
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.core.FolderSizes
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.RenamePattern
import com.idanck.nexplorer.core.Section
import com.idanck.nexplorer.core.ViewOptions
import com.idanck.nexplorer.core.sectionsFor
import com.idanck.nexplorer.core.sortedFor
import com.idanck.nexplorer.data.archive.Archives
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.crypto.EncryptedPreview
import com.idanck.nexplorer.data.crypto.KeyRing
import com.idanck.nexplorer.data.crypto.Vaults
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.MediaCollection
import com.idanck.nexplorer.data.files.MediaCollections
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.files.StorageVolumes
import com.idanck.nexplorer.data.net.NetConnection
import com.idanck.nexplorer.data.net.RemoteSpace
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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
    /** "הסתר תיקייה" — לא מוצגות. */
    val hiddenFolders: Set<String> = emptySet(),
    /** "גודל תיקיות": מה שחושב עד עכשיו (נתיב → בתים). */
    val folderSizes: Map<String, Long> = emptyMap(),
    /** התיקייה המוצפנת (הפתוחה) שהתיקייה הנוכחית בתוכה. */
    val vaultRoot: String? = null,
    /** המקומות ברשת שבמגירה (D3). */
    val connections: List<NetConnection> = emptyList(),
    /** מקום פנוי בשיתוף האחרון שנפתח בכל חיבור (מזהה → מקום), כשהשרת מדווח. */
    val remoteSpaces: Map<String, RemoteSpace> = emptyMap(),
) {
    val folderPath: String? get() = (location as? Location.Folder)?.path

    /** התיקייה הנוכחית במקום ברשת. */
    val isRemote: Boolean get() = folderPath?.let(NetPaths::isRemote) == true

    /** החיבור של התיקייה הנוכחית; null — במכשיר (או חיבור שנמחק). */
    val connection: NetConnection?
        get() = folderPath?.let(NetPaths::connectionId)?.let { id -> connections.firstOrNull { it.id == id } }

    /** רשימת השיתופים של שרת: אין כאן תיקייה ליצור או להדביק בה. */
    val atShareList: Boolean get() = folderPath?.let(NetPaths::isShareList) == true

    val remoteSpace: RemoteSpace? get() = folderPath?.let(NetPaths::connectionId)?.let(remoteSpaces::get)

    val volume: StorageVolumeInfo? get() = folderPath?.takeIf { !NetPaths.isRemote(it) }?.let { StorageVolumes.volumeOf(it, volumes) }
    val inSelection: Boolean get() = selection.isNotEmpty()
    val isFavorite: Boolean get() = folderPath != null && folderPath in favorites
    val isAtRoot: Boolean
        get() = when (val loc = location) {
            is Location.Media -> false
            is Location.Folder -> if (isRemote) {
                loc.path == connection?.root || NetPaths.segments(loc.path).isEmpty()
            } else {
                volume?.path == loc.path || loc.path == "/"
            }
        }

    val title: String
        get() = when (val loc = location) {
            is Location.Media -> loc.collection.label
            is Location.Folder -> when {
                isRemote && isAtRoot -> connection?.label ?: NetPaths.name(loc.path)
                isRemote -> NetPaths.name(loc.path)
                isAtRoot -> volume?.label ?: "/"
                else -> File(loc.path).name
            }
        }

    val titleIsLtr: Boolean get() = location is Location.Folder && !isAtRoot

    val crumbs: List<Crumb>
        get() = when (val loc = location) {
            is Location.Media -> listOf(Crumb(loc.collection.label, "", isLtr = false))
            is Location.Folder -> if (isRemote) remoteCrumbs(loc.path) else {
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

    /** החיבור (בשמו מהמגירה), ואחריו השיתוף והתיקיות. */
    private fun remoteCrumbs(path: String): List<Crumb> {
        val c = connection
        val root = c?.root?.takeIf { FileNames.isInside(path, it) } ?: NetPaths.connectionId(path)?.let { id ->
            NetPaths.protocolOf(path)?.let { NetPaths.root(it, id) }
        } ?: return emptyList()
        return buildList {
            add(Crumb(c?.label ?: NetPaths.name(root), root, isLtr = false))
            var acc = root
            path.removePrefix(root).trim('/').split('/').filter { it.isNotEmpty() }.forEach { segment ->
                acc = NetPaths.child(acc, segment)
                add(Crumb(segment, acc, isLtr = true))
            }
        }
    }
}

sealed interface BrowserEvent {
    data class Message(val text: String) : BrowserEvent
    data class OpenFile(val entry: Entry) : BrowserEvent
    /** ארכיון שנפתח בתוך האפליקציה (ZIP, 7z, TAR…): חלון "חלץ". */
    data class OpenArchive(val entry: Entry) : BrowserEvent

    /** ‏APK: גיליון הפרטים וההתקנה. */
    data class OpenApk(val entry: Entry) : BrowserEvent

    /** קובץ ‎.enc‎: חלון הסיסמה ("פתח" / "פענח"). [inVault] — בתוך תיקייה מוצפנת פתוחה: נפתח בלי סיסמה. */
    data class OpenEncrypted(val entry: Entry, val inVault: Boolean) : BrowserEvent

    /** כניסה לתיקייה מוצפנת נעולה: הסיסמה, ואז ל-[then] (התיקייה עצמה או משהו בתוכה). */
    data class UnlockVault(val vault: Entry, val then: String) : BrowserEvent

    /** קבצים ברשת: הורדה לעותק זמני (חלון עם התקדמות), ואז [purpose] על העותק. */
    data class Fetch(val entries: List<Entry>, val purpose: FetchPurpose) : BrowserEvent
}

/** מה עושים עם קובץ מהרשת אחרי שהורד לעותק זמני. */
enum class FetchPurpose { Open, OpenWith, Share }

/** תוצאה של שינוי שם מרובה. */
data class BatchRenameResult(val renamed: Int, val failed: List<String>, val moved: List<Pair<String, String>>)

/**
 * המצב של חלון הקבצים: מיקום, רשימה, בחירה, לוח גזירים ומסנן.
 * חי ברמת ה-Activity, כך שחזרה מההגדרות או מהחיפוש מוצאת את החלון כפי שהיה.
 */
class BrowserViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val container = (app as NexplorerApp).container
    private val settings = container.settings
    private val transfers = container.transfers
    private val vaults = container.vaults
    private val remote = container.remote

    // הנפחים נטענים מייד (StatFs זול): בלעדיהם הפריימים הראשונים מציגים "/ › storage › emulated › 0",
    // ו"חזרה" מיידית יוצאת מהשורש. המיקום ולוח הגזירים שורדים הריגת תהליך ברקע.
    private val _state = MutableStateFlow(
        BrowserState(location = initialLocation(), volumes = StorageVolumes.list(app), clipboard = savedClipboard()),
    )
    val state: StateFlow<BrowserState> = _state

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var sizesJob: Job? = null
    private var selectionAnchor: String? = null
    private var resumedOnce = false

    /**
     * החלון השני בשני חלונות נפתח בפעם הראשונה בתיקייה של הראשון. כאן ולא ב-Compose: סיבוב לאורך
     * ובחזרה לרוחב לא מאפס אותו.
     */
    var pairedOnce = false

    /** מיקום הגלילה של כל תיקייה שביקרנו בה — "למעלה" חוזר לאותה שורה. */
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    init {
        _state.update { it.copy(vaultRoot = it.folderPath?.let { path -> Vaults.rootOf(File(path))?.absolutePath }) }
        applySettings(reload = false)
        load()
        viewModelScope.launch {
            // התיקייה המוצפנת ננעלה ("נעל", או שהאפליקציה יצאה לרקע) — יוצאים ממנה.
            vaults.unlocked.collect { open ->
                val root = _state.value.vaultRoot ?: return@collect
                if (root !in open) navigate(Location.Folder(File(root).parent ?: StorageVolumes.primaryPath()))
            }
        }
        viewModelScope.launch {
            // שינוי בהעדפות (מכאן או מההגדרות) — מיון מחדש; "הצג מוסתרים" — רישום מחדש.
            settings.state.drop(1).map { listOf(it.viewFor(_state.value.folderPath), it.favorites, it.hiddenFolders, it.connections) }
                .distinctUntilChanged()
                .collect { applySettings(reload = true) }
        }
        viewModelScope.launch {
            transfers.finished.collect { transfer ->
                refresh()
                refreshVolumes()
                // מועדפים ותצוגה "רק בתיקייה זו" עוברים עם התיקייה.
                if (transfer.moved.isNotEmpty()) settings.update { it.relocated(transfer.moved) }
                if (transfer.status == TransferStatus.Failed) {
                    emit("${transfer.kind.verb} ${transfer.kind.past("נכשל", "נכשלה")}: ${transfer.error}")
                }
                if (transfer.status == TransferStatus.Done) transfer.note?.let(::emit)
            }
        }
    }

    private fun initialLocation(): Location {
        saved.get<String>(KEY_LOCATION)?.let { key ->
            if (key.startsWith(MEDIA_PREFIX)) {
                MediaCollection.entries.firstOrNull { it.name == key.removePrefix(MEDIA_PREFIX) }?.let { return Location.Media(it) }
            } else if (NetPaths.isRemote(key)) {
                // אחרי הריגת התהליך — חוזרים לאותה תיקייה ברשת, אם החיבור עוד במגירה.
                val id = NetPaths.connectionId(key)
                if (settings.state.value.connections.any { it.id == id }) return Location.Folder(key)
            } else if (File(key).isDirectory) {
                return Location.Folder(outsideLockedVault(key))
            }
        }
        val s = settings.state.value
        val last = s.lastFolder?.takeIf { s.reopenLastFolder && File(it).isDirectory }
        return Location.Folder(outsideLockedVault(last ?: StorageVolumes.primaryPath()))
    }

    /** תיקייה בתוך תיקייה מוצפנת נעולה (אחרי הפעלה מחדש) — במקומה, התיקייה שמעל התיקייה המוצפנת. */
    private fun outsideLockedVault(path: String): String {
        if (NetPaths.isRemote(path)) return path
        val root = Vaults.rootOf(File(path)) ?: return path
        return if (vaults.isUnlocked(root)) path else root.parent ?: StorageVolumes.primaryPath()
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
        when {
            entry.isDirectory -> navigate(Location.Folder(entry.path))
            // ברשת: עותק זמני במכשיר, ואז פתיחה רגילה. קובץ מוצפן, ארכיון ו-APK — מהעותק, כקובץ.
            NetPaths.isRemote(entry.path) -> if (EncryptedFiles.hasEncryptedName(entry.name)) {
                emit("קובץ מוצפן ברשת — העתק אותו למכשיר כדי לפתוח או לפענח")
            } else {
                emitEvent(BrowserEvent.Fetch(listOf(entry), FetchPurpose.Open))
            }
            EncryptedFiles.hasEncryptedName(entry.name) -> {
                val vault = Vaults.rootOf(File(entry.path))
                emitEvent(BrowserEvent.OpenEncrypted(entry, inVault = vault != null && vaults.isUnlocked(vault)))
            }
            ArchiveFormats.of(entry.name) != null -> emitEvent(BrowserEvent.OpenArchive(entry))
            entry.extension == "apk" -> emitEvent(BrowserEvent.OpenApk(entry))
            else -> emitEvent(BrowserEvent.OpenFile(entry))
        }
    }

    fun navigate(location: Location) {
        val vault = (location as? Location.Folder)?.takeIf { !NetPaths.isRemote(it.path) }?.let { Vaults.rootOf(File(it.path)) }
        if (vault != null && !vaults.isUnlocked(vault)) {
            emitEvent(BrowserEvent.UnlockVault(LocalFiles.entryOf(vault, countChildren = false), (location as Location.Folder).path))
            return
        }
        // בלי הרשימה הקודמת: אחרת שורות של התיקייה הקודמת מוצגות (ולחיצות) מתחת לכותרת החדשה עד שהרישום מסתיים.
        _state.update {
            it.copy(
                location = location, selection = emptySet(), filter = null, error = null,
                listed = emptyList(), entries = emptyList(), sections = emptyList(), loading = true, folderSizes = emptyMap(),
                vaultRoot = vault?.absolutePath,
            )
        }
        saved[KEY_LOCATION] = keyOf(location)
        selectionAnchor = null
        sizesJob?.cancel()
        applySettings(reload = false)
        load()
        // "פתח בתיקייה האחרונה" — רק במכשיר: הפעלה לא מתחילה בהתחברות לשרת (ואולי מחוץ לבית).
        if (location is Location.Folder && !NetPaths.isRemote(location.path)) {
            viewModelScope.launch { settings.update { it.copy(lastFolder = location.path) } }
        }
    }

    fun openPath(path: String) {
        // מועדף ברשת — תיקייה (מועדפים הם תמיד תיקיות).
        if (NetPaths.isRemote(path)) return navigate(Location.Folder(path))
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
                // בשורש של חיבור ברשת "למעלה" חוזר למכשיר — לא יוצא מהאפליקציה.
                if (s.isRemote && s.isAtRoot) {
                    navigate(Location.Folder(StorageVolumes.primaryPath()))
                    return true
                }
                if (s.isAtRoot) return false
                val parent = NetPaths.parentOf(loc.path) ?: return false
                navigate(Location.Folder(if (s.isRemote) parent else File(parent).absolutePath))
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
                is Location.Folder -> if (NetPaths.isRemote(location.path)) {
                    remote.list(location.path).map { list -> if (showHidden) list else list.filter { !it.isHidden } }
                } else {
                    LocalFiles.list(location.path, showHidden)
                }
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
                    computeFolderSizes()
                    if (location is Location.Folder && NetPaths.isRemote(location.path)) loadSpace(location.path)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(listed = emptyList(), entries = emptyList(), sections = emptyList(), loading = false, error = e.message)
                    }
                }
        }
    }

    /** מקום פנוי בשיתוף — בקשה נפרדת, אחרי שהרשימה כבר מוצגת. */
    private fun loadSpace(path: String) {
        val id = NetPaths.connectionId(path) ?: return
        viewModelScope.launch {
            val space = remote.space(path) ?: return@launch
            _state.update { it.copy(remoteSpaces = it.remoteSpaces + (id to space)) }
        }
    }

    private fun applySettings(reload: Boolean) {
        val s = settings.state.value
        val path = _state.value.folderPath
        val view = s.viewFor(path)
        val hiddenChanged = view.showHidden != _state.value.view.showHidden
        val sizesChanged = view.showFolderSizes != _state.value.view.showFolderSizes
        _state.update {
            it.copy(
                view = view,
                viewIsFolderScoped = path != null && path in s.folderViews,
                favorites = s.favorites,
                hiddenFolders = s.hiddenFolders,
                connections = s.connections,
            )
        }
        if (reload && hiddenChanged) {
            load()
        } else {
            arrange()
            if (reload && sizesChanged) computeFolderSizes()
        }
    }

    /**
     * "גודל תיקיות": כל תיקייה ברשימה, לפי הסדר שבו היא מוצגת, ברקע. התוצאות מתפרסמות בקבוצות
     * (לא בכל תיקייה) — במיון לפי גודל הרשימה מסתדרת מחדש, ולא בכל רגע.
     */
    private fun computeFolderSizes() {
        sizesJob?.cancel()
        val s = _state.value
        val location = s.location
        // ברשת: חישוב גודל של כל תיקייה הוא סריקה של השרת — לא ברקע, בלי שביקשו.
        if (!s.view.showFolderSizes || location !is Location.Folder || NetPaths.isRemote(location.path)) {
            if (s.folderSizes.isNotEmpty()) {
                _state.update { it.copy(folderSizes = emptyMap()) }
                arrange()
            }
            return
        }
        val folders = s.entries.filter { it.isDirectory }.map { it.path }
        sizesJob = viewModelScope.launch {
            // רענון של אותה תיקייה: הגדלים הקודמים נשארים עד שמחושבים מחדש — בלי הבהוב.
            val found = s.folderSizes.filterKeys { it in folders }.toMutableMap()
            var published = System.currentTimeMillis()
            for (path in folders) {
                val size = withContext(Dispatchers.IO) { FolderSizes.of(File(path)) { isActive } } ?: continue
                found[path] = size
                if (System.currentTimeMillis() - published >= SIZES_PUBLISH_MS) {
                    publishSizes(location, found.toMap())
                    published = System.currentTimeMillis()
                }
            }
            publishSizes(location, found.toMap())
        }
    }

    private fun publishSizes(location: Location, sizes: Map<String, Long>) {
        if (_state.value.location != location) return
        _state.update { it.copy(folderSizes = sizes) }
        arrange()
    }

    /** סינון, מיון וקיבוץ — מחוץ ל-main thread כי תיקייה יכולה להכיל אלפי פריטים. */
    private fun arranged(s: BrowserState): Pair<List<Entry>, List<Section>> {
        val shown = s.listed
            .let { list -> if (s.hiddenFolders.isEmpty()) list else list.filter { it.path !in s.hiddenFolders } }
            // גודל שחושב נכנס לרשומה: גם למיון לפי גודל וגם לסכום הבחירה.
            .let { list ->
                if (!s.view.showFolderSizes || s.folderSizes.isEmpty()) list
                else list.map { e -> if (e.isDirectory) s.folderSizes[e.path]?.let { e.copy(size = it) } ?: e else e }
            }
        val filtered = s.filter?.takeIf { it.isNotBlank() }?.let { f ->
            shown.filter { it.name.contains(f, ignoreCase = true) }
        } ?: shown
        val sorted = filtered.sortedFor(s.view.sort)
        // רשימת השיתופים: אין לשיתוף תאריך — מדור "ינואר 1970" מעל כולם.
        return sorted to if (s.view.showSections && !s.atShareList) sectionsFor(sorted, s.view.sort, System.currentTimeMillis(), ZoneId.systemDefault()) else emptyList()
    }

    private fun arrange() {
        viewModelScope.launch {
            val s = _state.value
            val (entries, sections) = withContext(Dispatchers.Default) { arranged(s) }
            _state.update { current ->
                // תוצאה של רשימה ישנה (ניווט באמצע) לא דורסת את החדשה.
                val stale = current.listed !== s.listed || current.filter != s.filter || current.view != s.view ||
                    current.folderSizes !== s.folderSizes || current.hiddenFolders != s.hiddenFolders
                if (stale) current
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

    /** "הסתר תיקייה" (אפשרויות תיקייה): התיקייה הנוכחית יורדת מהרשימה, ועוברים לתיקייה שמעליה. */
    fun hideCurrentFolder() {
        val s = _state.value
        val path = s.folderPath ?: return
        if (s.isAtRoot) return
        viewModelScope.launch {
            settings.update { it.copy(hiddenFolders = it.hiddenFolders + path) }
            // המעבר לתיקייה שמעל קורא את ההעדפות: מחכים שההסתרה כבר בהן — בלי הבהוב של התיקייה.
            settings.state.first { path in it.hiddenFolders }
            goUp()
            emit("התיקייה הוסתרה. להצגה: הגדרות ← תיקיות מוסתרות")
        }
    }

    fun setFavorites(paths: List<String>) {
        viewModelScope.launch { settings.update { it.copy(favorites = paths) } }
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

    /** שני חלונות: העתקה או העברה ישר לתיקייה של החלון השני, או לתיקייה שעליה שוחררה גרירה. */
    fun sendTo(paths: List<String>, targetDir: String, move: Boolean) {
        if (paths.isEmpty()) return
        // לאותה תיקייה: העברה היא כלום, והעתקה רק משכפלת בשם אחר — לא מה שהתכוונו בגרירה בין חלונות.
        if (paths.all { NetPaths.parentOf(it) == targetDir }) {
            emit("הפריטים כבר בתיקייה הזו")
            return
        }
        transfers.enqueue(if (move) TransferKind.Move else TransferKind.Copy, paths, targetDir)
        clearSelection()
    }

    // ---------- ארכיונים ושינוי שם מרובה ----------

    fun compress(paths: List<String>, archiveName: String, format: ArchiveFormat, password: CharArray?) {
        val folder = _state.value.folderPath?.takeIf { !NetPaths.isRemote(it) } ?: return
        val name = ArchiveFormats.withExtension(archiveName, format)
        transfers.enqueue(TransferKind.Compress, paths, folder, archiveName = name, password = password)
        clearSelection()
    }

    /** [intoFolder] — לתיקייה חדשה בשם הארכיון; אחרת לתיקייה שבה הוא נמצא. */
    fun extract(archive: Entry, intoFolder: Boolean, password: CharArray?) {
        val parent = File(archive.path).parent ?: return
        val folderName = if (intoFolder) ArchivePaths.extractFolderName(archive.name) else null
        transfers.enqueue(TransferKind.Extract, listOf(archive.path), parent, archiveName = folderName, password = password)
        clearSelection()
    }

    // ---------- הצפנה ----------

    fun encrypt(paths: List<String>, password: CharArray) {
        transfers.enqueue(TransferKind.Encrypt, paths, null, password = password)
        clearSelection()
    }

    fun decrypt(paths: List<String>, password: CharArray) {
        transfers.enqueue(TransferKind.Decrypt, paths, null, password = password)
        clearSelection()
    }

    /**
     * "פתח": מפענח עותק זמני (ראה [EncryptedPreview]) ופותח אותו כמו כל קובץ — במציג, בנגן או באפליקציה
     * אחרת; הקובץ המוצפן לא משתנה. מחזיר הודעת שגיאה, או null כשנפתח. [onProgress] — מ-0 עד 1.
     */
    suspend fun openEncrypted(entry: Entry, password: CharArray?, onProgress: (Float) -> Unit): String? {
        // בלי סיסמה — בתוך תיקייה מוצפנת פתוחה, במפתח שלה.
        val keys = password?.let { KeyRing(it) }
            ?: Vaults.rootOf(File(entry.path))?.let(vaults::copyFor)
            ?: return "התיקייה המוצפנת נעולה"
        val opened = try {
            withContext(Dispatchers.IO) {
                val total = (entry.size - EncryptedFiles.HEADER_SIZE).coerceAtLeast(1)
                var done = 0L
                val file = keys.use {
                    EncryptedPreview.open(getApplication<Application>().cacheDir, File(entry.path), it) { bytes ->
                        done += bytes
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                LocalFiles.entryOf(file, countChildren = false)
            }
        } catch (e: IOException) {
            return e.message ?: "לא ניתן לפתוח את הקובץ"
        }
        clearSelection()
        emitEvent(BrowserEvent.OpenFile(opened))
        return null
    }

    // ---------- תיקייה מוצפנת ----------

    /** "תיקייה מוצפנת" בכפתור "חדש": נוצרת פתוחה. */
    fun createVault(name: String, password: CharArray) {
        val folder = _state.value.folderPath?.takeIf { !NetPaths.isRemote(it) } ?: return
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) { runCatching { Vaults.create(File(folder), name, password) } }
            password.fill('\u0000')
            created.onSuccess { (dir, keys) ->
                vaults.put(dir, keys)
                refresh()
                emit("התיקייה המוצפנת נוצרה — מה שמועתק או מועבר לתוכה מוצפן")
            }.onFailure { emit(it.message ?: "לא ניתן ליצור את התיקייה") }
        }
    }

    /** מחזיר הודעת שגיאה, או null כשנפתחה (ואז נכנסים ל-[then]). */
    suspend fun unlockVault(vault: Entry, then: String, password: CharArray): String? {
        val keys = try {
            withContext(Dispatchers.IO) { Vaults.unlock(File(vault.path), password) }
        } catch (e: IOException) {
            return e.message ?: "לא ניתן לפתוח את התיקייה"
        }
        vaults.put(File(vault.path), keys)
        navigate(Location.Folder(then))
        return null
    }

    /** "נעל": המפתח נמחק מהזיכרון, ויוצאים מהתיקייה (האיסוף של התיקיות הפתוחות ב-init). */
    fun lockVault() {
        val root = _state.value.vaultRoot ?: return
        vaults.lock(File(root))
    }

    suspend fun inspectArchive(path: String): Result<Archives.Info> =
        withContext(Dispatchers.IO) { runCatching { Archives.inspect(File(path)) } }

    /** שמות אחרים בתיקייה — מה שהתבנית לא יכולה לקבל. */
    suspend fun namesBesides(entries: List<Entry>): Set<String> = withContext(Dispatchers.IO) {
        val folder = _state.value.folderPath ?: return@withContext emptySet()
        File(folder).list().orEmpty().toSet() - entries.map { it.name }.toSet()
    }

    /**
     * רוב הפריטים עוברים ישירות לשם החדש. רק מי שהיעד שלו תפוס כרגע ע"י פריט אחר בבחירה (החלפה a↔b,
     * שרשרת) או ששינויו הוא רק ברישיות עובר קודם דרך שם זמני גלוי והפיך — `<שם>.nexplorer-rename` —
     * כך שגם אם התהליך נהרג באמצע, הקובץ נשאר גלוי ואפשר לזהות מה הוא היה.
     */
    fun batchRename(entries: List<Entry>, pattern: RenamePattern) {
        val folder = _state.value.folderPath?.takeIf { !NetPaths.isRemote(it) } ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { renameAll(File(folder), entries, pattern) }
            clearSelection()
            if (result.moved.isNotEmpty()) settings.update { it.relocated(result.moved) }
            emit(
                when {
                    result.failed.isEmpty() -> "שונה השם ל-${result.renamed} פריטים"
                    else -> "שונה השם ל-${result.renamed}; ${result.failed.size} נכשלו: ${result.failed.first()}"
                },
            )
            load()
        }
    }

    private fun renameAll(dir: File, entries: List<Entry>, pattern: RenamePattern): BatchRenameResult {
        val names = entries.map { it.name }
        val others = dir.list().orEmpty().toSet() - names.toSet()
        val plan = BatchRename.plan(names, pattern, others).filter { it.changes }
        val sources = plan.map { it.from.lowercase() }.toSet()
        val (staged, direct) = plan.partition { step ->
            step.to.equals(step.from, ignoreCase = true) || step.to.lowercase() in sources
        }
        val failed = mutableListOf<String>()
        val moved = mutableListOf<Pair<String, String>>()
        var renamed = 0

        // שלב א: מפנים את השמות שאחרים בבחירה צריכים.
        val temps = staged.mapNotNull { step ->
            val temp = File(dir, FileNames.unique("${step.from}.nexplorer-rename") { File(dir, it).exists() })
            if (File(dir, step.from).renameTo(temp)) step to temp else null.also { failed += step.from }
        }
        // שלב ב: ישירים, ואז מהשמות הזמניים.
        for (step in direct) {
            val source = File(dir, step.from)
            val dest = File(dir, step.to)
            if (!dest.exists() && source.renameTo(dest)) {
                renamed++
                moved += source.absolutePath to dest.absolutePath
            } else {
                failed += step.from
            }
        }
        for ((step, temp) in temps) {
            val dest = File(dir, step.to)
            if (!dest.exists() && temp.renameTo(dest)) {
                renamed++
                moved += File(dir, step.from).absolutePath to dest.absolutePath
                continue
            }
            val back = File(dir, step.from).takeIf { !it.exists() }
                ?: File(dir, FileNames.unique(step.from) { File(dir, it).exists() })
            failed += if (temp.renameTo(back)) step.from else "${step.from} (נשאר בשם ${temp.name})"
        }
        return BatchRenameResult(renamed, failed, moved)
    }

    fun createFolder(name: String) = runOp {
        val folder = requireNotNull(_state.value.folderPath)
        if (NetPaths.isRemote(folder)) remote.createFolder(folder, name) else LocalFiles.createFolder(folder, name)
    }

    fun createFile(name: String) = runOp {
        val folder = requireNotNull(_state.value.folderPath)
        if (NetPaths.isRemote(folder)) remote.createFile(folder, name) else LocalFiles.createFile(folder, name)
    }

    fun rename(path: String, newName: String) = runOp {
        val isRemote = NetPaths.isRemote(path)
        (if (isRemote) remote.rename(path, newName) else LocalFiles.rename(path, newName)).onSuccess { newPath ->
            clearSelection()
            if (newPath != path) {
                settings.update { it.relocated(path, newPath) }
            }
        }
    }

    // ---------- רשת ----------

    /**
     * קבצים ברשת לעותקים זמניים במכשיר (ראה [com.idanck.nexplorer.data.net.RemoteFiles.download]). מחזיר את
     * העותקים, או הודעת שגיאה. סגירת החלון מבטלת (והעותק החלקי נמחק). [onProgress] — מ-0 עד 1.
     */
    suspend fun fetch(entries: List<Entry>, onProgress: (Float) -> Unit): Result<List<Entry>> {
        val total = entries.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        return try {
            val local = entries.map { entry ->
                val file = remote.download(getApplication<Application>().cacheDir, entry.path) { bytes ->
                    done += bytes
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
                LocalFiles.entryOf(file, countChildren = false)
            }
            clearSelection()
            Result.success(local)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    /** "הסר" במגירה: החיבור, הסיסמה שלו (ובענן — האסימון), והמועדפים והתצוגות בתוכו. */
    fun removeConnection(id: String) {
        val connection = _state.value.connections.firstOrNull { it.id == id } ?: return
        val root = NetPaths.root(connection.protocol, id)
        fun inside(path: String) = FileNames.isInside(path, root)
        viewModelScope.launch {
            settings.update { s ->
                s.copy(
                    connections = s.connections.filterNot { it.id == id },
                    favorites = s.favorites.filterNot(::inside),
                    hiddenFolders = s.hiddenFolders.filterNot(::inside).toSet(),
                    folderViews = s.folderViews.filterKeys { !inside(it) },
                )
            }
            remote.forget(id)
            container.cloud.tokens.remove(id)
            if (_state.value.folderPath?.let(::inside) == true) navigate(Location.Folder(StorageVolumes.primaryPath()))
            emit("\"${connection.label}\" הוסר מהמגירה")
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

        /** "גודל תיקיות": כל כמה זמן מתפרסמים הגדלים שחושבו (ולא אחרי כל תיקייה). */
        const val SIZES_PUBLISH_MS = 300L
    }

    private fun emitEvent(event: BrowserEvent) {
        _events.trySend(event)
    }
}
