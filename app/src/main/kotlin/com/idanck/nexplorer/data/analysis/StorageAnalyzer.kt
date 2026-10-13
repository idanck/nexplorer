package com.idanck.nexplorer.data.analysis

import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.StorageReport
import com.idanck.nexplorer.core.UsageCategory
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue

/**
 * קובץ (או תיקיית מטמון שלמה) ברשימות של המנתח. השם נגזר מהנתיב — במאות אלפי קבצים כל מחרוזת נחסכת.
 * [label] — כש"השם" הוא נתיב יחסי ("DCIM/Camera").
 */
data class AnalyzedItem(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val label: String? = null,
) {
    val name: String get() = label ?: path.substringAfterLast('/')
}

data class StorageAnalysis(
    val volume: StorageVolumeInfo,
    val scannedAt: Long,
    val files: Int,
    /** בתים לכל קטגוריה; "אפליקציות ומערכת" כולל את מה שתפוס ולא נמצא בסריקה. */
    val byCategory: Map<UsageCategory, Long>,
    val largest: List<AnalyzedItem>,
    /** כל קבוצה — קבצים זהים בתוכן (SHA-256), הישן ביותר ראשון. */
    val duplicates: List<List<AnalyzedItem>>,
    val junk: List<AnalyzedItem>,
    val bigFolders: List<AnalyzedItem>,
) {
    val duplicatesReclaimable: Long get() = StorageReport.reclaimable(duplicates) { it.size }
    val largestTotal: Long get() = largest.sumOf { it.size }
    val junkTotal: Long get() = junk.sumOf { it.size }
}

sealed interface AnalysisProgress {
    data class Scanning(val files: Int) : AnalysisProgress
    /** בבתים: שני סרטונים זהים של 10 GB הם רוב העבודה, לא "2 קבצים". */
    data class Hashing(val doneBytes: Long, val totalBytes: Long) : AnalysisProgress
}

/**
 * סריקה אחת של נפח: פילוח לפי סוג, 20 הקבצים הגדולים, כפילויות, זבל ומטמון, תיקיות גדולות.
 * לא עוקב אחרי קישורים ולא נכנס ל-Android/data ו-Android/obb (סגורים מ-Android 11, וממילא של אפליקציות).
 */
object StorageAnalyzer {

    suspend fun analyze(volume: StorageVolumeInfo, onProgress: (AnalysisProgress) -> Unit): StorageAnalysis = withContext(Dispatchers.IO) {
        val root = File(volume.path)
        val blocked = setOf("${volume.path}/Android/data", "${volume.path}/Android/obb")
        val byCategory = UsageCategory.entries.associateWith { 0L }.toMutableMap()
        val largest = PriorityQueue<AnalyzedItem>(compareBy { it.size })
        val candidates = mutableListOf<AnalyzedItem>()
        val junk = mutableListOf<AnalyzedItem>()
        // גודל לכל תיקייה בעומק 1 ו-2 מהשורש — מהן נבחרות "תיקיות גדולות".
        val folderSizes = HashMap<String, Long>()
        var files = 0
        var scannedBytes = 0L

        fun count(file: File, size: Long) {
            val relative = file.path.removePrefix(volume.path).trimStart('/')
            val parts = relative.split('/')
            if (parts.size >= 2) folderSizes.merge(parts[0], size, Long::plus)
            if (parts.size >= 3) folderSizes.merge(parts[0] + "/" + parts[1], size, Long::plus)
        }

        val stack = ArrayDeque<File>().apply { addLast(root) }
        while (stack.isNotEmpty()) {
            ensureActive()
            val dir = stack.removeLast()
            for (child in dir.listFiles().orEmpty()) {
                if (files % 256 == 0) ensureActive()
                if (LocalFiles.isLink(child)) continue
                if (child.isDirectory) {
                    if (child.path in blocked) continue
                    if (StorageReport.isJunkFolder(child.path.removePrefix(volume.path))) {
                        val size = treeSize(child)
                        junk += AnalyzedItem(child.path, size, child.lastModified(), isDirectory = true)
                        byCategory.merge(UsageCategory.Other, size, Long::plus)
                        scannedBytes += size
                        count(File(child, "_"), size)
                    } else {
                        stack.addLast(child)
                    }
                    continue
                }
                val size = child.length()
                files++
                scannedBytes += size
                val category = StorageReport.categoryOf(FileKinds.of(child.name, false))
                byCategory.merge(category, size, Long::plus)
                count(child, size)
                val item = AnalyzedItem(child.path, size, child.lastModified())
                if (largest.size < StorageReport.LARGE_FILES) largest += item
                else if (size > largest.peek()!!.size) {
                    largest.poll()
                    largest += item
                }
                if (size >= StorageReport.DUPLICATE_MIN_BYTES) candidates += item
                if (StorageReport.isJunkFile(child.name)) junk += item
                if (files % 500 == 0) onProgress(AnalysisProgress.Scanning(files))
            }
        }
        onProgress(AnalysisProgress.Scanning(files))

        // מה שתפוס ולא נמצא בסריקה: אפליקציות (APK ונתונים), מערכת, ותיקיות שאין אליהן גישה.
        val unaccounted = (volume.usedBytes - scannedBytes).coerceAtLeast(0)
        byCategory.merge(UsageCategory.Apps, unaccounted, Long::plus)

        StorageAnalysis(
            volume = volume,
            scannedAt = System.currentTimeMillis(),
            files = files,
            byCategory = byCategory,
            largest = largest.sortedByDescending { it.size },
            duplicates = duplicates(candidates, onProgress),
            junk = junk.sortedByDescending { it.size },
            bigFolders = bigFolders(volume.path, folderSizes),
        )
    }

    /**
     * אותו גודל → אותם 64 KB ראשונים → אותו SHA-256 מלא. רק השלב האחרון קורא קבצים שלמים,
     * ורק לקבוצות ששרדו את שני הראשונים — כך שסריקה של אחסון מלא לא קוראת את כולו.
     */
    private suspend fun duplicates(candidates: List<AnalyzedItem>, onProgress: (AnalysisProgress) -> Unit): List<List<AnalyzedItem>> {
        val bySize = StorageReport.sameSizeGroups(candidates, { it.size })
        val total = bySize.sumOf { group -> group.sumOf { it.size } }
        var done = 0L
        var finished = 0L
        var lastReport = 0L
        val onBytes: (Long) -> Unit = { bytes ->
            done += bytes
            if (done - lastReport >= REPORT_BYTES) {
                lastReport = done
                onProgress(AnalysisProgress.Hashing(done, total))
            }
        }
        val result = mutableListOf<List<AnalyzedItem>>()
        for (group in bySize) {
            val byHead = group.groupBy { hash(it.path, HEAD_BYTES) {} }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (sameHead in byHead) {
                val byFull = sameHead.groupBy { hash(it.path, Long.MAX_VALUE, onBytes) }.filterKeys { it != null }.values.filter { it.size > 1 }
                byFull.forEach { result += it.sortedBy { item -> item.lastModified } }
            }
            // קבוצה שנפלה כבר בבדיקת הראש לא נקראה עד הסוף — נספרת כגמורה.
            finished += group.sumOf { it.size }
            done = maxOf(done, finished)
            onProgress(AnalysisProgress.Hashing(done, total))
        }
        onProgress(AnalysisProgress.Hashing(total, total))
        return result.sortedByDescending { group -> group.first().size * (group.size - 1) }
    }

    private fun bigFolders(rootPath: String, sizes: Map<String, Long>): List<AnalyzedItem> {
        val ranked = sizes.entries.sortedByDescending { it.value }
        val chosen = mutableListOf<Map.Entry<String, Long>>()
        for (entry in ranked) {
            if (chosen.size == StorageReport.BIG_FOLDERS) break
            // "DCIM" ו-"DCIM/Camera" כמעט באותו גודל — מציגים רק את המדויקת יותר.
            val parent = chosen.firstOrNull { entry.key.startsWith(it.key + "/") }
            if (parent != null && entry.value >= parent.value * 0.8) {
                chosen.remove(parent)
            } else if (chosen.any { it.key.startsWith(entry.key + "/") && it.value >= entry.value * 0.8 }) {
                continue
            }
            chosen += entry
        }
        return chosen.sortedByDescending { it.value }
            .map { (relative, size) -> AnalyzedItem("$rootPath/$relative", size, 0, isDirectory = true, label = relative) }
    }

    private suspend fun hash(path: String, limit: Long, onBytes: (Long) -> Unit): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            var left = limit
            while (left > 0) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) break
                digest.update(buffer, 0, read)
                left -= read
                onBytes(read.toLong())
            }
        }
        digest.digest().joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun treeSize(dir: File): Long {
        var total = 0L
        val stack = ArrayDeque<File>().apply { addLast(dir) }
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            for (child in stack.removeLast().listFiles().orEmpty()) {
                when {
                    LocalFiles.isLink(child) -> Unit
                    child.isDirectory -> stack.addLast(child)
                    else -> total += child.length()
                }
            }
        }
        return total
    }

    private const val HEAD_BYTES = 64L * 1024
    private const val REPORT_BYTES = 32L * 1024 * 1024
}
