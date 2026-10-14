package com.idanck.nexplorer.data.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * "תיקייה מוצפנת" (לוח "תפריט חדש"): תיקייה רגילה עם קובץ סימון — [MARKER], קובץ ‎.enc‎ קטן עם תוכן
 * ידוע, כך שפתיחה שלו היא בדיקת הסיסמה. מה שמועתק או מועבר לתוכה דרך האפליקציה נכתב מוצפן, במלח
 * של התיקייה — מפתח אחד שנגזר פעם אחת פותח את כל הקבצים, וכל קובץ עדיין נפתח לבד באותה סיסמה.
 * נבדק ב-JVM.
 */
object Vaults {

    const val MARKER = ".nexplorer-vault.enc"
    private val CHECK = "nexplorer vault v1".toByteArray(Charsets.US_ASCII)

    fun isVault(dir: File): Boolean = File(dir, MARKER).isFile

    /** התיקייה המוצפנת ש-[file] בתוכה, או שהוא עצמו; null — לא בתוך תיקייה מוצפנת. */
    fun rootOf(file: File): File? {
        var dir: File? = if (file.isDirectory) file else file.parentFile
        while (dir != null) {
            if (isVault(dir)) return dir
            dir = dir.parentFile
        }
        return null
    }

    /** יוצר את [name] ב-[parent] ומחזיר אותה עם המפתח שלה — פתוחה. */
    suspend fun create(parent: File, name: String, password: CharArray): Pair<File, KeyRing> {
        if (rootOf(parent) != null) throw IOException("אי אפשר ליצור תיקייה מוצפנת בתוך תיקייה מוצפנת")
        val dir = File(parent, name)
        if (dir.exists()) throw IOException("כבר קיים פריט בשם הזה")
        if (!dir.mkdir()) throw IOException("לא ניתן ליצור את התיקייה")
        val keys = KeyRing(password)
        try {
            val part = File(dir, "$MARKER.nexplorer-part")
            FileOutputStream(part).use { output ->
                EncryptedFiles.encrypt(CHECK.inputStream(), output, keys)
                output.flush()
                output.fd.sync()
            }
            if (!part.renameTo(File(dir, MARKER))) throw IOException("לא ניתן ליצור את התיקייה")
            return dir to keys
        } catch (e: Throwable) {
            keys.close()
            dir.deleteRecursively()
            throw e
        }
    }

    /** בודק את הסיסמה מול הסימון. [EncryptedFiles.WrongPassword] בסיסמה שגויה. */
    suspend fun unlock(root: File, password: CharArray): KeyRing {
        val marker = File(root, MARKER)
        val keys = KeyRing(password)
        try {
            val plain = ByteArrayOutputStream()
            marker.inputStream().use { EncryptedFiles.decrypt(it, plain, keys) }
            if (!plain.toByteArray().contentEquals(CHECK)) throw EncryptedFiles.Damaged("הסימון של התיקייה המוצפנת פגום")
            val (salt, iterations) = EncryptedFiles.passwordSaltOf(marker)
            keys.sealWith(salt, iterations)
            return keys
        } catch (e: Throwable) {
            keys.close()
            throw e
        }
    }
}

/**
 * תיקיות מוצפנות שנפתחו: המפתח בזיכרון בלבד, עד "נעל" או עד שהאפליקציה יוצאת לרקע. כל פעולה מקבלת
 * עותק ([copyFor]) ולא את המפתח עצמו.
 */
class VaultKeys {

    private val open = HashMap<String, KeyRing>()
    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())

    /** נתיבי התיקיות הפתוחות — המסכים מתעדכנים כשתיקייה ננעלת. */
    val unlocked: StateFlow<Set<String>> = _unlocked

    @Synchronized
    fun put(root: File, keys: KeyRing) {
        open.put(root.absolutePath, keys)?.close()
        _unlocked.value = open.keys.toSet()
    }

    @Synchronized
    fun isUnlocked(root: File): Boolean = root.absolutePath in open

    /** null — התיקייה נעולה. */
    @Synchronized
    fun copyFor(root: File): KeyRing? = open[root.absolutePath]?.copy()

    @Synchronized
    fun lock(root: File) {
        open.remove(root.absolutePath)?.close()
        _unlocked.value = open.keys.toSet()
    }

    @Synchronized
    fun lockAll() {
        if (open.isEmpty()) return
        open.values.forEach { it.close() }
        open.clear()
        _unlocked.value = emptySet()
    }
}
