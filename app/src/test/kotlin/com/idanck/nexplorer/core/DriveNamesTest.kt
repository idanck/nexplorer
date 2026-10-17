package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveNamesTest {

    private data class Item(val name: String, val mime: String = "text/plain", val created: Long = 0, val id: String = name)

    private fun names(vararg items: Item): List<String> =
        DriveNames.assign(items.toList(), driveName = { it.name }, mimeType = { it.mime }, created = { it.created }, id = { it.id })

    @Test
    fun `duplicates get a suffix by creation order`() {
        // הישן ביותר — בשמו, גם כשהוא לא ראשון ברשימה.
        assertEquals(
            listOf("report (1).pdf", "report.pdf", "notes.txt"),
            names(Item("report.pdf", created = 200, id = "b"), Item("report.pdf", created = 100, id = "a"), Item("notes.txt")),
        )
        // אותו זמן יצירה — לפי ה-ID, כדי שהשם לא יתחלף בין רשימה לרשימה.
        assertEquals(listOf("x (1)", "x"), names(Item("x", id = "z"), Item("x", id = "y")))
    }

    @Test
    fun `a suffix never takes a name that exists`() {
        assertEquals(
            listOf("a.txt", "a (1).txt", "a (2).txt"),
            names(Item("a.txt", created = 1), Item("a (1).txt", created = 5), Item("a.txt", created = 9, id = "late")),
        )
    }

    @Test
    fun `slashes are shown as a division slash`() {
        assertEquals(listOf("a\u2215b.txt"), names(Item("a/b.txt")))
        // השם שמוצג הוא שם תקין בנתיב.
        assertNull(FileNames.validate(names(Item("2026/10/16")).single()))
    }

    @Test
    fun `google docs get the extension of their export`() {
        val doc = "application/vnd.google-apps.document"
        // מסמך "Report" וקובץ "Report.docx" באותה תיקייה — שני שמות.
        assertEquals(listOf("Report.docx", "Report (1).docx"), names(Item("Report", doc, 1, "a"), Item("Report.docx", created = 2, id = "b")))
        assertEquals("xlsx", DriveNames.exportOf("application/vnd.google-apps.spreadsheet")?.extension)
        assertEquals("pptx", DriveNames.exportOf("application/vnd.google-apps.presentation")?.extension)
        assertEquals("image/png", DriveNames.exportOf("application/vnd.google-apps.drawing")?.mimeType)
        assertNull(DriveNames.exportOf("image/png"))
    }

    @Test
    fun `renaming a google doc keeps the extension out of drive`() {
        val doc = "application/vnd.google-apps.document"
        assertEquals("Budget", DriveNames.driveNameFor("Budget.docx", doc))
        assertEquals("Budget", DriveNames.driveNameFor("Budget", doc))
        // רק הסיומת של הייצוא; קובץ רגיל — כמו שהוא.
        assertEquals("Budget.pdf", DriveNames.driveNameFor("Budget.pdf", doc))
        assertEquals("Budget.docx", DriveNames.driveNameFor("Budget.docx", "application/octet-stream"))
        assertEquals(".docx", DriveNames.driveNameFor(".docx", doc))
    }

    @Test
    fun `forms, sites and other google types are hidden`() {
        assertTrue(DriveNames.visible("image/jpeg"))
        assertTrue(DriveNames.visible(DriveNames.FOLDER))
        assertTrue(DriveNames.visible(DriveNames.SHORTCUT))
        assertTrue(DriveNames.visible("application/vnd.google-apps.document"))
        assertFalse(DriveNames.visible("application/vnd.google-apps.form"))
        assertFalse(DriveNames.visible("application/vnd.google-apps.site"))
        assertFalse(DriveNames.visible("application/vnd.google-apps.script"))
    }
}
