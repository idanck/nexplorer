package com.idanck.nexplorer.data.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

class EncryptedFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val chunk = 1 shl 16

    /** מעט חזרות — הבדיקות בודקות את הפורמט, לא את העלות. */
    private fun ring(password: String = "סיסמה-123") = KeyRing(password.toCharArray(), iterations = 10_000)

    private fun seal(plain: ByteArray, keys: KeyRing): ByteArray = runBlocking {
        ByteArrayOutputStream().also { EncryptedFiles.encrypt(ByteArrayInputStream(plain), it, keys) }.toByteArray()
    }

    private fun open(sealed: ByteArray, keys: KeyRing): ByteArray = runBlocking {
        ByteArrayOutputStream().also { EncryptedFiles.decrypt(ByteArrayInputStream(sealed), it, keys) }.toByteArray()
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `round trip on every chunk boundary`() {
        val keys = ring()
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 200_003)) {
            val plain = Random(size).nextBytes(size)
            val sealed = seal(plain, keys)
            val chunks = maxOf(1, (size + chunk - 1) / chunk)
            assertEquals("size $size", EncryptedFiles.HEADER_SIZE + size + 16L * chunks, sealed.size.toLong())
            assertArrayEquals("size $size", plain, open(sealed, keys))
        }
    }

    @Test
    fun `wrong password is reported as such`() {
        val sealed = seal("שלום".toByteArray(), ring("נכונה"))
        try {
            open(sealed, ring("שגויה"))
            fail("נפתח בסיסמה שגויה")
        } catch (e: EncryptedFiles.WrongPassword) {
            assertEquals("סיסמה שגויה", e.message)
        }
    }

    @Test
    fun `password is normalized before hashing`() {
        // "שָׁ" בשני סדרי ניקוד שונים — NFC מאחד אותם.
        val a = "שָׁ"
        val b = "שָׁ"
        val sealed = seal(byteArrayOf(1, 2, 3), ring(a))
        assertArrayEquals(byteArrayOf(1, 2, 3), open(sealed, ring(b)))
    }

    @Test
    fun `pbkdf2 matches RFC 7914 vectors and the JDK`() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(EncryptedFiles.pbkdf2("password".toByteArray(), "salt".toByteArray(), 1)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(EncryptedFiles.pbkdf2("password".toByteArray(), "salt".toByteArray(), 4096)),
        )
        // ה-JDK מקודד את הסיסמה ב-UTF-8 — כמו כאן.
        val salt = ByteArray(16) { it.toByte() }
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("סיסמה".toCharArray(), salt, 2000, 256)).encoded
        assertArrayEquals(jdk, EncryptedFiles.pbkdf2(EncryptedFiles.passwordBytes("סיסמה".toCharArray()), salt, 2000))
    }

    @Test
    fun `a changed byte anywhere is caught`() {
        val keys = ring()
        val sealed = seal(Random(1).nextBytes(3 * chunk + 10), keys)
        // חלק שני (לא הראשון) — "פגום", לא "סיסמה שגויה".
        val tampered = sealed.copyOf().also { it[EncryptedFiles.HEADER_SIZE + chunk + 16 + 5] = (it[EncryptedFiles.HEADER_SIZE + chunk + 16 + 5] + 1).toByte() }
        assertDamaged { open(tampered, keys) }
        // שינוי בכותרת משנה את המפתח: החלק הראשון לא נפתח.
        val header = sealed.copyOf().also { it[EncryptedFiles.HEADER_SIZE - 1] = (it[EncryptedFiles.HEADER_SIZE - 1] + 1).toByte() }
        try {
            open(header, keys)
            fail("כותרת ששונתה נפתחה")
        } catch (e: IOException) {
            // צפוי
        }
    }

    @Test
    fun `truncation and appended bytes are caught`() {
        val keys = ring()
        val sealed = seal(Random(2).nextBytes(3 * chunk + 10), keys)
        // חיתוך בגבול חלק: בלי סימון "אחרון" שהיה רק בחלק שנחתך.
        assertDamaged { open(sealed.copyOf(EncryptedFiles.HEADER_SIZE + 2 * (chunk + 16)), keys) }
        // נשאר רק החלק הראשון: הסיסמה נכונה, אז "קטוע" ולא "סיסמה שגויה".
        assertDamaged { open(sealed.copyOf(EncryptedFiles.HEADER_SIZE + chunk + 16), keys) }
        assertDamaged { open(sealed + byteArrayOf(0), keys) }
        assertDamaged { open(sealed.copyOf(EncryptedFiles.HEADER_SIZE + 5), keys) }
        assertDamaged { open("not encrypted at all, just text that is long enough".toByteArray(), keys) }
    }

    private fun assertDamaged(block: () -> Unit) {
        try {
            block()
            fail("קובץ פגום נפתח")
        } catch (e: EncryptedFiles.Damaged) {
            // צפוי
        }
    }

    @Test
    fun `one salt per operation, a new key per file`() {
        val keys = ring()
        val a = seal(byteArrayOf(1), keys)
        val b = seal(byteArrayOf(1), keys)
        val passwordSalt = 11 until 27
        val fileSalt = 27 until 43
        assertArrayEquals(a.sliceArray(passwordSalt), b.sliceArray(passwordSalt))
        assertNotEquals(hex(a.sliceArray(fileSalt)), hex(b.sliceArray(fileSalt)))
        assertNotEquals(hex(a.sliceArray(43 until a.size)), hex(b.sliceArray(43 until b.size)))
        // פעולה אחרת — מלח אחר.
        assertNotEquals(hex(a.sliceArray(passwordSalt)), hex(seal(byteArrayOf(1), ring()).sliceArray(passwordSalt)))
    }

    @Test
    fun `files keep their name, extension and date`() = runBlocking {
        val dir = tmp.newFolder("d")
        val photo = File(dir, "photo.jpg").apply { writeBytes(Random(3).nextBytes(100_000)) }
        photo.setLastModified(1_700_000_000_000)
        File(dir, "photo.jpg.enc").writeText("taken")
        val keys = ring()
        var counted = 0L
        val sealed = EncryptedFiles.encryptFile(photo, dir, keys, {}, { counted += it })
        assertEquals("photo (1).jpg.enc", sealed.name)
        assertEquals("נספר בכתיבה ובאימות", 200_000L, counted)
        assertEquals(1_700_000_000_000, sealed.lastModified())
        assertTrue(EncryptedFiles.isEncrypted(sealed))
        assertFalse(EncryptedFiles.isEncrypted(photo))
        assertTrue("המקור לא נמחק כאן", photo.exists())

        val out = tmp.newFolder("out")
        val restored = EncryptedFiles.decryptFile(sealed, out, keys, {}, {})
        assertEquals("photo (1).jpg", restored.name)
        assertArrayEquals(photo.readBytes(), restored.readBytes())
        assertEquals(1_700_000_000_000, restored.lastModified())
        assertFalse(dir.walk().plus(out.walk()).any { it.name.endsWith(".nexplorer-part") })
    }

    @Test
    fun `wrong password and cancel leave nothing behind`() = runBlocking {
        val dir = tmp.newFolder("d")
        val file = File(dir, "a.bin").apply { writeBytes(Random(4).nextBytes(3 * chunk)) }
        val sealed = EncryptedFiles.encryptFile(file, dir, ring("x1"), {}, {})
        try {
            EncryptedFiles.decryptFile(sealed, dir, ring("x2"), {}, {})
            fail("נפתח בסיסמה שגויה")
        } catch (e: EncryptedFiles.WrongPassword) {
            // צפוי
        }
        var calls = 0
        try {
            EncryptedFiles.encryptFile(file, dir, ring(), { if (++calls == 2) throw IllegalStateException("cancel") }, {})
            fail("לא בוטל")
        } catch (e: IllegalStateException) {
            // צפוי
        }
        assertEquals(setOf("a.bin", "a.bin.enc"), dir.list()!!.toSet())
    }

    @Test
    fun `names`() {
        assertEquals("photo.jpg", EncryptedFiles.plainName("photo.jpg.enc"))
        assertEquals("photo.jpg", EncryptedFiles.plainName("photo.jpg.ENC"))
        assertEquals("renamed", EncryptedFiles.plainName("renamed"))
        assertEquals(".enc", EncryptedFiles.plainName(".enc"))
        assertTrue(EncryptedFiles.hasEncryptedName("a.enc"))
        assertFalse(EncryptedFiles.hasEncryptedName(".enc"))
        assertFalse(EncryptedFiles.hasEncryptedName("a.encx"))
    }
}
