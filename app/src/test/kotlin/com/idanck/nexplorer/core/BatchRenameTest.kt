package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BatchRenameTest {

    @Test
    fun `numbering pads to the widest number and keeps the extension`() {
        val names = List(12) { "IMG_$it.jpg" }
        val plan = BatchRename.plan(names, RenamePattern(find = "IMG_", replace = "", prefix = "Trip {n} - "), emptyList())
        assertEquals("Trip 01 - 0.jpg", plan.first().to)
        assertEquals("Trip 12 - 11.jpg", plan.last().to)
        assertEquals(true, plan.all { it.problem == null })
    }

    @Test
    fun `find is case-insensitive unless asked`() {
        assertEquals("b.txt", BatchRename.apply("A.txt", RenamePattern(find = "a", replace = "b"), "1"))
        assertEquals("A.txt", BatchRename.apply("A.txt", RenamePattern(find = "a", replace = "b", matchCase = true), "1"))
    }

    @Test
    fun `collisions inside the set and with other files are problems`() {
        val plan = BatchRename.plan(listOf("a.txt", "b.txt"), RenamePattern(find = "a", replace = "b"), listOf("B (copy).txt"))
        // a → b מתנגש ב-b שכבר בקבוצה (ונשאר b).
        assertNotNull(plan[0].problem)
        val other = BatchRename.plan(listOf("x.txt"), RenamePattern(prefix = "B (copy)", find = "x", replace = ""), listOf("b (COPY).txt"))
        assertEquals("כבר קיים פריט בשם הזה", other.single().problem)
    }

    @Test
    fun `swapping names inside the set is allowed`() {
        // a→b ו-b→a: הביצוע הוא דו-שלבי, ולכן זו לא התנגשות.
        val plan = BatchRename.plan(listOf("a", "b"), RenamePattern(find = "a", replace = "c"), emptyList())
        assertNull(plan[0].problem)
        assertEquals("c", plan[0].to)
        assertEquals(false, plan[1].changes)
    }

    @Test
    fun `invalid names are rejected`() {
        val plan = BatchRename.plan(listOf("a.txt"), RenamePattern(prefix = "x/"), emptyList())
        assertNotNull(plan.single().problem)
    }

    @Test
    fun `case change keeps the extension as is`() {
        assertEquals("REPORT.pdf", BatchRename.apply("report.pdf", RenamePattern(case = CaseChange.Upper), "1"))
    }
}
