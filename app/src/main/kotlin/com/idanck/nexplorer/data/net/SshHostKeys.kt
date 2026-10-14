package com.idanck.nexplorer.data.net

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * המפתח של שרת SSH כמו בשורה של known_hosts: "ssh-ed25519 AAAA…" (הסוג ואז המפתח ב-Base64). כך הוא
 * נשמר ב-[NetConnection.hostKey], וממנו טביעת האצבע שמוצגת לאישור — אותה צורה ש-`ssh` מציג:
 * "SHA256:…" (Base64 בלי ריפוד).
 */
object SshHostKeys {

    fun encode(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        return "${KeyType.fromKey(key)} ${Base64.getEncoder().encodeToString(blob)}"
    }

    /** null — לא בצורה של [encode]. */
    fun fingerprint(hostKey: String): String? {
        val blob = runCatching { Base64.getDecoder().decode(hostKey.substringAfter(' ', "")) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    /** "ED25519", "RSA", "ECDSA" — כמו בסוגריים אחרי טביעת האצבע ב-`ssh`. */
    fun typeLabel(hostKey: String): String = when (val type = hostKey.substringBefore(' ')) {
        "ssh-ed25519" -> "ED25519"
        "ssh-rsa" -> "RSA"
        "ssh-dss" -> "DSA"
        else -> if (type.startsWith("ecdsa-")) "ECDSA" else type.uppercase()
    }
}
