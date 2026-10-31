package com.idanck.nexplorer.data.peer

import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ההצפנה של "העברה בין מכשירים" (שפת העיצוב 1.16, `docs/plan/08-peer.md`). בלי תלות באנדרואיד — נבדק ב-JVM.
 *
 * חיבור (פעם אחת לכל זוג מכשירים): ECDH על P-256, עם התחייבות (hash commitment, כמו ב-ZRTP) — הלקוח מתחייב למפתח
 * שלו לפני שהוא רואה את המפתח של השרת, ולכן מי שבאמצע לא יכול לחפש מפתחות שייתנו את אותו קוד. השרת מציג קוד של 6
 * ספרות שנגזר משני המפתחות ומשני ה-nonces, המשתמש מקליד אותו בלקוח, והלקוח משווה לקוד שהוא חישב. מפתח החיבור [pairKey]
 * נשמר בשני הצדדים (ב-Keystore), ומכאן כל שיחה גוזרת ממנו מפתחות חד-פעמיים ([sessionKeys]).
 */
object PeerCrypto {

    const val NONCE_BYTES = 32
    const val KEY_BYTES = 32

    private val random = SecureRandom()
    private val curve: ECPublicKey by lazy { keyPair().public as ECPublicKey }

    fun random(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    fun encode(key: PublicKey): ByteArray = key.encoded

    /** מפתח ציבורי מהצד השני — רק P-256; אחר (או פגום) — [IOException]. */
    fun decodePublic(bytes: ByteArray): PublicKey {
        val key = try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes)) as ECPublicKey
        } catch (e: Exception) {
            throw IOException("מפתח לא תקין מהמכשיר השני", e)
        }
        if (key.params.curve != curve.params.curve) throw IOException("מפתח לא תקין מהמכשיר השני")
        return key
    }

    fun agree(own: PrivateKey, other: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply {
            init(own)
            doPhase(other, true)
        }.generateSecret()

    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        parts.forEach { update(it) }
        digest()
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { update(it) }
        doFinal()
    }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = KEY_BYTES): ByteArray {
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous, info, byteArrayOf(counter.toByte()))
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    /** מה שהלקוח שולח לפני שהוא רואה את המפתח של השרת. */
    fun commit(clientPublic: ByteArray, clientNonce: ByteArray): ByteArray = sha256(COMMIT, clientPublic, clientNonce)

    fun pairKey(shared: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): ByteArray =
        hkdf(shared, clientNonce + serverNonce, "nexplorer-pair-v1".toByteArray())

    /** הקוד שהשרת מציג והלקוח מקליד: 6 ספרות מכל מה שהוחלף בחיבור. */
    fun code(clientPublic: ByteArray, serverPublic: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): String {
        val h = sha256("nexplorer-sas-v1".toByteArray(), clientPublic, serverPublic, clientNonce, serverNonce)
        val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    fun clientConfirm(key: ByteArray): ByteArray = hmac(key, "client-confirm".toByteArray())

    fun serverConfirm(key: ByteArray): ByteArray = hmac(key, "server-confirm".toByteArray())

    /** מפתחות השיחה: לכל כיוון מפתח משלו, משני ה-nonces של השיחה. */
    fun sessionKeys(pairKey: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): Pair<ByteArray, ByteArray> {
        val salt = clientNonce + serverNonce
        return hkdf(pairKey, salt, "c2s".toByteArray()) to hkdf(pairKey, salt, "s2c".toByteArray())
    }

    /** השוואה בזמן קבוע. */
    fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun sameCode(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private val COMMIT = "nexplorer-commit-v1".toByteArray()
}
