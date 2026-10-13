package com.idanck.nexplorer.ui.analyzer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.data.analysis.AnalysisProgress
import com.idanck.nexplorer.data.analysis.AnalyzedItem
import com.idanck.nexplorer.data.analysis.StorageAnalysis
import com.idanck.nexplorer.data.analysis.StorageAnalyzer
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import com.idanck.nexplorer.data.files.StorageVolumes
import com.idanck.nexplorer.data.transfer.TransferKind
import com.idanck.nexplorer.data.transfer.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** רשימה שנפתחה מתוך "מה אפשר לפנות". */
enum class Finding(val title: String) { Duplicates("כפילויות"), Largest("קבצים גדולים"), Junk("זבל ומטמון") }

data class AnalyzerState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val volume: StorageVolumeInfo? = null,
    val analysis: StorageAnalysis? = null,
    val progress: AnalysisProgress? = null,
    val error: String? = null,
    val finding: Finding? = null,
    val selection: Set<String> = emptySet(),
    /** הסבר אחרי מחיקה שחלק ממנה דולג (כפילות בלי עותק בטוח שנשאר). */
    val message: String? = null,
) {
    val scanning: Boolean get() = progress != null
}

/**
 * מנתח האחסון (לוח Analyzer). התוצאה נשמרת כל עוד המסך חי בתהליך — חזרה אליו לא סורקת שוב,
 * ו"נסרק לפני" אומר כמה היא ישנה.
 */
class AnalyzerViewModel(app: Application) : AndroidViewModel(app) {

    private val transfers = (app as NexplorerApp).container.transfers
    private val _state = MutableStateFlow(AnalyzerState())
    val state: StateFlow<AnalyzerState> = _state
    private var job: Job? = null
    private var pendingDelete: Long? = null
    /** סריקה שבוטלה עדיין יכולה לדווח התקדמות מתוך תיקייה גדולה — רק הסריקה הנוכחית כותבת למצב. */
    @Volatile private var generation = 0

    init {
        viewModelScope.launch {
            transfers.finished.collect { transfer ->
                if (transfer.id != pendingDelete) return@collect
                pendingDelete = null
                if (transfer.status == TransferStatus.Done || transfer.status == TransferStatus.Failed) dropMissing()
            }
        }
    }

    private var startedFor: String? = null
    private var started = false

    /**
     * [path] — נפח שמכיל אותו (מהתיקייה הנוכחית); null — הנפח הראשי. נקרא בכל כניסה למסך ובסיבוב:
     * אותה כניסה לא סורקת שוב, וסריקה שרצה או נפח שנבחר ידנית לא נזרקים.
     */
    fun start(path: String?) {
        if (started && startedFor == path) return
        started = true
        startedFor = path
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) { StorageVolumes.list(getApplication()) }
            val volume = path?.let { StorageVolumes.volumeOf(it, volumes) } ?: volumes.firstOrNull()
            _state.update { it.copy(volumes = volumes) }
            if (volume != null && (volume.path != _state.value.volume?.path || _state.value.analysis == null)) scan(volume)
        }
    }

    fun scan(volume: StorageVolumeInfo = requireNotNull(_state.value.volume)) {
        job?.cancel()
        val gen = ++generation
        _state.update {
            it.copy(volume = volume, analysis = null, error = null, finding = null, selection = emptySet(), message = null, progress = AnalysisProgress.Scanning(0))
        }
        job = viewModelScope.launch {
            runCatching {
                StorageAnalyzer.analyze(volume) { p -> if (gen == generation) _state.update { it.copy(progress = p) } }
            }.onSuccess { analysis ->
                if (gen == generation) _state.update { it.copy(analysis = analysis, progress = null) }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (gen == generation) _state.update { it.copy(progress = null, error = e.message ?: "הסריקה נכשלה") }
            }
        }
    }

    /** הממצאים יכולים להיות בני שעה: מה שכבר נמחק או השתנה בדפדפן יוצא לפני שמסמנים משהו. */
    fun open(finding: Finding) {
        viewModelScope.launch {
            dropMissing()
            // בכפילויות העותקים העודפים כבר מסומנים — זה "מה אפשר לפנות". בגדולים ובזבל — כלום.
            val preselected = if (finding == Finding.Duplicates) selectableAll(finding).toSet() else emptySet()
            _state.update { it.copy(finding = finding, selection = preselected, message = null) }
        }
    }

    fun close() = _state.update { it.copy(finding = null, selection = emptySet(), message = null) }

    fun toggle(path: String) = _state.update { s ->
        s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path)
    }

    /**
     * "בחר הכל": בכפילויות — כל העותקים חוץ מהישן בכל קבוצה (זה "מה אפשר לפנות"); אחרת — הכל.
     * בגדולים ובזבל — כלום מסומן כשנפתחים, "בחר הכל" רק בלחיצה.
     */
    fun selectableAll(finding: Finding): List<String> {
        val analysis = _state.value.analysis ?: return emptyList()
        return when (finding) {
            Finding.Duplicates -> analysis.duplicates.flatMap { group -> group.drop(1).map { it.path } }
            Finding.Largest -> analysis.largest.map { it.path }
            Finding.Junk -> analysis.junk.map { it.path }
        }
    }

    fun setAll(finding: Finding, selected: Boolean) = _state.update { s ->
        s.copy(selection = if (selected) selectableAll(finding).toSet() else emptySet())
    }

    /**
     * בכפילויות, ממש לפני המחיקה: לכל קבוצה חייב להישאר עותק לא-מסומן שעדיין קיים, באותו גודל ותאריך.
     * אחרת (בחר הכל ידני, או שהעותק "הנשמר" נמחק/נערך מאז הסריקה) — הקבוצה כולה לא נמחקת.
     */
    fun deleteSelected() {
        val s = _state.value
        val selection = s.selection
        if (selection.isEmpty()) return
        viewModelScope.launch {
            val (paths, skippedGroups) = withContext(Dispatchers.IO) {
                fun AnalyzedItem.unchanged() = File(path).let { it.exists() && it.length() == size && (lastModified == 0L || it.lastModified() == lastModified) }
                if (s.finding != Finding.Duplicates) {
                    selection.filter { File(it).exists() } to 0
                } else {
                    var skipped = 0
                    val safe = s.analysis?.duplicates.orEmpty().flatMap { group ->
                        val chosen = group.filter { it.path in selection }
                        val keeper = group.any { it.path !in selection && it.unchanged() }
                        when {
                            chosen.isEmpty() -> emptyList()
                            !keeper || chosen.any { !it.unchanged() } -> {
                                skipped++
                                emptyList()
                            }
                            else -> chosen.map { it.path }
                        }
                    }
                    safe to skipped
                }
            }
            if (paths.isNotEmpty()) pendingDelete = transfers.enqueue(TransferKind.Delete, paths, null)
            _state.update {
                it.copy(
                    selection = emptySet(),
                    message = if (skippedGroups > 0) "$skippedGroups קבוצות לא נמחקו — לא היה בהן עותק שנשאר ולא השתנה" else null,
                )
            }
        }
    }

    /** אחרי מחיקה: מה שכבר לא קיים יוצא מהרשימות, בלי לסרוק את כל הנפח מחדש. */
    private suspend fun dropMissing() {
        val analysis = _state.value.analysis ?: return
        val updated = withContext(Dispatchers.IO) {
            fun List<AnalyzedItem>.alive() = filter { File(it.path).exists() }
            analysis.copy(
                largest = analysis.largest.alive(),
                duplicates = analysis.duplicates.map { it.alive() }.filter { it.size > 1 },
                junk = analysis.junk.alive(),
            )
        }
        _state.update { it.copy(analysis = updated) }
    }

    fun items(finding: Finding): List<AnalyzedItem> {
        // הרשימה המוצגת; "בחר הכל" עובד דרך [selectableAll].
        val analysis = _state.value.analysis ?: return emptyList()
        return when (finding) {
            Finding.Duplicates -> analysis.duplicates.flatten()
            Finding.Largest -> analysis.largest
            Finding.Junk -> analysis.junk
        }
    }
}
