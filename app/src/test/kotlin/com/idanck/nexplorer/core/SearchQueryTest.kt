package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `plain word is a case-insensitive contains`() {
        val q = SearchQuery.parse("router")
        assertTrue(q.matches("my-Router.cfg"))
        assertFalse(q.matches("modem.cfg"))
    }

    @Test
    fun `wildcards anchor to the whole name`() {
        assertTrue(SearchQuery.parse("router*").matches("router-backup-388.1.cfg"))
        assertFalse(SearchQuery.parse("router*").matches("old-router.cfg"))
        assertTrue(SearchQuery.parse("*.jpg").matches("IMG_1.JPG"))
        assertTrue(SearchQuery.parse("IMG_?.jpg").matches("IMG_1.jpg"))
        assertFalse(SearchQuery.parse("IMG_?.jpg").matches("IMG_12.jpg"))
    }

    @Test
    fun `not and or`() {
        val q = SearchQuery.parse("router* !old")
        assertTrue(q.matches("router-backup-388.1.cfg"))
        assertFalse(q.matches("router-old.cfg"))
        val or = SearchQuery.parse("*.jpg | *.png")
        assertTrue(or.matches("a.png"))
        assertTrue(or.matches("b.jpg"))
        assertFalse(or.matches("c.gif"))
        assertTrue(SearchQuery.parse("!tmp").matches("notes.md"))
    }

    @Test
    fun `highlights the literal parts`() {
        assertEquals(listOf(0..5), SearchQuery.parse("router* !old").highlights("router-backup.cfg"))
        assertEquals(listOf(3..7), SearchQuery.parse("route").highlights("my-router"))
        assertEquals(emptyList<IntRange>(), SearchQuery.parse("zzz").highlights("my-router"))
        assertTrue(SearchQuery.parse("").isEmpty)
    }
}
