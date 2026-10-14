package com.idanck.nexplorer.data.net

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * סיסמאות של חיבורי רשת (לוח "חיבור חדש": "נשמר ב-Android Keystore"): AES-256-GCM במפתח שנוצר בתוך
 * ה-Keystore ולא יוצא ממנו. ב-DataStore נשמר רק הצופן ("iv:ciphertext" ב-Base64). הסרת האפליקציה מוחקת
 * את המפתח, ואין גיבוי (allowBackup=false) — סיסמה לא עוברת למכשיר אחר.
 */
object ConnectionSecrets {

    private const val ALIAS = "nexplorer.connections.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encrypt(password: CharArray): String {
        val plain = utf8(password)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.doFinal(plain)
            return b64(cipher.iv) + ":" + b64(sealed)
        } finally {
            plain.fill(0)
        }
    }

    /** [IOException] כשאין מה לפענח — מפתח שנמחק (שחזור נתונים) או צופן פגום. */
    fun decrypt(secret: String): CharArray {
        val (iv, sealed) = secret.split(':').takeIf { it.size == 2 }
            ?.let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            ?: throw unreadable()
        val plain = try {
            val key = existingKey() ?: throw unreadable()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }.doFinal(sealed)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw unreadable()
        }
        try {
            val chars = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain))
            return CharArray(chars.remaining()).also { chars.get(it) }
        } finally {
            plain.fill(0)
        }
    }

    private fun unreadable() = IOException("הסיסמה השמורה של החיבור לא נקראת — ערוך את החיבור והזן אותה שוב")

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    @Synchronized
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private fun utf8(chars: CharArray): ByteArray {
        val buffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
