package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.sign

class SortingTest {
    private fun file(name: String, size: Long = 0, modified: Long = 0) =
        Entry("/x/$name", name, false, size, -1, modified, false, FileKinds.of(name, false))

    private fun dir(name: String, modified: Long = 0) =
        Entry("/x/$name", name, true, 0, 0, modified, false, FileKind.Folder)

    @Test
    fun `natural order puts file2 before file10`() {
        val names = listOf("file10.txt", "File2.txt", "file1.txt").sortedWith(NaturalOrder)
        assertEquals(listOf("file1.txt", "File2.txt", "file10.txt"), names)
    }

    @Test
    fun `natural order keeps its contract with non-ASCII digits`() {
        // המעגל מהסקירה: "٣" < "55" < "א" < "٣" עם isDigit().
        val a = "٣"
        val b = "55"
        val c = "א"
        val ab = NaturalOrder.compare(a, b).sign
        val bc = NaturalOrder.compare(b, c).sign
        val ac = NaturalOrder.compare(a, c).sign
        assertFalse("לא מעגלי", ab < 0 && bc < 0 && ac > 0)

        // TimSort זורק "Comparison method violates its general contract" על השוואה לא טרנזיטיבית.
        val alphabet = "0123456789٠١٢٣٤٥٦٧٨٩۰۱۲abcXYZאבג._ -"
        val random = java.util.Random(7)
        repeat(300) {
            val names = List(200) { String(CharArray(1 + random.nextInt(6)) { alphabet[random.nextInt(alphabet.length)] }) }
            names.sortedWith(NaturalOrder)
        }
    }

    @Test
    fun `name sort keeps folders first in both directions`() {
        val entries = listOf(file("b.txt"), dir("Zeta"), file("a.txt"), dir("alpha"))
        assertEquals(
            listOf("alpha", "Zeta", "a.txt", "b.txt"),
            entries.sortedFor(SortOrder(SortKey.Name, false)).map { it.name },
        )
        assertEquals(
            listOf("Zeta", "alpha", "b.txt", "a.txt"),
            entries.sortedFor(SortOrder(SortKey.Name, true)).map { it.name },
        )
    }

    @Test
    fun `date sort mixes folders and files, newest first`() {
        val entries = listOf(file("old.jpg", modified = 1), dir("Download", modified = 3), file("new.jpg", modified = 5))
        assertEquals(
            listOf("new.jpg", "Download", "old.jpg"),
            entries.sortedFor(SortOrder(SortKey.Date, true)).map { it.name },
        )
    }

    @Test
    fun `size sort descending`() {
        val entries = listOf(file("s", 10), file("l", 1000), file("m", 100))
        assertEquals(listOf("l", "m", "s"), entries.sortedFor(SortOrder(SortKey.Size, true)).map { it.name })
    }

    @Test
    fun `view options survive encoding and tolerate garbage`() {
        val options = ViewOptions(ViewMode.Gallery, SortOrder(SortKey.Size, false), 2, false, true, showFolderSizes = true)
        assertEquals(options, ViewOptions.decode(options.encode()))
        // ערך שנשמר לפני "גודל תיקיות" (שישה שדות) נקרא, והשדה החדש בברירת המחדל.
        assertEquals(options.copy(showFolderSizes = false), ViewOptions.decode("Gallery|Size|0|2|0|1"))
        assertEquals(ViewOptions(), ViewOptions.decode("nonsense"))
        assertEquals(ViewOptions(), ViewOptions.decode(null))
    }
}
