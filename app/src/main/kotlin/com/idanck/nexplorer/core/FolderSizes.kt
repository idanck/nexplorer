package com.idanck.nexplorer.core

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * הגודל של תיקייה ("גודל תיקיות" בלוח "תפריט ⋮"): סכום הקבצים הרגילים בכל העומק. קישורים
 * סימבוליים לא נפתחים (לולאות, וכפל של אותו תוכן); תיקייה שאין גישה אליה מדולגת.
 */
object FolderSizes {

    /** [keepGoing] נבדק בכל קובץ — false עוצר באמצע (ניווט לתיקייה אחרת), והתוצאה null. */
    fun of(dir: File, keepGoing: () -> Boolean = { true }): Long? {
        // walkFileTree על נתיב חסר קורא ל-visitFileFailed ומחזיר 0 — זה לא "תיקייה ריקה".
        if (!dir.isDirectory) return null
        var total = 0L
        var stopped = false
        val visitor = object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (keepGoing()) FileVisitResult.CONTINUE else stop()

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!keepGoing()) return stop()
                if (attrs.isRegularFile) total += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            private fun stop(): FileVisitResult {
                stopped = true
                return FileVisitResult.TERMINATE
            }
        }
        return try {
            Files.walkFileTree(dir.toPath(), visitor)
            if (stopped) null else total
        } catch (e: IOException) {
            null
        }
    }
}
