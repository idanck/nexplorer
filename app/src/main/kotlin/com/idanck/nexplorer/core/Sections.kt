package com.idanck.nexplorer.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class Section(val title: String, val entries: List<Entry>)

/**
 * "הצג מדורים": קיבוץ לפי מפתח המיון — תאריך לפי זמן, שם לפי אות, גודל לפי טווח, סוג לפי קטגוריה.
 * הרשימה כבר ממוינת; מדור הוא רצף של פריטים עם אותה כותרת, ולכן כיוון המיון נשמר.
 */
fun sectionsFor(sorted: List<Entry>, order: SortOrder, now: Long, zone: ZoneId): List<Section> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val sections = mutableListOf<Section>()
    var currentTitle: String? = null
    var current = mutableListOf<Entry>()
    for (entry in sorted) {
        val title = sectionTitle(entry, order, today, zone)
        if (title != currentTitle) {
            if (currentTitle != null) sections += Section(currentTitle, current)
            currentTitle = title
            current = mutableListOf()
        }
        current += entry
    }
    if (currentTitle != null) sections += Section(currentTitle, current)
    return sections
}

private fun sectionTitle(entry: Entry, order: SortOrder, today: LocalDate, zone: ZoneId): String {
    if (entry.isDirectory && order.foldersFirst()) return FileKind.Folder.label
    return when (order.key) {
        SortKey.Date -> dateTitle(Instant.ofEpochMilli(entry.lastModified).atZone(zone).toLocalDate(), today)
        SortKey.Name -> letterTitle(entry.name)
        SortKey.Size -> sizeTitle(entry.size)
        SortKey.Type -> entry.kind.label
    }
}

internal fun dateTitle(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    // השבוע מתחיל ביום ראשון (כמו בלוח השנה בישראל), לא "6 הימים האחרונים".
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    return when {
        // תאריך עתידי (שעון מצלמה, אזור זמן) — "היום", ולא חודש שמופיע מעל "היום".
        days <= 0L -> "היום"
        days == 1L -> "אתמול"
        !date.isBefore(weekStart) -> "השבוע"
        else -> "${HebrewDates.MONTHS[date.monthValue - 1]} ${date.year}"
    }
}

/** לפי התו הראשון כמו שהוא — אותו מפתח שהמיון משתמש בו, אחרת "B, A, B" כש-"_b" ממוין לפני "a". */
private fun letterTitle(name: String): String {
    val first = name.firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

private fun sizeTitle(size: Long): String = when {
    size >= 1_000_000_000L -> "מעל 1 GB"
    size >= 100_000_000L -> "100 MB – 1 GB"
    size >= 1_000_000L -> "1–100 MB"
    else -> "פחות מ-1 MB"
}
