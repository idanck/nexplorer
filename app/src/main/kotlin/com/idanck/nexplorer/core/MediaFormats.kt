package com.idanck.nexplorer.core

import java.util.Locale

/**
 * מה נפתח בתוך האפליקציה ומה עובר לאפליקציה אחרת. תמונה — תמיד במציג. שמע ווידאו — רק מה ש-ExoPlayer
 * מפענח בלי ספריות נייטיב (WMA, ‏WMV ו-MIDI לא — הם עוברים ל"פתיחה באמצעות").
 */
object MediaFormats {

    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr")
    private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts", "avi", "flv")

    fun opensInApp(entry: Entry): Boolean = !entry.isDirectory && when (entry.kind) {
        FileKind.Image -> true
        FileKind.Audio -> entry.extension in AUDIO
        FileKind.Video -> entry.extension in VIDEO
        else -> false
    }

    /** שמע או וידאו שהנגן המובנה מנגן (תמונה לא). */
    fun playable(entry: Entry): Boolean = entry.kind != FileKind.Image && opensInApp(entry)
}

/** זמן בנגן: "0:07", "12:34", "1:02:03". שלילי או לא ידוע — "0:00". */
object MediaTime {
    fun format(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
