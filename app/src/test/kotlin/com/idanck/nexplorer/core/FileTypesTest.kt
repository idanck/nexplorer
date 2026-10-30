package com.idanck.nexplorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTypesTest {
    private fun file(name: String) = Entry("/x/$name", name, false, 1, -1, 0, false, FileKinds.of(name, false))
    private fun folder(name: String, vault: Boolean = false) = Entry("/x/$name", name, true, 0, 0, 0, false, FileKind.Folder, isVault = vault)

    @Test
    fun `documents split into pdf, doc, sheet, slides and ebook`() {
        assertEquals(FileType.Pdf, FileTypes.of(file("a.pdf")))
        assertEquals(FileType.Doc, FileTypes.of(file("a.docx")))
        assertEquals(FileType.Doc, FileTypes.of(file("a.rtf")))
        assertEquals(FileType.Sheet, FileTypes.of(file("a.xlsx")))
        assertEquals(FileType.Slides, FileTypes.of(file("a.pptx")))
        assertEquals(FileType.Ebook, FileTypes.of(file("a.epub")))
    }

    @Test
    fun `text splits into code, sheet and plain text`() {
        assertEquals(FileType.Code, FileTypes.of(file("Main.kt")))
        assertEquals(FileType.Code, FileTypes.of(file("config.json")))
        assertEquals(FileType.Sheet, FileTypes.of(file("data.csv")))
        assertEquals(FileType.Text, FileTypes.of(file("notes.txt")))
        assertEquals(FileType.Text, FileTypes.of(file("README.md")))
    }

    @Test
    fun `media, archives and apps keep their kind`() {
        assertEquals(FileType.Image, FileTypes.of(file("a.jpg")))
        assertEquals(FileType.Video, FileTypes.of(file("a.mp4")))
        // "ts" — הקלטה (MPEG-TS), לא TypeScript.
        assertEquals(FileType.Video, FileTypes.of(file("a.ts")))
        assertEquals(FileType.Audio, FileTypes.of(file("a.flac")))
        assertEquals(FileType.Archive, FileTypes.of(file("a.tar.gz")))
        assertEquals(FileType.Apk, FileTypes.of(file("a.apk")))
        assertEquals(FileType.Folder, FileTypes.of(folder("x")))
    }

    @Test
    fun `unknown files are other, encrypted files are encrypted`() {
        assertEquals(FileType.Other, FileTypes.of(file("disk.iso")))
        assertEquals(FileType.Code, FileTypes.of(file("query.sql")))
        assertEquals(FileType.Encrypted, FileTypes.of(file("a.pdf.enc"), encrypted = true))
    }

    @Test
    fun `known folders get a badge by name, any case`() {
        assertEquals(SpecialFolder.Camera, FileTypes.specialFolder(folder("DCIM")))
        assertEquals(SpecialFolder.Download, FileTypes.specialFolder(folder("Download")))
        assertEquals(SpecialFolder.Download, FileTypes.specialFolder(folder("downloads")))
        assertEquals(SpecialFolder.Music, FileTypes.specialFolder(folder("Ringtones")))
        assertEquals(SpecialFolder.Pictures, FileTypes.specialFolder(folder("Screenshots")))
        assertEquals(SpecialFolder.Vault, FileTypes.specialFolder(folder("Download", vault = true)))
        assertNull(FileTypes.specialFolder(folder("Projects")))
        assertNull(FileTypes.specialFolder(file("Music")))
    }

    @Test
    fun `the other badge is a short ascii extension`() {
        assertEquals("ISO", FileTypes.badge(file("disk.iso")))
        assertEquals("BIN", FileTypes.badge(file("fw.bin")))
        assertNull(FileTypes.badge(file("backup.longext")))
        assertNull(FileTypes.badge(file("noext")))
        assertNull(FileTypes.badge(file("a.קובץ")))
    }
}
