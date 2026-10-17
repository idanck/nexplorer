package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.Entry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.random.Random

/** שרת בזיכרון לקובץ אחד: סופר כמה זרמים נפתחו (בקשה לשרת), ומאיזה מקום. */
private class OneFile(val data: ByteArray) : RemoteFs {
    val opens = mutableListOf<Long>()
    var failAfter: Int? = null
    var closed = 0

    override fun openRead(path: String, offset: Long): InputStream {
        opens += offset
        val bytes = if (offset >= data.size) ByteArray(0) else data.copyOfRange(offset.toInt(), data.size)
        val limit = failAfter
        return object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (limit == null) return super.read(b, off, len)
                if (pos >= limit) throw IOException("החיבור לשרת נותק")
                return super.read(b, off, minOf(len, limit - pos))
            }

            override fun close() {
                closed++
            }
        }
    }

    override fun list(path: String): List<Entry> = throw UnsupportedOperationException()
    override fun stat(path: String): Entry? = throw UnsupportedOperationException()
    override fun openWrite(path: String, size: Long): OutputStream = throw UnsupportedOperationException()
    override fun mkdir(path: String) = throw UnsupportedOperationException()
    override fun delete(path: String, isDirectory: Boolean) = throw UnsupportedOperationException()
    override fun rename(path: String, target: String) = throw UnsupportedOperationException()
    override fun setLastModified(path: String, time: Long) = throw UnsupportedOperationException()
    override fun space(path: String): RemoteSpace? = null
    override fun close() = Unit
}

class RemoteReaderTest {

    private val data = Random(5).nextBytes(3 * 1024 * 1024 + 17)

    private fun RemoteReader.bytes(offset: Long, size: Int): ByteArray {
        val buffer = ByteArray(size)
        val n = read(offset, size, buffer)
        return buffer.copyOf(n)
    }

    private fun expected(offset: Long, size: Int) = data.copyOfRange(offset.toInt(), minOf(data.size, offset.toInt() + size))

    @Test
    fun `sequential reads share one stream from the server`() {
        val fs = OneFile(data)
        val reader = RemoteReader(fs, "smb://a/s/f", data.size.toLong())
        var offset = 0L
        while (offset < data.size) {
            val got = reader.bytes(offset, 4096)
            assertArrayEquals(expected(offset, 4096), got)
            offset += got.size
        }
        assertEquals(0, reader.bytes(offset, 4096).size)
        assertEquals(listOf(0L), fs.opens)
    }

    @Test
    fun `a near jump forward skips, a jump back or far ahead opens a new stream`() {
        val fs = OneFile(data)
        val reader = RemoteReader(fs, "smb://a/s/f", data.size.toLong())
        assertArrayEquals(expected(0, 100), reader.bytes(0, 100))
        assertArrayEquals(expected(300_000, 100), reader.bytes(300_000, 100))
        assertEquals("קפיצה קרובה קדימה — באותו זרם", listOf(0L), fs.opens)
        assertArrayEquals(expected(50, 100), reader.bytes(50, 100))
        assertEquals("אחורה — זרם חדש", listOf(0L, 50L), fs.opens)
        val far = 150 + RemoteReader.SKIP_LIMIT + 1
        assertArrayEquals(expected(far, 100), reader.bytes(far, 100))
        assertEquals("רחוק קדימה — זרם חדש", listOf(0L, 50L, far), fs.opens)
        assertEquals("כל זרם שהוחלף נסגר", 2, fs.closed)
    }

    @Test
    fun `the end of the file is a short read, and past it nothing`() {
        val fs = OneFile(data)
        val reader = RemoteReader(fs, "smb://a/s/f", data.size.toLong())
        val tail = data.size - 10L
        assertArrayEquals(expected(tail, 10), reader.bytes(tail, 4096))
        assertEquals(0, reader.bytes(data.size.toLong(), 4096).size)
        assertEquals(0, reader.bytes(data.size + 100L, 4096).size)
        assertEquals(listOf(tail), fs.opens)
    }

    @Test
    fun `a failed read is thrown, and the next read opens again`() {
        val fs = OneFile(data).apply { failAfter = 500_000 }
        val reader = RemoteReader(fs, "smb://a/s/f", data.size.toLong())
        val failure = runCatching { reader.bytes(0, 600_000) }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
        fs.failAfter = null
        assertArrayEquals(expected(10, 100), reader.bytes(10, 100))
        assertEquals(listOf(0L, 10L), fs.opens)
        reader.close()
        assertEquals(2, fs.closed)
    }
}
