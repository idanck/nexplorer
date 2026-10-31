package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvActionPlanTest {

    private fun file(name: String) = Entry("/sdcard/Movies/$name", name, false, 100, -1, 0, false, FileKinds.of(name, false))
    private val folder = Entry("/sdcard/Movies/Kids", "Kids", true, 0, 9, 0, false, FileKind.Folder)

    @Test
    fun `a single file gets the full panel`() {
        assertEquals(
            listOf(TvOp.Open, TvOp.OpenWith, TvOp.Select, TvOp.Copy, TvOp.Cut, TvOp.Rename, TvOp.Properties, TvOp.Delete),
            TvActionPlan.ops(listOf(file("Trip.mp4")), inSelection = false),
        )
    }

    @Test
    fun `a folder has no open with`() {
        assertTrue(TvOp.OpenWith !in TvActionPlan.ops(listOf(folder), inSelection = false))
    }

    @Test
    fun `a selection has no open, select or rename`() {
        assertEquals(
            listOf(TvOp.Copy, TvOp.Cut, TvOp.Properties, TvOp.Delete),
            TvActionPlan.ops(listOf(file("a.mp4"), folder), inSelection = true),
        )
        // פריט אחד שכבר בבחירה: "בחר כמה" לא חוזר.
        assertTrue(TvOp.Select !in TvActionPlan.ops(listOf(file("a.mp4")), inSelection = true))
        assertEquals(emptyList<TvOp>(), TvActionPlan.ops(emptyList(), inSelection = false))
    }

    @Test
    fun `play for media the player handles, open for the rest`() {
        assertEquals("נגן", TvActionPlan.openLabel(file("Concert.mkv")))
        assertEquals("נגן", TvActionPlan.openLabel(file("song.mp3")))
        assertEquals("פתח", TvActionPlan.openLabel(file("photo.jpg")))
        assertEquals("פתח", TvActionPlan.openLabel(folder))
        // WMV לא מפוענח בנגן — עובר לאפליקציה אחרת.
        assertEquals("פתח", TvActionPlan.openLabel(file("old.wmv")))
    }
}
