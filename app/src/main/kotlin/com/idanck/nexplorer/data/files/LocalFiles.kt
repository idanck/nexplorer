package com.idanck.nexplorer.data.files

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * גישה ישירה למערכת הקבצים (java.io). עובד כשיש "גישה לכל הקבצים" (D2, ערך זמני).
 * כל הפעולות כאן קצרות; העתקה, העברה ומחיקה רצות דרך TransferManager.
 */
object LocalFiles {

    fun entryOf(file: File, countChildren: Boolean = true): Entry {
        val isDir = file.isDirectory
        return Entry(
            path = file.absolutePath,
            name = file.name.ifEmpty { file.absolutePath },
            isDirectory = isDir,
            size = if (isDir) 0 else file.length(),
            childCount = if (isDir && countChildren) file.list()?.size ?: -1 else -1,
            lastModified = file.lastModified(),
            isHidden = file.name.startsWith("."),
            kind = FileKinds.of(file.name, isDir),
        )
    }

    suspend fun list(dir: String, showHidden: Boolean): Result<List<Entry>> = withContext(Dispatchers.IO) {
        val folder = File(dir)
        val children = folder.listFiles()
            ?: return@withContext Result.failure(
                IOException(if (!folder.exists()) "התיקייה לא קיימת" else "אין הרשאה לקרוא את התיקייה"),
            )
        Result.success(
            children.asSequence()
                .filter { showHidden || !it.name.startsWith(".") }
                .map { entryOf(it) }
                .toList(),
        )
    }

    suspend fun createFolder(parent: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        FileNames.validate(name)?.let { return@withContext Result.failure(IOException(it)) }
        val target = File(parent, name)
        when {
            target.exists() -> Result.failure(IOException("כבר קיים פריט בשם הזה"))
            target.mkdir() -> Result.success(target.absolutePath)
            else -> Result.failure(IOException("לא ניתן ליצור את התיקייה"))
        }
    }

    suspend fun createFile(parent: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        FileNames.validate(name)?.let { return@withContext Result.failure(IOException(it)) }
        val target = File(parent, name)
        if (target.exists()) return@withContext Result.failure(IOException("כבר קיים פריט בשם הזה"))
        val created = runCatching { target.createNewFile() }.getOrDefault(false)
        if (created) Result.success(target.absolutePath) else Result.failure(IOException("לא ניתן ליצור את הקובץ"))
    }

    suspend fun rename(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        FileNames.validate(newName)?.let { return@withContext Result.failure(IOException(it)) }
        val source = File(path)
        val target = File(source.parentFile, newName)
        when {
            target.absolutePath == source.absolutePath -> Result.success(path)
            // שינוי רישיות בלבד ("a.txt" → "A.txt") על מערכת קבצים שאינה רגישה לרישיות.
            target.exists() && !target.name.equals(source.name, ignoreCase = true) ->
                Result.failure(IOException("כבר קיים פריט בשם הזה"))
            source.renameTo(target) -> Result.success(target.absolutePath)
            else -> Result.failure(IOException("לא ניתן לשנות את השם"))
        }
    }

    /** גודל ומספר פריטים של עץ — לחלון המאפיינים. */
    data class TreeStats(val bytes: Long, val files: Int, val folders: Int)

    suspend fun treeStats(paths: List<String>): TreeStats = withContext(Dispatchers.IO) {
        var bytes = 0L
        var files = 0
        var folders = 0
        paths.forEach { root ->
            File(root).walkTopDown().forEach { f ->
                if (f.isDirectory) folders++ else {
                    files++
                    bytes += f.length()
                }
            }
        }
        // השורש עצמו לא נספר כ"תיקייה בתוך".
        TreeStats(bytes, files, (folders - paths.count { File(it).isDirectory }).coerceAtLeast(0))
    }
}
