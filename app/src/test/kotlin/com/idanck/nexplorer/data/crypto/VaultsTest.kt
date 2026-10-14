package com.idanck.nexplorer.data.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VaultsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val password = "כספת 1".toCharArray()

    @Test
    fun `create, unlock with the right password, refuse the wrong one`() = runBlocking {
        val (vault, created) = Vaults.create(tmp.root, "Vault", password)
        created.close()
        assertTrue(Vaults.isVault(vault))
        assertEquals(listOf(Vaults.MARKER), vault.list()!!.toList())

        Vaults.unlock(vault, password).close()
        try {
            Vaults.unlock(vault, "אחרת".toCharArray())
            fail("נפתחה בסיסמה שגויה")
        } catch (e: EncryptedFiles.WrongPassword) {
            // צפוי
        }
    }

    @Test
    fun `files sealed in a vault share its salt and open alone with the password`() = runBlocking {
        val (vault, created) = Vaults.create(tmp.root, "Vault", password)
        created.close()
        val keys = Vaults.unlock(vault, password)
        val photo = tmp.newFile("photo.jpg").apply { writeBytes(ByteArray(70_000) { it.toByte() }) }
        val sealed = EncryptedFiles.encryptFile(photo, vault, keys.copy(), {}, {})
        assertArrayEquals(
            EncryptedFiles.passwordSaltOf(File(vault, Vaults.MARKER)).first,
            EncryptedFiles.passwordSaltOf(sealed).first,
        )
        // בלי התיקייה: סיסמה בלבד, כמו כל קובץ מוצפן.
        val out = tmp.newFolder("out")
        val restored = KeyRing(password).use { EncryptedFiles.decryptFile(sealed, out, it, {}, {}) }
        assertArrayEquals(photo.readBytes(), restored.readBytes())
        keys.close()
    }

    @Test
    fun `a copy outlives the original`() = runBlocking {
        val (vault, keys) = Vaults.create(tmp.root, "Vault", password)
        val copy = keys.copy()
        keys.close()
        val file = tmp.newFile("a.txt").apply { writeText("שלום") }
        val sealed = EncryptedFiles.encryptFile(file, vault, copy, {}, {})
        assertTrue(EncryptedFiles.isEncrypted(sealed))
        copy.close()
    }

    @Test
    fun `root of nested paths and refusal to nest`() = runBlocking {
        val (vault, keys) = Vaults.create(tmp.root, "Vault", password)
        keys.close()
        val deep = File(vault, "a/b").apply { mkdirs() }
        assertEquals(vault, Vaults.rootOf(deep))
        assertEquals(vault, Vaults.rootOf(File(deep, "x.enc")))
        assertEquals(vault, Vaults.rootOf(vault))
        assertNull(Vaults.rootOf(tmp.root))
        try {
            Vaults.create(deep, "Inner", password)
            fail("נוצרה תיקייה מוצפנת בתוך תיקייה מוצפנת")
        } catch (e: IOException) {
            assertFalse(File(deep, "Inner").exists())
        }
        try {
            Vaults.create(tmp.root, "Vault", password)
            fail("נדרסה תיקייה קיימת")
        } catch (e: IOException) {
            // צפוי
        }
    }

    @Test
    fun `vault keys hand out copies and forget on lock`() = runBlocking {
        val (vault, keys) = Vaults.create(tmp.root, "Vault", password)
        val vaults = VaultKeys()
        vaults.put(vault, keys)
        assertTrue(vaults.isUnlocked(vault))
        assertEquals(setOf(vault.absolutePath), vaults.unlocked.value)
        val copy = vaults.copyFor(vault)!!
        vaults.lockAll()
        assertFalse(vaults.isUnlocked(vault))
        assertNull(vaults.copyFor(vault))
        assertTrue(vaults.unlocked.value.isEmpty())
        // העותק עדיין עובד אחרי הנעילה.
        val file = tmp.newFile("b.bin").apply { writeBytes(ByteArray(10)) }
        EncryptedFiles.encryptFile(file, vault, copy, {}, {})
        copy.close()
    }
}
