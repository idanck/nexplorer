package com.idanck.nexplorer.data.files

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** "ערוך כטקסט": קריאה וכתיבה של קובץ טקסט קטן, בלי Android (נבדק ב-JVM). */
object TextFiles {

    /** מעבר לזה עורך Compose נהיה איטי מכדי לעבוד בו; פותחים באפליקציה אחרת. */
    const val MAX_BYTES = 1_000_000L

    sealed interface Loaded {
        data class Text(val text: String) : Loaded
        data class TooLarge(val bytes: Long) : Loaded
        /** תווים שאינם UTF-8 תקין, או NUL — כנראה קובץ בינארי. שמירה הייתה משחיתה אותו. */
        data object Binary : Loaded
    }

    fun load(file: File): Loaded {
        val size = file.length()
        if (size > MAX_BYTES) return Loaded.TooLarge(size)
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) return Loaded.Binary
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            // BOM נשמר כמו שהוא: הוא חלק מהקובץ, והשמירה כותבת בדיוק את מה שבעורך.
            Loaded.Text(decoder.decode(ByteBuffer.wrap(bytes)).toString())
        } catch (e: CharacterCodingException) {
            Loaded.Binary
        }
    }

    /**
     * כתיבה לשם זמני ליד הקובץ, sync, ואז החלפה ב-rename — כיבוי באמצע לא משאיר חצי קובץ.
     * כשה-rename מעל קובץ קיים לא נתמך (חלק ממערכות הקבצים של כרטיסים), מוחקים את המקור ורק אז מעבירים.
     */
    fun save(file: File, text: String) {
        val parent = file.parentFile ?: throw IOException("אין תיקייה לקובץ")
        val part = File(parent, ".${file.name}.nexplorer-part")
        try {
            FileOutputStream(part).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
        } catch (e: Exception) {
            // הכתיבה לקובץ הזמני נכשלה (למשל דיסק מלא) — המקור לא נגע.
            part.delete()
            throw e
        }
        if (part.renameTo(file)) return
        // יש מערכות קבצים (כרטיסים) שלא מחליפות קובץ קיים ב-rename. המקור נמחק רק כשהעותק החדש שלם על הדיסק,
        // ואם גם זה נכשל — העותק החדש נשאר ליד, ולא נמחק.
        if (file.delete() && part.renameTo(file)) return
        throw IOException("השמירה לא הושלמה. הגרסה החדשה נשמרה ב-\"${part.name}\"")
    }
}
