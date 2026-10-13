package com.idanck.nexplorer.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
) {
    val hasCriteria: Boolean get() = text.isNotBlank() || kind != null || size != SizeFilter.Any || date != DateFilter.Any
    val total: Int get() = groups.sumOf { it.hits.size }
}

/**
 * חיפוש עמוק: מעבר רקורסיבי על מערכת הקבצים (F6, בלי אינדקס בשלב 1).
 * התוצאות מתפרסמות בהדרגה; כל שינוי בשאילתה מבטל את הסריקה הקודמת.
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as NexplorerApp).container.settings
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state
    private var job: Job? = null

    fun start(root: String?) {
        if (root != null && root != _state.value.root) {
            _state.update { it.copy(root = root) }
            restart()
        }
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
        val s = _state.value
        if (!s.hasCriteria) {
            _state.update { it.copy(groups = emptyList(), scanning = false, scannedFolders = 0, truncated = false) }
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
            withContext(Dispatchers.IO) { scan(s, roots, volumes, started) }
            _state.update { it.copy(scanning = false, elapsedMs = System.currentTimeMillis() - started) }
        }
    }

    private suspend fun scan(s: SearchState, roots: List<Pair<String, String>>, volumes: List<StorageVolumeInfo>, started: Long) {
        val query = SearchQuery.parse(s.text)
        val showHidden = settings.state.value.defaultView.showHidden
        val now = System.currentTimeMillis()
        val groups = linkedMapOf<String, MutableList<SearchHit>>()
        var scanned = 0
        var found = 0
        var lastPublish = 0L

        fun publish(force: Boolean) {
            val t = System.currentTimeMillis()
            if (!force && t - lastPublish < PUBLISH_MS) return
            lastPublish = t
            val snapshot = groups.map { (label, hits) -> SearchGroup(label, hits.toList()) }
            _state.update { it.copy(groups = snapshot, scannedFolders = scanned, elapsedMs = t - started) }
        }

        fun accepts(file: File, entry: Entry): Boolean {
            if (!query.isEmpty && !query.matches(file.name)) return false
            if (s.kind != null && entry.kind != s.kind) return false
            if (s.size != SizeFilter.Any && (entry.isDirectory || entry.size < s.size.minBytes)) return false
            if (s.date != DateFilter.Any && now - entry.lastModified > s.date.maxAgeMs) return false
            return true
        }

        for ((label, rootPath) in roots) {
            val root = File(rootPath)
            val blocked = setOf("$rootPath/Android/data", "$rootPath/Android/obb")
            val stack = ArrayDeque<File>().apply { add(root) }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                scanned++
                for (child in children) {
                    val hidden = child.name.startsWith(".")
                    if (hidden && !showHidden) continue
                    val isDir = child.isDirectory
                    if (isDir && child.absolutePath !in blocked) stack.add(child)
                    val entry = LocalFiles.entryOf(child, countChildren = false)
                    if (accepts(child, entry)) {
                        val volume = StorageVolumes.volumeOf(child.absolutePath, volumes)
                        val parent = child.parent.orEmpty().removePrefix(volume?.path ?: "").trimStart('/')
                        groups.getOrPut(label) { mutableListOf() } += SearchHit(entry, parent.ifEmpty { "/" })
                        found++
                        if (found >= LIMIT) {
                            _state.update { it.copy(truncated = true) }
                            publish(force = true)
                            return
                        }
                    }
                }
                publish(force = false)
            }
        }
        publish(force = true)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val PUBLISH_MS = 200L
        const val LIMIT = 1000
    }
}
