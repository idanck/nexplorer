package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SectionsTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(2026, 10, 13)

    private fun file(name: String, modified: Long, size: Long = 0) =
        Entry("/x/$name", name, false, size, -1, modified, false, FileKinds.of(name, false))

    @Test
    fun `date sections follow the board - today, this week, then month`() {
        // 13/10/2026 הוא יום שלישי; השבוע התחיל ביום ראשון 11/10.
        val entries = listOf(
            file("future", at(2026, 10, 20)),
            file("a", at(2026, 10, 13)),
            file("b", at(2026, 10, 12)),
            file("c", at(2026, 10, 11)),
            file("c2", at(2026, 10, 9)),
            file("d", at(2026, 9, 28)),
            file("e", at(2025, 9, 1)),
        )
        val titles = sectionsFor(entries, SortOrder(SortKey.Date, true), now, zone).map { it.title }
        assertEquals(listOf("היום", "אתמול", "השבוע", "אוקטובר 2026", "ספטמבר 2026", "ספטמבר 2025"), titles)
    }

    @Test
    fun `letter headers use the same first character the sort uses`() {
        val entries = listOf(file("_b.txt", 0), file("apple", 0), file(".zshrc", 0), file("zoo", 0))
            .sortedFor(SortOrder(SortKey.Name, false))
        val titles = sectionsFor(entries, SortOrder(SortKey.Name, false), now, zone).map { it.title }
        assertEquals(listOf("#", "A", "Z"), titles)
    }

    @Test
    fun `name sections group folders then letters`() {
        val entries = listOf(
            Entry("/x/Docs", "Docs", true, 0, 2, 0, false, FileKind.Folder),
            file("apple.txt", 0),
            file("avocado.txt", 0),
            file("1.txt", 0),
        ).sortedFor(SortOrder(SortKey.Name, false))
        val sections = sectionsFor(entries, SortOrder(SortKey.Name, false), now, zone)
        assertEquals(listOf("תיקיות", "#", "A"), sections.map { it.title })
        assertEquals(2, sections.last().entries.size)
    }
}
