package com.idanck.nexplorer.data.transfer

import android.content.Context
import com.idanck.nexplorer.core.FileNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class TransferKind(val verb: String) { Copy("העתקה"), Move("העברה"), Delete("מחיקה") }

enum class TransferStatus { Queued, Preparing, Running, Paused, Done, Failed, Cancelled }

data class Transfer(
    val id: Long,
    val kind: TransferKind,
    val sources: List<String>,
    val targetDir: String?,
    val status: TransferStatus = TransferStatus.Queued,
    val totalBytes: Long = 0,
    val doneBytes: Long = 0,
    val totalItems: Int = sources.size,
    val doneItems: Int = 0,
    val currentName: String? = null,
    val bytesPerSecond: Long = 0,
    val finishedAt: Long = 0,
    val error: String? = null,
) {
    val title: String
        get() = if (sources.size == 1) sources.first().substringAfterLast('/') else "${sources.size} פריטים"

    val isActive: Boolean
        get() = status == TransferStatus.Queued || status == TransferStatus.Preparing ||
            status == TransferStatus.Running || status == TransferStatus.Paused

    /** מחיקה נמדדת בפריטים, העתקה בבתים. */
    val fraction: Float
        get() = when {
            status == TransferStatus.Done -> 1f
            kind == TransferKind.Delete -> if (totalItems <= 0) 0f else doneItems.toFloat() / totalItems
            totalBytes <= 0 -> 0f
            else -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        }

    val etaSeconds: Long?
        get() = if (bytesPerSecond <= 0 || totalBytes <= 0) null else (totalBytes - doneBytes) / bytesPerSecond
}

/**
 * תור ההעברות: פעולה אחת רצה, השאר "בתור" (לוח "הדבקה ומגש העברות").
 *
 * רץ ב-scope של האפליקציה ולא של מסך, כדי שהעתקה תמשיך כשעוברים מסך או מכבים אותו;
 * [TransferService] מחזיק את התהליך בחיים כל עוד יש משהו פעיל.
 * אף פעולה לא דורסת קובץ קיים: התנגשות מקבלת שם פנוי ("name (1).ext").
 */
class TransferManager(private val context: Context, private val scope: CoroutineScope) {

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers

    private val _finished = MutableSharedFlow<Transfer>(extraBufferCapacity = 16)
    /** כל פעולה שהסתיימה (בהצלחה או לא) — המסכים מרעננים את התיקיות שנגעו בהן. */
    val finished: SharedFlow<Transfer> = _finished

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val ids = AtomicLong(0)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val paused = ConcurrentHashMap.newKeySet<Long>()

    init {
        scope.launch(Dispatchers.IO) {
            for (id in queue) run(id)
        }
    }

    fun enqueue(kind: TransferKind, sources: List<String>, targetDir: String?): Long {
        val id = ids.incrementAndGet()
        _transfers.update { it + Transfer(id, kind, sources, targetDir) }
        queue.trySend(id)
        TransferService.start(context)
        return id
    }

    fun cancel(id: Long) {
        cancelled += id
        paused -= id
        _transfers.update { list ->
            list.map { if (it.id == id && it.status == TransferStatus.Queued) it.copy(status = TransferStatus.Cancelled) else it }
        }
    }

    fun togglePause(id: Long) {
        if (!paused.remove(id)) paused += id
    }

    fun clearFinished() {
        _transfers.update { list -> list.filter { it.isActive } }
    }

    val hasActive: Boolean get() = _transfers.value.any { it.isActive }

    private fun get(id: Long): Transfer? = _transfers.value.firstOrNull { it.id == id }

    private fun set(id: Long, transform: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun run(id: Long) {
        val job = get(id) ?: return
        if (job.status != TransferStatus.Queued) return
        set(id) { it.copy(status = TransferStatus.Preparing) }
        val outcome = try {
            when (job.kind) {
                TransferKind.Delete -> delete(job)
                TransferKind.Copy, TransferKind.Move -> copyOrMove(job)
            }
            set(id) { it.copy(status = TransferStatus.Done, doneBytes = it.totalBytes, doneItems = it.totalItems) }
            null
        } catch (e: Cancelled) {
            TransferStatus.Cancelled to null
        } catch (e: Exception) {
            TransferStatus.Failed to (e.message ?: "שגיאה לא צפויה")
        }
        if (outcome != null) set(id) { it.copy(status = outcome.first, error = outcome.second) }
        set(id) { it.copy(finishedAt = System.currentTimeMillis(), currentName = null, bytesPerSecond = 0) }
        cancelled -= id
        paused -= id
        get(id)?.let { _finished.tryEmit(it) }
    }

    private class Cancelled : Exception()

    /** נקודת בדיקה בתוך הלולאות: ביטול זורק, השהיה ממתינה. */
    private suspend fun checkpoint(id: Long) {
        if (id in cancelled) throw Cancelled()
        if (id in paused) {
            set(id) { it.copy(status = TransferStatus.Paused, bytesPerSecond = 0) }
            while (id in paused) {
                if (id in cancelled) throw Cancelled()
                delay(200)
            }
            set(id) { it.copy(status = TransferStatus.Running) }
        }
    }

    private suspend fun copyOrMove(job: Transfer) {
        val targetDir = File(requireNotNull(job.targetDir))
        if (!targetDir.isDirectory) throw IOException("תיקיית היעד לא קיימת")
        val sources = job.sources.map(::File)
        sources.firstOrNull { it.isDirectory && FileNames.isInside(targetDir.absolutePath, it.absolutePath) }?.let {
            throw IOException("אי אפשר להעתיק את \"${it.name}\" לתוך עצמה")
        }

        val pending = mutableListOf<File>()
        var doneItems = 0
        for (source in sources) {
            checkpoint(job.id)
            if (!source.exists()) throw IOException("\"${source.name}\" כבר לא קיים")
            if (job.kind == TransferKind.Move) {
                // העברה בתוך אותה תיקייה היא כלום; בתוך אותו נפח — שינוי שם מיידי בלי להעתיק בתים.
                if (source.parentFile?.absolutePath == targetDir.absolutePath) {
                    doneItems++
                    continue
                }
                val dest = File(targetDir, uniqueIn(targetDir, source.name))
                if (source.renameTo(dest)) {
                    doneItems++
                    continue
                }
            }
            pending += source
        }
        set(job.id) { it.copy(doneItems = doneItems) }
        if (pending.isEmpty()) return

        val total = pending.sumOf { sizeOf(it) }
        set(job.id) { it.copy(status = TransferStatus.Running, totalBytes = total) }
        val progress = Progress(job.id)
        for (source in pending) {
            val dest = File(targetDir, uniqueIn(targetDir, source.name))
            copyTree(job.id, source, dest, progress)
            if (job.kind == TransferKind.Move && !source.deleteRecursively()) {
                throw IOException("ההעתקה הצליחה, אבל לא ניתן למחוק את המקור \"${source.name}\"")
            }
            doneItems++
            set(job.id) { it.copy(doneItems = doneItems) }
        }
    }

    private fun uniqueIn(dir: File, name: String): String = FileNames.unique(name) { File(dir, it).exists() }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()

    private suspend fun copyTree(id: Long, source: File, dest: File, progress: Progress) {
        checkpoint(id)
        if (source.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) throw IOException("לא ניתן ליצור את \"${dest.name}\"")
            source.listFiles()?.forEach { child -> copyTree(id, child, File(dest, child.name), progress) }
            dest.setLastModified(source.lastModified())
            return
        }
        set(id) { it.copy(currentName = source.name) }
        try {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        checkpoint(id)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        progress.add(read.toLong())
                    }
                }
            }
        } catch (e: Exception) {
            // קובץ חלקי ביעד גרוע יותר מקובץ חסר: הוא נראה תקין.
            dest.delete()
            throw if (e is Cancelled || e is IOException) e else IOException("שגיאה בהעתקת \"${source.name}\"", e)
        }
        dest.setLastModified(source.lastModified())
    }

    private suspend fun delete(job: Transfer) {
        val files = job.sources.map(::File).flatMap { root ->
            if (root.isDirectory) root.walkBottomUp().toList() else listOf(root)
        }
        set(job.id) { it.copy(status = TransferStatus.Running, totalItems = files.size, doneItems = 0) }
        var done = 0
        var lastPublish = 0L
        for (file in files) {
            checkpoint(job.id)
            if (file.exists() && !file.delete()) throw IOException("לא ניתן למחוק את \"${file.name}\"")
            done++
            val now = System.currentTimeMillis()
            if (now - lastPublish > PUBLISH_MS) {
                lastPublish = now
                set(job.id) { it.copy(doneItems = done, currentName = file.name) }
            }
        }
        set(job.id) { it.copy(doneItems = done) }
    }

    /** מצרף בתים ומפרסם לממשק לכל היותר כל [PUBLISH_MS], עם מהירות על חלון של שנייה. */
    private inner class Progress(private val id: Long) {
        private var done = 0L
        private var lastPublish = 0L
        private var windowStart = System.currentTimeMillis()
        private var windowBytes = 0L
        private var speed = 0L

        fun add(bytes: Long) {
            done += bytes
            val now = System.currentTimeMillis()
            if (now - windowStart >= 1000) {
                speed = (done - windowBytes) * 1000 / (now - windowStart)
                windowStart = now
                windowBytes = done
            }
            if (now - lastPublish >= PUBLISH_MS) {
                lastPublish = now
                val snapshot = done
                val bps = speed
                set(id) { it.copy(status = TransferStatus.Running, doneBytes = snapshot, bytesPerSecond = bps) }
            }
        }
    }

    private companion object {
        const val BUFFER = 256 * 1024
        const val PUBLISH_MS = 150L
    }
}
