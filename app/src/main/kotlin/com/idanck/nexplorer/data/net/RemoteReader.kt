package com.idanck.nexplorer.data.net

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/**
 * קובץ ברשת שנקרא בבקשות "מ-offset, עד size בתים" — מה שאפליקציה אחרת שולחת דרך ה-FD של
 * [RemoteFilesProvider]. בקשה שממשיכה מאיפה שהקודמת נגמרה — מאותו זרם; קפיצה קדימה עד [SKIP_LIMIT] —
 * מדלגים בו; אחרת (אחורה, או רחוק) — זרם חדש מהמקום ([RemoteFs.openRead]). חוט אחד בכל פעם.
 */
class RemoteReader(private val fs: RemoteFs, private val path: String, val length: Long) : Closeable {

    private var input: InputStream? = null
    private var position = 0L

    /** כמו read של קובץ: [size] בתים, או פחות רק בסוף הקובץ (קריאה קצרה נראית כמו הסוף). 0 — הסוף. */
    fun read(offset: Long, size: Int, data: ByteArray): Int {
        if (offset >= length || size == 0) return 0
        try {
            val stream = streamAt(offset)
            var done = 0
            while (done < size) {
                val n = stream.read(data, done, size - done)
                if (n < 0) break
                done += n
            }
            position += done
            return done
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    private fun streamAt(offset: Long): InputStream {
        val current = input
        if (current != null && offset >= position && offset - position <= SKIP_LIMIT) {
            while (position < offset) {
                val n = current.skip(offset - position)
                if (n <= 0) break
                position += n
            }
            if (position == offset) return current
        }
        close()
        // הקורא מבקש גושים קטנים (דף, 4KB), ו-SFTP שולח בקשה לפי גודל הקריאה.
        return BufferedInputStream(fs.openRead(path, offset), BUFFER).also {
            input = it
            position = offset
        }
    }

    override fun close() {
        runCatching { input?.close() }
        input = null
    }

    companion object {
        private const val BUFFER = 256 * 1024
        /** קפיצה קדימה עד כאן — מדלגים בזרם הפתוח; מעבר — זרם חדש מהשרת. */
        const val SKIP_LIMIT = 1024 * 1024L
    }
}
