package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatsTest {
    @Test
    fun `sizes read like the boards`() {
        assertEquals("0 B", SizeFormat.short(0))
        assertEquals("512 B", SizeFormat.short(512))
        assertEquals("48 KB", SizeFormat.short(48_000))
        assertEquals("3.2 MB", SizeFormat.short(3_200_000))
        assertEquals("89.4 GB", SizeFormat.short(89_400_000_000))
        assertEquals("222 GB", SizeFormat.short(222_000_000_000))
        assertEquals("1 MB", SizeFormat.short(999_999))
        assertEquals("6.36 MB", SizeFormat.precise(6_360_000))
    }

    @Test
    fun `dates read like the boards`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val now = LocalDateTime.of(2026, 10, 13, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val t = LocalDateTime.of(2026, 10, 12, 10, 12).atZone(zone).toInstant().toEpochMilli()
        assertEquals("12 באוק׳, 10:12", HebrewDates.short(t, now, zone))
        val old = LocalDateTime.of(2025, 5, 27, 8, 22).atZone(zone).toInstant().toEpochMilli()
        assertEquals("27 במאי 2025", HebrewDates.short(old, now, zone))
    }

    @Test
    fun `counts`() {
        assertEquals("ריק", Counts.items(0))
        assertEquals("פריט 1", Counts.items(1))
        assertEquals("1,204 פריטים", Counts.items(1204))
        assertEquals("נבחר פריט 1", Counts.selected(1))
        assertEquals("0:42", Durations.clock(42))
        assertEquals("1:02:03", Durations.clock(3723))
    }

    @Test
    fun `file names`() {
        assertEquals(null, FileNames.validate("ok.txt"))
        assertEquals("השם לא יכול להכיל /", FileNames.validate("a/b"))
        val taken = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", FileNames.unique("report.pdf") { it in taken })
        assertEquals("Photos (1)", FileNames.unique("Photos") { it == "Photos" })
        assertEquals(true, FileNames.isInside("/a/b/c", "/a/b"))
        assertEquals(false, FileNames.isInside("/a/bc", "/a/b"))
    }
}
