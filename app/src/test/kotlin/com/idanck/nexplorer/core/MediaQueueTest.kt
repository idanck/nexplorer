package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQueueTest {

    private fun entry(name: String, dir: Boolean = false) = Entry(
        path = "/music/$name",
        name = name,
        isDirectory = dir,
        size = 1,
        childCount = -1,
        lastModified = 0,
        isHidden = name.startsWith("."),
        kind = FileKinds.of(name, dir),
    )

    @Test
    fun `queue is the playable files of the same kind, in folder order`() {
        val folder = listOf(entry("Live", dir = true), entry("a.mp3"), entry("cover.jpg"), entry("b.flac"), entry("c.wma"), entry("clip.mp4"))
        val queue = MediaQueue.of(folder, entry("b.flac"))
        assertEquals(listOf("a.mp3", "b.flac"), queue.items.map { it.name })
        assertEquals(1, queue.start)
        assertEquals("b.flac", queue.current.name)
    }

    @Test
    fun `a file missing from the listing goes first`() {
        val queue = MediaQueue.of(listOf(entry("a.mp3"), entry("b.mp3")), entry(".hidden.mp3"))
        assertEquals(listOf(".hidden.mp3", "a.mp3", "b.mp3"), queue.items.map { it.name })
        assertEquals(0, queue.start)
    }

    @Test
    fun `what opens inside the app`() {
        assertTrue(MediaFormats.opensInApp(entry("photo.heic")))
        assertTrue(MediaFormats.opensInApp(entry("song.MP3")))
        assertTrue(MediaFormats.opensInApp(entry("movie.mkv")))
        assertFalse(MediaFormats.opensInApp(entry("song.wma")))
        assertFalse(MediaFormats.opensInApp(entry("tune.mid")))
        assertFalse(MediaFormats.opensInApp(entry("movie.wmv")))
        assertFalse(MediaFormats.opensInApp(entry("notes.txt")))
        assertFalse(MediaFormats.opensInApp(entry("Music.mp3", dir = true)))
        assertFalse(MediaFormats.playable(entry("photo.jpg")))
    }

    @Test
    fun `player times`() {
        assertEquals("0:00", MediaTime.format(-5))
        assertEquals("0:07", MediaTime.format(7_900))
        assertEquals("12:34", MediaTime.format(754_000))
        assertEquals("1:02:03", MediaTime.format(3_723_000))
    }
}
