package com.idanck.nexplorer.data.crypto

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * "פתח" בקובץ מוצפן: עותק מפוענח זמני במטמון הפרטי של האפליקציה ([cacheDir] — אפליקציה אחרת רואה
 * אותו רק דרך הרשאה לקובץ הזה, ב-FileProvider). נמחק כשהאפליקציה עולה מחדש ולא מייד אחרי הפתיחה,
 * כי המציג, הנגן או אפליקציה אחרת עוד קוראים ממנו.
 */
object EncryptedPreview {

    private const val DIR = "decrypted"
    private var cleared = false

    /** תיקייה נפרדת לכל פתיחה: שני קבצים באותו שם לא דורסים זה את זה, והשם המקורי נשמר. */
    suspend fun open(cacheDir: File, source: File, password: CharArray, onBytes: (Long) -> Unit): File {
        clearStale(cacheDir)
        val dir = File(File(cacheDir, DIR), UUID.randomUUID().toString())
        if (!dir.mkdirs()) throw IOException("לא ניתן ליצור עותק זמני")
        try {
            return KeyRing(password).use { keys ->
                EncryptedFiles.decryptFile(source, dir, keys, { currentCoroutineContext().ensureActive() }, onBytes)
            }
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    /**
     * מוחק את העותקים מהריצה הקודמת — פעם אחת לתהליך: בעלייה (ברקע), או לפני הפתיחה הראשונה אם היא
     * הקדימה. נעול, כדי שמחיקה שרצה לא תמחק עותק שנוצר באמצע.
     */
    @Synchronized
    fun clearStale(cacheDir: File) {
        if (cleared) return
        File(cacheDir, DIR).deleteRecursively()
        cleared = true
    }
}
