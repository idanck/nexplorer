package com.idanck.nexplorer.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** תווי בידוד BiDi: מספר-עם-יחידה בתוך משפט עברי נקרא משמאל לימין (design-language §3). */
const val FSI = '⁨'
const val PDI = '⁩'

fun String.isolated(): String = "$FSI$this$PDI"

object SizeFormat {
    private val UNITS = listOf("KB", "MB", "GB", "TB", "PB")

    /**
     * בסיס 1000, כמו בהגדרות של אנדרואיד, כדי שהמספרים יתאימו למה שהמערכת מציגה.
     * עד 100 — ספרה אחת אחרי הנקודה ("3.2 MB"), ומעל — שלם ("222 GB"). ".0" נחתך ("48 KB").
     */
    fun short(bytes: Long): String = format(bytes, precise = false)

    /** לכותרת הבחירה: שתי ספרות מתחת ל-10 ("6.36 MB"). */
    fun precise(bytes: Long): String = format(bytes, precise = true)

    private fun format(bytes: Long, precise: Boolean): String {
        if (bytes < 1000) return "${bytes.coerceAtLeast(0)} B"
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1000 && unit < UNITS.lastIndex) {
            value /= 1000
            unit++
        }
        var text = render(value, precise)
        // 999,999 בתים מתעגלים ל-"1000 KB"; עדיף "1 MB".
        if (text.toDouble() >= 1000 && unit < UNITS.lastIndex) {
            value /= 1000
            unit++
            text = render(value, precise)
        }
        return "$text ${UNITS[unit]}"
    }

    private fun render(value: Double, precise: Boolean): String {
        val decimals = when {
            value >= 100 -> 0
            precise && value < 10 -> 2
            else -> 1
        }
        return String.format(Locale.US, "%.${decimals}f", value)
            .let { if (decimals > 0) it.trimEnd('0').trimEnd('.') else it }
    }
}

object HebrewDates {
    val MONTHS = listOf(
        "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
        "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
    )
    private val SHORT = listOf(
        "ינו׳", "פבר׳", "מרץ", "אפר׳", "מאי", "יוני",
        "יולי", "אוג׳", "ספט׳", "אוק׳", "נוב׳", "דצמ׳",
    )

    /** "12 באוק׳, 10:12" — ובשנה אחרת "12 באוק׳ 2025". */
    fun short(millis: Long, now: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val current = Instant.ofEpochMilli(now).atZone(zone)
        val day = "${time.dayOfMonth} ב${SHORT[time.monthValue - 1]}"
        return if (time.year == current.year) {
            String.format(Locale.US, "%s, %02d:%02d", day, time.hour, time.minute)
        } else {
            "$day ${time.year}"
        }
    }

    /** "12 באוקטובר 2026, 10:12:05" — לחלון המאפיינים. */
    fun full(millis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return String.format(
            Locale.US, "%d ב%s %d, %02d:%02d:%02d",
            t.dayOfMonth, MONTHS[t.monthValue - 1], t.year, t.hour, t.minute, t.second,
        )
    }

    /** "לפני 3 דק׳" — לרשימת ההעברות שהסתיימו. */
    fun ago(millis: Long, now: Long): String {
        val seconds = ((now - millis) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "עכשיו"
            seconds < 3600 -> "לפני ${seconds / 60} דק׳"
            seconds < 86_400 -> "לפני ${seconds / 3600} שע׳"
            else -> "לפני ${seconds / 86_400} ימים"
        }
    }
}

object Counts {
    /** 0 → "ריק", 1 → "פריט 1", אחרת "N פריטים". */
    fun items(n: Int): String = when {
        n <= 0 -> "ריק"
        n == 1 -> "פריט 1"
        else -> "${grouped(n)} פריטים"
    }

    fun folders(n: Int): String = if (n == 1) "תיקייה 1" else "${grouped(n)} תיקיות"

    fun files(n: Int): String = if (n == 1) "קובץ 1" else "${grouped(n)} קבצים"

    /** כותרת הבחירה: "נבחר פריט 1" / "נבחרו 3 פריטים". */
    fun selected(n: Int): String = if (n == 1) "נבחר פריט 1" else "נבחרו ${grouped(n)} פריטים"

    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}

object Durations {
    /** "0:42", "12:05", "1:02:03" — זמן שנותר בהעברה. */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }
}
