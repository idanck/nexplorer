package com.idanck.nexplorer.data.transfer

import android.content.Context
import android.os.Build
import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.data.archive.Archives
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.data.crypto.KeyRing
import com.idanck.nexplorer.data.crypto.VaultKeys
import com.idanck.nexplorer.data.crypto.Vaults
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.files.MediaScan
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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** [feminine] — להתאמת הפועל: "ההעתקה נכשלה" אבל "החילוץ נכשל". */
enum class TransferKind(val verb: String, val feminine: Boolean) {
    Copy("העתקה", true),
    Move("העברה", true),
    Delete("מחיקה", true),
    Compress("דחיסה", true),
    Extract("חילוץ", false),
    Encrypt("הצפנה", true),
    Decrypt("פענוח", false),
    ;

    fun past(masculine: String, feminine: String): String = if (this.feminine) feminine else masculine
}

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
    /** העברות שבוצעו (מקור → יעד), כדי שמועדפים ותצוגה לפי תיקייה יעברו איתן. */
    val moved: List<Pair<String, String>> = emptyList(),
    /** דחיסה: שם ה-ZIP. חילוץ: שם תיקייה חדשה, או null = לתוך [targetDir]. */
    val archiveName: String? = null,
    /** הצלחה עם הערה ("2 רשומות דולגו") — מוצגת כהודעה. */
    val note: String? = null,
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
class TransferManager(private val context: Context, private val scope: CoroutineScope, private val vaults: VaultKeys) {

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers

    private val _finished = MutableSharedFlow<Transfer>(extraBufferCapacity = 16)
    /** כל פעולה שהסתיימה (בהצלחה או לא) — המסכים מרעננים את התיקיות שנגעו בהן. */
    val finished: SharedFlow<Transfer> = _finished

    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val ids = AtomicLong(0)
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val paused = ConcurrentHashMap.newKeySet<Long>()
    private val secrets = ConcurrentHashMap<Long, CharArray>()

    init {
        scope.launch(Dispatchers.IO) {
            for (id in queue) run(id)
        }
    }

    /** [password] — לארכיון ולהצפנה. נשמר בזיכרון עד סוף הפעולה, לא במצב שהממשק רואה, ונמחק אחריה. */
    fun enqueue(
        kind: TransferKind,
        sources: List<String>,
        targetDir: String?,
        archiveName: String? = null,
        password: CharArray? = null,
    ): Long {
        val id = ids.incrementAndGet()
        if (password != null && password.isNotEmpty()) secrets[id] = password
        _transfers.update { it + Transfer(id, kind, sources, targetDir, archiveName = archiveName) }
        queue.trySend(id)
        TransferService.start(context)
        return id
    }

    fun cancel(id: Long) {
        cancelled += id
        paused -= id
        // פעולה שבוטלה עוד בתור לא תגיע ל-run — הסיסמה שלה נמחקת כאן.
        if (get(id)?.status == TransferStatus.Queued) secrets.remove(id)?.fill('\u0000')
        _transfers.update { list ->
            list.map { if (it.id == id && it.status == TransferStatus.Queued) it.copy(status = TransferStatus.Cancelled) else it }
        }
    }

    fun togglePause(id: Long) {
        // המשך אחרי השהיה צריך שוב את שירות החזית (הוא נעצר כשהכל מושהה).
        if (paused.remove(id)) TransferService.start(context) else paused += id
    }

    fun pauseAll() {
        _transfers.value.filter { it.isActive }.forEach { paused += it.id }
    }

    fun clearFinished() {
        _transfers.update { list -> list.filter { it.isActive } }
    }

    val hasActive: Boolean get() = _transfers.value.any { it.isActive }

    /** שירות החזית נחוץ רק כשמשהו באמת רץ או ממתין לתורו — לא כשהכל מושהה ונשכח. */
    val needsService: Boolean get() = _transfers.value.any { it.isActive && it.id !in paused }

    private fun get(id: Long): Transfer? = _transfers.value.firstOrNull { it.id == id }

    private fun set(id: Long, transform: (Transfer) -> Transfer) {
        _transfers.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun run(id: Long) {
        val job = get(id) ?: return
        if (job.status != TransferStatus.Queued) {
            secrets.remove(id)?.fill('\u0000')
            return
        }
        set(id) { it.copy(status = TransferStatus.Preparing) }
        val outcome = try {
            when (job.kind) {
                TransferKind.Delete -> delete(job)
                TransferKind.Copy, TransferKind.Move -> copyOrMove(job)
                TransferKind.Compress -> compress(job)
                TransferKind.Extract -> extract(job)
                TransferKind.Encrypt -> encrypt(job)
                TransferKind.Decrypt -> decrypt(job)
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
        secrets.remove(id)?.fill('\u0000')
        flushScan()
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
        if (!targetDir.canWrite()) throw IOException("אין הרשאת כתיבה בתיקיית היעד")
        val sources = job.sources.map(::File)
        // נתיב קנוני: דרך קישור (‎/storage/self/primary‎) תיקייה יכולה "להיות בתוך עצמה" בלי שהנתיב יראה את זה.
        val targetCanonical = targetDir.canonicalPath
        sources.firstOrNull { it.isDirectory && FileNames.isInside(targetCanonical, it.canonicalPath) }?.let {
            throw IOException("אי אפשר להעתיק את \"${it.name}\" לתוך עצמה")
        }

        // לתוך תיקייה מוצפנת (פתוחה): כל קובץ שמגיע מבחוץ נכתב מוצפן. עותק של המפתח — נעילה באמצע לא עוצרת.
        val vault = Vaults.rootOf(targetDir)
        val vaultKeys = vault?.let {
            vaults.copyFor(it) ?: throw IOException("התיקייה המוצפנת \"${it.name}\" נעולה — פתח אותה ונסה שוב")
        }
        try {
            copyOrMoveInto(job, targetDir, targetCanonical, sources, vault, vaultKeys)
        } finally {
            vaultKeys?.close()
        }
    }

    private suspend fun copyOrMoveInto(
        job: Transfer,
        targetDir: File,
        targetCanonical: String,
        sources: List<File>,
        vault: File?,
        vaultKeys: KeyRing?,
    ) {
        /** מבחוץ לתוך התיקייה המוצפנת. בתוכה (העברה בין תת-תיקיות) הקבצים כבר מוצפנים. */
        fun sealing(source: File) = vaultKeys != null && Vaults.rootOf(source) != vault
        val pending = mutableListOf<File>()
        var doneItems = 0
        for (source in sources) {
            checkpoint(job.id)
            if (!source.exists() && !LocalFiles.isLink(source)) throw IOException("\"${source.name}\" כבר לא קיים")
            // שינוי שם היה מכניס את הקובץ לתיקייה המוצפנת כמו שהוא — לא מוצפן.
            if (job.kind == TransferKind.Move && !sealing(source)) {
                // העברה בתוך אותה תיקייה היא כלום; בתוך אותו נפח — שינוי שם מיידי בלי להעתיק בתים.
                if (source.parentFile?.canonicalPath == targetCanonical) {
                    doneItems++
                    continue
                }
                val dest = File(targetDir, uniqueIn(targetDir, source.name))
                if (source.renameTo(dest)) {
                    touched(source, dest)
                    recordMove(job.id, source, dest)
                    doneItems++
                    continue
                }
            }
            if (LocalFiles.isLink(source) && source.isDirectory) {
                throw IOException("\"${source.name}\" הוא קישור לתיקייה — פתח את היעד שלו והעתק משם")
            }
            pending += source
        }
        set(job.id) { it.copy(doneItems = doneItems) }
        if (pending.isEmpty()) return

        var total = 0L
        // הצפנה סופרת כל בית פעמיים: בכתיבה ובאימות.
        for (source in pending) total += sizeOf(job.id, source) * (if (sealing(source)) 2 else 1)
        set(job.id) { it.copy(status = TransferStatus.Running, totalBytes = total) }
        val progress = Progress(job.id)
        val move = job.kind == TransferKind.Move
        for (source in pending) {
            val dest = File(targetDir, uniqueIn(targetDir, source.name))
            try {
                if (sealing(source)) {
                    val real = if (LocalFiles.isLink(source)) source.canonicalFile else source
                    sealTree(job.id, real, targetDir, if (real.isDirectory) dest.name else null, requireNotNull(vaultKeys), progress, move && real == source)
                    if (move && real != source) Files.deleteIfExists(source.toPath())
                } else if (LocalFiles.isLink(source)) {
                    // קישור לקובץ שנבחר במפורש: מעתיקים את התוכן; בהעברה נמחק הקישור — לא הקובץ שהוא מצביע עליו.
                    copyTree(job.id, source.canonicalFile, dest, progress, move = false)
                    if (move) Files.deleteIfExists(source.toPath())
                } else {
                    copyTree(job.id, source, dest, progress, move)
                }
            } catch (e: Exception) {
                // העתקה: היעד חדש (uniqueIn) וחלקי — עדיף בלי מאשר עותק שנראה שלם. בהעברה כל קובץ
                // שהועתק כבר נמחק מהמקור, ולכן היעד החלקי הוא המקום היחיד שלו — לא נוגעים.
                if (!move) dest.deleteRecursively()
                throw e
            }
            if (move) recordMove(job.id, source, dest)
            doneItems++
            set(job.id) { it.copy(doneItems = doneItems) }
        }
    }

    /**
     * כמו [copyTree], לתוך תיקייה מוצפנת: כל קובץ נכתב מוצפן ([EncryptedFiles.encryptFile] — נקרא בחזרה לפני
     * שהמקור נמחק בהעברה). קובץ שכבר מוצפן מועתק כמו שהוא. [name] — שם התיקייה ביעד; לקובץ השם נקבע בהצפנה.
     */
    private suspend fun sealTree(id: Long, source: File, destDir: File, name: String?, keys: KeyRing, progress: Progress, move: Boolean) {
        checkpoint(id)
        if (LocalFiles.isLink(source)) return
        if (source.isDirectory) {
            val dest = File(destDir, name ?: source.name)
            if (!dest.exists() && !dest.mkdirs()) throw IOException("לא ניתן ליצור את \"${dest.name}\"")
            val children = source.listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${source.name}\"")
            for (child in children) sealTree(id, child, dest, null, keys, progress, move)
            dest.setLastModified(source.lastModified())
            if (move && !source.delete()) {
                throw IOException("חלק מהפריטים נשארו ב-\"${source.name}\" (נוספו בזמן ההעברה, או שהם קישורים)")
            }
            return
        }
        if (EncryptedFiles.isEncrypted(source)) {
            // כבר מוצפן (אולי בסיסמה אחרת) — לא מצפינים פעמיים. הבתים נספרו כפול בסך הכל.
            val size = source.length()
            copyTree(id, source, File(destDir, uniqueIn(destDir, source.name)), progress, move)
            progress.add(size)
            return
        }
        set(id) { it.copy(currentName = source.name) }
        val sealed = EncryptedFiles.encryptFile(source, destDir, keys, { checkpoint(id) }, progress::add)
        if (move && !source.delete()) throw IOException("ההצפנה הצליחה, אבל לא ניתן למחוק את המקור \"${source.name}\"")
        touched(if (move) source else null, sealed)
    }

    private suspend fun compress(job: Transfer) {
        val targetDir = File(requireNotNull(job.targetDir))
        if (!targetDir.canWrite()) throw IOException("אין הרשאת כתיבה בתיקיית היעד")
        val sources = job.sources.map(::File)
        var total = 0L
        for (source in sources) total += sizeOf(job.id, source)
        val dest = File(targetDir, uniqueIn(targetDir, requireNotNull(job.archiveName)))
        set(job.id) { it.copy(status = TransferStatus.Running, totalBytes = total, currentName = dest.name) }
        val progress = Progress(job.id)
        val created = Archives.compress(sources, dest, secrets[job.id], checkpoint = { checkpoint(job.id) }, onBytes = progress::add)
        touched(null, created)
    }

    private suspend fun extract(job: Transfer) {
        val archive = File(job.sources.single())
        val parent = File(requireNotNull(job.targetDir))
        if (!parent.canWrite()) throw IOException("אין הרשאת כתיבה בתיקיית היעד")
        if (Vaults.rootOf(parent) != null) {
            throw IOException("חילוץ לתוך תיקייה מוצפנת היה כותב בה קבצים לא מוצפנים — חלץ מחוץ לה, והעבר לתוכה")
        }
        set(job.id) { it.copy(status = TransferStatus.Running, currentName = archive.name) }
        // "חלץ לתיקייה": תיקייה חדשה בשם פנוי — היא שלנו, ולכן בכשל אפשר למחוק אותה כולה.
        val folder = job.archiveName?.let { name ->
            File(parent, uniqueIn(parent, name)).also { if (!it.mkdirs()) throw IOException("לא ניתן ליצור את \"${it.name}\"") }
        }
        val progress = Progress(job.id)
        val result = try {
            Archives.extract(
                archive,
                folder ?: parent,
                secrets[job.id],
                checkpoint = { checkpoint(job.id) },
                onTotal = { total -> set(job.id) { it.copy(totalBytes = total) } },
                onBytes = progress::add,
            )
        } catch (e: Exception) {
            folder?.deleteRecursively()
            throw e
        }
        folder?.let { touched(null, it) }
        result.created.forEach { touched(null, it) }
        val notes = listOfNotNull(
            "${result.unsafe} רשומות דולגו — הנתיב שלהן יוצא מתיקיית החילוץ".takeIf { result.unsafe > 0 },
            "${result.special} קישורים או קבצים מיוחדים לא נוצרו".takeIf { result.special > 0 },
        )
        if (notes.isNotEmpty()) set(job.id) { it.copy(note = notes.joinToString(" · ")) }
    }

    /**
     * "הצפן": כל קובץ בבחירה ובתיקיות שבה הופך ל-"<שם>.enc" באותה תיקייה, והמקור נמחק רק אחרי שהגרסה
     * המוצפנת נכתבה ונקראה בחזרה. קובץ שכבר מוצפן לא מוצפן שוב. כשל באמצע משאיר את מה שכבר הוצפן — מוצפן.
     */
    private suspend fun encrypt(job: Transfer) {
        val password = secrets[job.id] ?: throw IOException("חסרה סיסמה")
        KeyRing(password).use { keys ->
            val found = filesIn(job.id, job.sources.map(::File))
            val (already, plain) = found.files.partition { EncryptedFiles.isEncrypted(it) }
            plain.firstOrNull { it.parentFile?.canWrite() != true }?.let {
                throw IOException("אין הרשאת כתיבה ב-\"${it.parentFile?.name}\"")
            }
            // PBKDF2 — שנייה בערך, עוד במצב "מכין".
            keys.prepareSealing()
            // כל בית נספר פעמיים: בכתיבה ובאימות.
            set(job.id) {
                it.copy(status = TransferStatus.Running, totalBytes = plain.sumOf { f -> f.length() } * 2, totalItems = plain.size)
            }
            val progress = Progress(job.id)
            var done = 0
            for (file in plain) {
                checkpoint(job.id)
                set(job.id) { it.copy(currentName = file.name) }
                val dir = file.parentFile ?: continue
                val sealed = EncryptedFiles.encryptFile(file, dir, keys, { checkpoint(job.id) }, progress::add)
                if (!file.delete()) {
                    throw IOException("\"${file.name}\" הוצפן, אבל לא ניתן למחוק את המקור — יש עכשיו גם עותק מוצפן וגם המקור")
                }
                touched(file, sealed)
                done++
                set(job.id) { it.copy(doneItems = done) }
            }
            val notes = listOfNotNull(
                "${already.size} קבצים כבר היו מוצפנים".takeIf { already.isNotEmpty() },
                "${found.links} קישורים לא הוצפנו".takeIf { found.links > 0 },
                "אין כאן קבצים להצפנה".takeIf { found.files.isEmpty() && found.links == 0 },
            )
            if (notes.isNotEmpty()) set(job.id) { it.copy(note = notes.joinToString(" · ")) }
        }
    }

    /**
     * "פענח": כל קובץ מוצפן בבחירה ובתיקיות שבה חוזר לשם המקורי (או לשם פנוי), והקובץ המוצפן נמחק אחרי
     * שהפענוח נכתב. קבצים שהוצפנו בסיסמה אחרת, או שנפגמו, נשארים כמו שהם ונספרים בהערה.
     */
    private suspend fun decrypt(job: Transfer) {
        val password = secrets[job.id] ?: throw IOException("חסרה סיסמה")
        KeyRing(password).use { keys ->
            val encrypted = filesIn(job.id, job.sources.map(::File)).files.filter { EncryptedFiles.isEncrypted(it) }
            if (encrypted.isEmpty()) throw IOException(if (job.sources.size == 1 && File(job.sources.single()).isFile) "הקובץ לא מוצפן" else "אין כאן קבצים מוצפנים")
            encrypted.firstOrNull { it.parentFile?.canWrite() != true }?.let {
                throw IOException("אין הרשאת כתיבה ב-\"${it.parentFile?.name}\"")
            }
            set(job.id) {
                it.copy(
                    status = TransferStatus.Running,
                    totalBytes = encrypted.sumOf { f -> (f.length() - EncryptedFiles.HEADER_SIZE).coerceAtLeast(0) },
                    totalItems = encrypted.size,
                )
            }
            val progress = Progress(job.id)
            var done = 0
            var wrong = 0
            val damaged = mutableListOf<IOException>()
            for (file in encrypted) {
                checkpoint(job.id)
                set(job.id) { it.copy(currentName = file.name) }
                val dir = file.parentFile ?: continue
                try {
                    val restored = EncryptedFiles.decryptFile(file, dir, keys, { checkpoint(job.id) }, progress::add)
                    if (!file.delete()) {
                        throw IOException("\"${restored.name}\" פוענח, אבל לא ניתן למחוק את הקובץ המוצפן")
                    }
                    touched(file, restored)
                    done++
                    set(job.id) { it.copy(doneItems = done) }
                } catch (e: EncryptedFiles.WrongPassword) {
                    wrong++
                } catch (e: EncryptedFiles.Damaged) {
                    damaged += e
                }
            }
            if (done == 0) {
                // אף קובץ לא נפתח: הסיבה היא השגיאה של הפעולה ולא הערה.
                throw damaged.firstOrNull()?.takeIf { wrong == 0 } ?: EncryptedFiles.WrongPassword()
            }
            val notes = listOfNotNull(
                "$wrong קבצים לא פוענחו — הם הוצפנו בסיסמה אחרת".takeIf { wrong > 0 },
                "${damaged.size} קבצים פגומים לא פוענחו".takeIf { damaged.isNotEmpty() },
            )
            if (notes.isNotEmpty()) set(job.id) { it.copy(note = notes.joinToString(" · ")) }
        }
    }

    private class Found(val files: List<File>, val links: Int)

    /** הקבצים שנבחרו ומה שבתוך התיקיות שנבחרו, בלי לעקוב אחרי קישורים ובלי קבצים זמניים שלנו. */
    private suspend fun filesIn(id: Long, sources: List<File>): Found {
        val files = mutableListOf<File>()
        var links = 0
        var seen = 0
        val stack = ArrayDeque<File>()
        for (source in sources) {
            when {
                LocalFiles.isLink(source) -> links++
                !source.exists() -> throw IOException("\"${source.name}\" כבר לא קיים")
                source.isDirectory -> stack.addLast(source)
                else -> files += source
            }
        }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (child in dir.listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${dir.name}\"")) {
                if (++seen % 256 == 0) checkpoint(id)
                when {
                    LocalFiles.isLink(child) -> links++
                    child.isDirectory -> stack.addLast(child)
                    // הסימון של תיקייה מוצפנת: פענוח שלו היה הורס אותה.
                    child.name.endsWith(".nexplorer-part") || child.name == Vaults.MARKER -> Unit
                    else -> files += child
                }
            }
        }
        // אותו קובץ פעמיים (תיקייה ומשהו בתוכה, מאוסף) — בפעם השנייה הוא כבר לא היה קיים.
        return Found(files.distinctBy { it.absolutePath }, links)
    }

    private fun recordMove(id: Long, from: File, to: File) {
        if (to.isDirectory) set(id) { it.copy(moved = it.moved + (from.absolutePath to to.absolutePath)) }
    }

    private fun uniqueIn(dir: File, name: String): String = FileNames.unique(name) { File(dir, it).exists() }

    /** בלי לעקוב אחרי קישורים (כמו copyTree), ועם נקודת ביטול — עץ גדול לוקח זמן. */
    private suspend fun sizeOf(id: Long, root: File): Long {
        if (LocalFiles.isLink(root)) return if (root.isFile) root.length() else 0
        if (!root.isDirectory) return root.length()
        var total = 0L
        var seen = 0
        val stack = ArrayDeque<File>().apply { addLast(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (child in dir.listFiles().orEmpty()) {
                if (++seen % 256 == 0) checkpoint(id)
                when {
                    LocalFiles.isLink(child) -> Unit
                    child.isDirectory -> stack.addLast(child)
                    else -> total += child.length()
                }
            }
        }
        return total
    }

    /**
     * [move]: כל קובץ נמחק מהמקור מייד אחרי שהעותק שלו נסגר ונכתב לדיסק, ותיקייה — רק כשהיא ריקה.
     * כך קובץ שנוסף למקור באמצע (מצלמה, סנכרון) לא נמחק בלי שהועתק, ואין רגע שבו קובץ קיים רק בזיכרון.
     */
    private suspend fun copyTree(id: Long, source: File, dest: File, progress: Progress, move: Boolean) {
        checkpoint(id)
        if (LocalFiles.isLink(source)) {
            // קישור בתוך עץ: לא עוקבים (לולאות, ומחיקה דרכו מוחקת את היעד האמיתי). אחסון חיצוני לא תומך ביצירת קישורים.
            return
        }
        if (source.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) throw IOException("לא ניתן ליצור את \"${dest.name}\"")
            val children = source.listFiles() ?: throw IOException("לא ניתן לקרוא את התיקייה \"${source.name}\"")
            for (child in children) copyTree(id, child, File(dest, child.name), progress, move)
            dest.setLastModified(source.lastModified())
            if (move && !source.delete()) {
                throw IOException("חלק מהפריטים נשארו ב-\"${source.name}\" (נוספו בזמן ההעברה, או שהם קישורים)")
            }
            return
        }
        set(id) { it.copy(currentName = source.name) }
        // נכתב לשם זמני ומקבל את השם האמיתי רק כשהוא שלם — גם אם התהליך נהרג באמצע.
        val part = File(dest.parentFile, ".${dest.name}.nexplorer-part")
        try {
            source.inputStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        checkpoint(id)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        progress.add(read.toLong())
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (!part.renameTo(dest)) throw IOException("לא ניתן לשמור את \"${dest.name}\"")
        } catch (e: Exception) {
            part.delete()
            throw if (e is Cancelled || e is IOException) e else IOException("שגיאה בהעתקת \"${source.name}\"", e)
        }
        dest.setLastModified(source.lastModified())
        if (move && !source.delete()) throw IOException("ההעתקה הצליחה, אבל לא ניתן למחוק את המקור \"${source.name}\"")
        touched(if (move) source else null, dest)
    }

    private suspend fun delete(job: Transfer) {
        // סדר post-order בלי לעקוב אחרי קישורים: קישור לתיקייה נמחק כקישור, לא דרכו.
        val files = mutableListOf<File>()
        fun collect(file: File) {
            if (file.isDirectory && !LocalFiles.isLink(file)) file.listFiles()?.forEach(::collect)
            files += file
        }
        job.sources.map(::File).forEach(::collect)
        set(job.id) { it.copy(status = TransferStatus.Running, totalItems = files.size, doneItems = 0) }
        var done = 0
        var lastPublish = 0L
        for (file in files) {
            checkpoint(job.id)
            try {
                // NOFOLLOW: גם קישור שבור (exists() == false) נמחק, אחרת תיקיית האב לא מתרוקנת.
                Files.deleteIfExists(file.toPath())
            } catch (e: IOException) {
                throw IOException("לא ניתן למחוק את \"${file.name}\"", e)
            }
            touched(file, null)
            done++
            val now = System.currentTimeMillis()
            if (now - lastPublish > PUBLISH_MS) {
                lastPublish = now
                set(job.id) { it.copy(doneItems = done, currentName = file.name) }
            }
        }
        set(job.id) { it.copy(doneItems = done) }
    }

    /** נאסף לאורך הפעולה ונשלח לסורק בסופה — ראה [MediaScan]. */
    private val toScan = mutableListOf<String>()

    private fun touched(removed: File?, added: File?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || toScan.size >= MAX_SCAN) return
        removed?.let { toScan += it.absolutePath }
        added?.let { toScan += it.absolutePath }
    }

    private fun flushScan() {
        if (toScan.isEmpty()) return
        MediaScan.changed(context, toScan.toList())
        toScan.clear()
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
        const val MAX_SCAN = 5_000
    }
}
