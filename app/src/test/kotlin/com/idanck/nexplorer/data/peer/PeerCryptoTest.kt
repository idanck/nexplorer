package com.idanck.nexplorer.data.peer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PeerCryptoTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val okm = PeerCrypto.hkdf(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
    }

    @Test
    fun bothSidesDeriveTheSameKeyAndCode() {
        val c = PeerCrypto.keyPair()
        val s = PeerCrypto.keyPair()
        val nc = PeerCrypto.random(32)
        val ns = PeerCrypto.random(32)
        val cp = PeerCrypto.encode(c.public)
        val sp = PeerCrypto.encode(s.public)
        val kc = PeerCrypto.pairKey(PeerCrypto.agree(c.private, PeerCrypto.decodePublic(sp)), nc, ns)
        val ks = PeerCrypto.pairKey(PeerCrypto.agree(s.private, PeerCrypto.decodePublic(cp)), nc, ns)
        assertArrayEquals(kc, ks)
        val code = PeerCrypto.code(cp, sp, nc, ns)
        assertEquals(6, code.length)
        assertTrue(code.all(Char::isDigit))
        assertEquals(code, PeerCrypto.code(cp, sp, nc, ns))
    }

    @Test
    fun commitBindsKeyAndNonce() {
        val cp = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val other = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val nc = PeerCrypto.random(32)
        val commit = PeerCrypto.commit(cp, nc)
        assertTrue(PeerCrypto.same(commit, PeerCrypto.commit(cp, nc)))
        assertFalse(PeerCrypto.same(commit, PeerCrypto.commit(other, nc)))
        assertFalse(PeerCrypto.same(commit, PeerCrypto.commit(cp, PeerCrypto.random(32))))
    }

    /** מישהו באמצע מחליף את המפתחות: הקוד בטלוויזיה והקוד שהטלפון מחשב — שונים (אלא בהסתברות 1:10^6). */
    @Test
    fun keySubstitutionChangesTheCode() {
        val phone = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val tv = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val mitmToTv = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val mitmToPhone = PeerCrypto.encode(PeerCrypto.keyPair().public)
        val nc = PeerCrypto.random(32)
        val ns = PeerCrypto.random(32)
        val shownOnTv = PeerCrypto.code(mitmToTv, tv, nc, ns)
        val expectedOnPhone = PeerCrypto.code(phone, mitmToPhone, nc, ns)
        assertNotEquals(shownOnTv, expectedOnPhone)
    }

    @Test
    fun sessionKeysDifferPerDirectionAndSession() {
        val k = PeerCrypto.random(32)
        val (a, b) = PeerCrypto.sessionKeys(k, PeerCrypto.random(32), PeerCrypto.random(32))
        val (c, _) = PeerCrypto.sessionKeys(k, PeerCrypto.random(32), PeerCrypto.random(32))
        assertFalse(PeerCrypto.same(a, b))
        assertFalse(PeerCrypto.same(a, c))
    }

    @Test(expected = IOException::class)
    fun rejectsGarbagePublicKey() {
        PeerCrypto.decodePublic(ByteArray(65) { 4 })
    }

    @Test
    fun confirmTagsDependOnKey() {
        val k = PeerCrypto.random(32)
        assertFalse(PeerCrypto.same(PeerCrypto.clientConfirm(k), PeerCrypto.serverConfirm(k)))
        assertFalse(PeerCrypto.same(PeerCrypto.clientConfirm(k), PeerCrypto.clientConfirm(PeerCrypto.random(32))))
    }
}
