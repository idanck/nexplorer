package com.idanck.nexplorer.data.files

import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.FileNames
import com.idanck.nexplorer.data.crypto.Vaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * גישה ישירה למערכת הקבצים (java.io). עובד כשיש "גישה לכל הקבצים" (D2, ערך זמני).
 * כל הפעולות כאן קצרות; העתקה, העברה ומחיקה רצות דרך TransferManager.
 */
object LocalFiles {

    /**
     * קישור סימבולי (‎/storage/self/primary‎, ‎/sdcard‎). פעולות על עץ לא עוקבות אחריו:
     * מחיקה דרכו מרוקנת את היעד האמיתי, והעתקה דרכו יכולה להיכנס ללולאה.
     */
    fun isLink(file: File): Boolean = runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    fun entryOf(file: File, countChildren: Boolean = true): Entry {
        val isDir = file.isDirectory
        val isVault = isDir && Vaults.isVault(file)
        return Entry(
            path = file.absolutePath,
            name = file.name.ifEmpty { file.absolutePath },
            isDirectory = isDir,
            size = if (isDir) 0 else file.length(),
            // קובץ הסימון של תיקייה מוצפנת לא נספר — הוא לא מוצג.
            childCount = if (isDir && countChildren) file.list()?.let { it.size - if (isVault) 1 else 0 } ?: -1 else -1,
            lastModified = file.lastModified(),
            isHidden = file.name.startsWith("."),
            kind = FileKinds.of(file.name, isDir),
            isVault = isVault,
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
                // הסימון של תיקייה מוצפנת לא מוצג גם עם "הצג קבצים מוסתרים": אין מה לעשות איתו.
                .filter { (showHidden || !it.name.startsWith(".")) && it.name != Vaults.MARKER }
                .map {
                    // תיקייה של עשרות אלפי קבצים: ניווט הלאה מבטל את הרישום הישן.
                    ensureActive()
                    entryOf(it)
                }
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

    /** בלי לעקוב אחרי קישורים, ועם בדיקת ביטול — סגירת חלון המאפיינים עוצרת סריקה של כל האחסון. */
    suspend fun treeStats(paths: List<String>): TreeStats = withContext(Dispatchers.IO) {
        var bytes = 0L
        var files = 0
        var folders = 0
        val stack = ArrayDeque<File>()
        for (path in paths) {
            val root = File(path)
            // השורש עצמו לא נספר כ"תיקייה בתוך".
            if (root.isDirectory && !isLink(root)) stack.addLast(root) else {
                files++
                bytes += root.length()
            }
            while (stack.isNotEmpty()) {
                ensureActive()
                for (child in stack.removeLast().listFiles().orEmpty()) {
                    if (child.isDirectory && !isLink(child)) {
                        folders++
                        stack.addLast(child)
                    } else {
                        files++
                        if (!isLink(child)) bytes += child.length()
                    }
                }
            }
        }
        TreeStats(bytes, files, folders)
    }
}
