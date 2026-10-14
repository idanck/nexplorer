package com.idanck.nexplorer.data.net

import net.schmizz.sshj.common.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/** טביעות האצבע כמו ש-`ssh-keygen -l -E sha256` מציג אותן (הערכים נוצרו בו). */
class SshHostKeysTest {

    private val ed25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIARk2H5Oiday1HWmOEmiYJHdVqW5oLiGDoUrxA4N8FWP"
    private val ecdsa = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBBPeSEtaZo+BXrwXvQcbhN5q9ihAEVA+eUC6tcvNiA/wXYQp4nMESvLbfw1gl1QxVDK32yuQRwUJlMbEdq/W/A8="

    @Test
    fun `fingerprints match ssh-keygen`() {
        assertEquals("SHA256:D5+W/fqDhQGOFlEIJr/5vInqwzAUW+13E0dY2MaF9sM", SshHostKeys.fingerprint(ed25519))
        assertEquals("SHA256:uQ9x6DtFonJ8O8QFJ/5oxaDsgd9HIC7sEmPZq8PpQAI", SshHostKeys.fingerprint(ecdsa))
        assertNull(SshHostKeys.fingerprint("ssh-ed25519"))
        assertNull(SshHostKeys.fingerprint("ssh-ed25519 !!!"))
    }

    @Test
    fun `a key read from the wire encodes back to its known_hosts form`() {
        for (line in listOf(ed25519, ecdsa)) {
            val key = Buffer.PlainBuffer(Base64.getDecoder().decode(line.substringAfter(' '))).readPublicKey()
            assertEquals(line, SshHostKeys.encode(key))
        }
    }

    @Test
    fun `type labels`() {
        assertEquals("ED25519", SshHostKeys.typeLabel(ed25519))
        assertEquals("ECDSA", SshHostKeys.typeLabel(ecdsa))
        assertEquals("RSA", SshHostKeys.typeLabel("ssh-rsa AAAA"))
    }
}
