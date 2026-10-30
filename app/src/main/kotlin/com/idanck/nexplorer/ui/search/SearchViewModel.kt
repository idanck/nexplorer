package com.idanck.nexplorer.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.core.SearchQuery
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.files.StorageVolumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class SizeFilter(val label: String, val minBytes: Long) {
    Any("כל גודל", 0),
    Over1Mb("> 1 MB", 1_000_000),
    Over10Mb("> 10 MB", 10_000_000),
    Over100Mb("> 100 MB", 100_000_000),
    Over1Gb("> 1 GB", 1_000_000_000),
}

enum class DateFilter(val label: String, val maxAgeMs: Long) {
    Any("כל זמן", 0),
    Day("24 שעות", 86_400_000),
    Week("7 ימים", 7 * 86_400_000L),
    Month("30 ימים", 30 * 86_400_000L),
    Year("שנה", 365 * 86_400_000L),
}

data class SearchHit(val entry: Entry, val parentLabel: String)

data class SearchGroup(val label: String, val hits: List<SearchHit>)

data class SearchState(
    val text: String = "",
    val allPlaces: Boolean = false,
    val kind: FileKind? = null,
    val size: SizeFilter = SizeFilter.Any,
    val date: DateFilter = DateFilter.Any,
    val root: String = StorageVolumes.primaryPath(),
    val groups: List<SearchGroup> = emptyList(),
    val scanning: Boolean = false,
    val scannedFolders: Int = 0,
    val elapsedMs: Long = 0,
    val truncated: Boolean = false,
    /** החיפוש ב-NAS נכשל (אין חיבור, ההתחברות נכשלה). */
    val error: String? = null,
) {
    /** חיפוש ב-NAS של Synology (7ב): ה-NAS מחפש, ואין "תיקיות שנסרקו". */
    val remote: Boolean get() = NetPaths.protocolOf(root) == NetProtocol.Synology

    /** "!" או "|" לבד הם עוד לא שאילתה — בלי זה הם היו מציגים את 1000 הקבצים הראשונים. */
    val hasCriteria: Boolean get() = !SearchQuery.parse(text).isEmpty || kind != null || size != SizeFilter.Any || date != DateFilter.Any
    val total: Int get() = groups.sumOf { it.hits.size }
}

/**
 * חיפוש עמוק: מעבר רקורסיבי על מערכת הקבצים (F6, בלי אינדקס בשלב 1).
 * התוצאות מתפרסמות בהדרגה; כל שינוי בשאילתה מבטל את הסריקה הקודמת.
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as NexplorerApp).container.settings
    private val remote = (app as NexplorerApp).container.remote
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state
    private var job: Job? = null

    /** כל סריקה מקבלת מספר; סריקה שבוטלה לא כותבת למצב גם אם היא מסיימת תיקייה אחרי הביטול. */
    @Volatile private var generation = 0
    private var interrupted = false

    fun start(root: String?) {
        if (root != null && root != _state.value.root) {
            _state.update { it.copy(root = root) }
            restart()
        } else if (interrupted) {
            restart()
        }
    }

    /** יציאה מהמסך: סריקה של "בכל המקומות" לא ממשיכה דקות ברקע ומתחרה בהעברות. */
    fun stop() {
        if (job?.isActive != true) return
        job?.cancel()
        generation++
        interrupted = true
        _state.update { it.copy(scanning = false) }
    }

    fun setText(text: String) = change { it.copy(text = text) }
    fun setAllPlaces(all: Boolean) = change { it.copy(allPlaces = all) }
    fun setKind(kind: FileKind?) = change { it.copy(kind = kind) }
    fun setSize(size: SizeFilter) = change { it.copy(size = size) }
    fun setDate(date: DateFilter) = change { it.copy(date = date) }

    private fun change(transform: (SearchState) -> SearchState) {
        _state.update(transform)
        restart()
    }

    private fun restart() {
        job?.cancel()
        interrupted = false
        val gen = ++generation
        val s = _state.value
        if (!s.hasCriteria) {
            _state.update { it.copy(groups = emptyList(), scanning = false, scannedFolders = 0, truncated = false, error = null) }
            return
        }
        if (s.remote) {
            job = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                _state.update { it.copy(groups = emptyList(), scanning = true, scannedFolders = 0, truncated = false, error = null) }
                val started = System.currentTimeMillis()
                try {
                    withContext(Dispatchers.IO) { searchNas(gen, s, started) }
                } catch (e: IOException) {
                    if (gen == generation) _state.update { it.copy(error = e.message ?: "החיפוש ב-NAS נכשל") }
                }
                if (gen == generation) _state.update { it.copy(scanning = false, elapsedMs = System.currentTimeMillis() - started) }
            }
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val volumes = withContext(Dispatchers.IO) { StorageVolumes.list(getApplication()) }
            val roots: List<Pair<String, String>> = if (s.allPlaces) {
                volumes.map { it.label to it.path }
            } else {
                val volume = StorageVolumes.volumeOf(s.root, volumes)
                listOf((volume?.label ?: s.root) to s.root)
            }
            _state.update { it.copy(groups = emptyList(), scanning = true, scannedFolders = 0, truncated = false) }
            val started = System.currentTimeMillis()
            withContext(Dispatchers.IO) { scan(gen, s, roots, volumes, started) }
            if (gen == generation) _state.update { it.copy(scanning = false, elapsedMs = System.currentTimeMillis() - started) }
        }
    }

    private suspend fun scan(gen: Int, s: SearchState, roots: List<Pair<String, String>>, volumes: List<StorageVolumeInfo>, started: Long) {
        val context = currentCoroutineContext()
        val query = SearchQuery.parse(s.text)
        val showHidden = settings.state.value.defaultView.showHidden
        val now = System.currentTimeMillis()
        val groups = linkedMapOf<String, MutableList<SearchHit>>()
        var scanned = 0
        var found = 0
        var lastPublish = 0L

        fun publish(force: Boolean, truncated: Boolean = false) {
            val t = System.currentTimeMillis()
            if (!force && t - lastPublish < PUBLISH_MS) return
            lastPublish = t
            val snapshot = groups.map { (label, hits) -> SearchGroup(label, hits.toList()) }
            _state.update {
                if (gen != generation || !context.isActive) it
                else it.copy(groups = snapshot, scannedFolders = scanned, elapsedMs = t - started, truncated = truncated || it.truncated)
            }
        }

        // השם קודם: הוא לא עולה כלום, ו-stat על כל קובץ באחסון הוא עיקר הזמן של חיפוש עמוק.
        fun accepts(file: File, isDir: Boolean): Entry? {
            if (!query.isEmpty && !query.matches(file.name)) return null
            if (s.kind != null && FileKinds.of(file.name, isDir) != s.kind) return null
            if (s.size != SizeFilter.Any && isDir) return null
            val entry = LocalFiles.entryOf(file, countChildren = false)
            if (s.size != SizeFilter.Any && entry.size < s.size.minBytes) return null
            if (s.date != DateFilter.Any && now - entry.lastModified > s.date.maxAgeMs) return null
            return entry
        }

        for ((label, rootPath) in roots) {
            val root = File(rootPath)
            // מהשורש של הנפח, לא של החיפוש: חיפוש מתוך Android/ לא נכנס ל-data/obb.
            val volumeRoot = StorageVolumes.volumeOf(rootPath, volumes)?.path ?: rootPath
            val blocked = setOf("$volumeRoot/Android/data", "$volumeRoot/Android/obb")
            val stack = ArrayDeque<File>().apply { add(root) }
            while (stack.isNotEmpty()) {
                context.ensureActive()
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                scanned++
                for (child in children) {
                    context.ensureActive()
                    val hidden = child.name.startsWith(".")
                    if (hidden && !showHidden) continue
                    val isDir = child.isDirectory
                    // קישור לתיקייה (‎/storage/self‎) — לולאה, או אותו אחסון פעמיים.
                    if (isDir && child.absolutePath !in blocked && !LocalFiles.isLink(child)) stack.add(child)
                    val entry = accepts(child, isDir) ?: continue
                    val volume = StorageVolumes.volumeOf(child.absolutePath, volumes)
                    val parent = child.parent.orEmpty().removePrefix(volume?.path ?: "").trimStart('/')
                    groups.getOrPut(label) { mutableListOf() } += SearchHit(entry, parent.ifEmpty { "/" })
                    found++
                    if (found >= LIMIT) {
                        publish(force = true, truncated = true)
                        return
                    }
                }
                publish(force = false)
            }
        }
        publish(force = true)
    }

    /**
     * Synology (7ב): ה-NAS מחפש ([SynologyFs.search]) — בתיקייה, או בכל השיתופים ("בכל ה-NAS", או מהשורש). מה שה-NAS
     * יודע לסנן — שם (מילה אחת), גודל ותאריך — נשלח אליו; השאר (כמה מילים, "בלי", "או", סוג) — כאן, על התוצאות.
     */
    private suspend fun searchNas(gen: Int, s: SearchState, started: Long) {
        val fs = remote.synology(s.root) ?: throw IOException("החיבור ל-NAS לא נמצא")
        val context = currentCoroutineContext()
        val query = SearchQuery.parse(s.text)
        val showHidden = settings.state.value.defaultView.showHidden
        val now = System.currentTimeMillis()
        val root = NetPaths.root(NetProtocol.Synology, NetPaths.connectionId(s.root) ?: return)
        val folders = if (s.allPlaces || NetPaths.isShareList(s.root)) fs.list(root).map { it.path } else listOf(s.root)
        val groups = linkedMapOf<String, MutableList<SearchHit>>()
        var found = 0
        var lastPublish = 0L

        fun publish(force: Boolean, truncated: Boolean = false) {
            val t = System.currentTimeMillis()
            if (!force && t - lastPublish < PUBLISH_MS) return
            lastPublish = t
            val snapshot = groups.map { (label, hits) -> SearchGroup(label, hits.toList()) }
            _state.update {
                if (gen != generation || !context.isActive) it
                else it.copy(groups = snapshot, elapsedMs = t - started, truncated = truncated || it.truncated)
            }
        }

        fs.search(
            folders = folders,
            pattern = query.serverPattern,
            minSize = s.size.minBytes,
            modifiedAfter = if (s.date != DateFilter.Any) now - s.date.maxAgeMs else 0,
            isCancelled = { gen != generation || !context.isActive },
        ) { entries ->
            for (entry in entries) {
                if (entry.isHidden && !showHidden) continue
                if (!query.isEmpty && !query.matches(entry.name)) continue
                if (s.kind != null && entry.kind != s.kind) continue
                if (s.size != SizeFilter.Any && (entry.isDirectory || entry.size < s.size.minBytes)) continue
                if (s.date != DateFilter.Any && now - entry.lastModified > s.date.maxAgeMs) continue
                val segments = NetPaths.segments(entry.path)
                val share = segments.firstOrNull() ?: continue
                groups.getOrPut(share) { mutableListOf() } += SearchHit(entry, "/" + segments.dropLast(1).joinToString("/"))
                found++
                if (found >= LIMIT) {
                    publish(force = true, truncated = true)
                    return@search false
                }
            }
            publish(force = false)
            true
        }
        publish(force = true)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val PUBLISH_MS = 200L
        const val LIMIT = 1000
    }
}
