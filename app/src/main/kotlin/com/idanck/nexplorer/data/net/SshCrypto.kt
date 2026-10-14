package com.idanck.nexplorer.data.net

import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyAgreementSpi
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi
import org.bouncycastle.jcajce.provider.asymmetric.edec.SignatureSpi
import java.security.Provider
import java.security.Security

/**
 * ההצפנה של SSH (sshj). sshj מבקש מ-JCA את Ed25519 (מפתחות של שרתים ושל משתמשים) ואת X25519 (החלפת
 * מפתחות, curve25519-sha256) — ובאנדרואיד הם קיימים רק מגרסה 13. לרשום את BouncyCastle כולו לא עובד:
 * לאנדרואיד יש ספק "BC" מקוצץ משלו, ו-sshj היה משתמש בו לכל אלגוריתם. במקום זה — ספק קטן עם ששת
 * השירותים בלבד מתוך bcprov (שכבר באפליקציה בשביל smbj). הוא ראשון בתור, גם באנדרואיד 13 ומעלה: sshj
 * מבקש את המפתח, את ההסכמה ואת החתימה בנפרד, ומפתח מספק אחד בהסכמה של ספק אחר עלול להידחות — כך כולם
 * מאותו מקום בכל גרסה. AES, ‏HMAC, ‏ECDH ו-RSA — מהמערכת, במהירות שלה (הספק לא מכיר אותם). המחלקות
 * נוצרות ישירות ולא לפי שם — R8 רואה אותן ושומר.
 */
internal object SshCrypto {

    internal const val PROVIDER = "NexplorerEdEC"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            // בלי זה sshj מוצא את ה-"BC" של המערכת ומבקש ממנו הכול.
            SecurityUtils.setRegisterBouncyCastle(false)
            if (Security.getProvider(PROVIDER) == null) Security.insertProviderAt(EdEcProvider(), 1)
            installed = true
        }
    }

    internal class EdEcProvider : Provider(PROVIDER, 1.0, "Ed25519 / X25519 (BouncyCastle) ל-SSH") {
        init {
            add("KeyFactory", "Ed25519", KeyFactorySpi.Ed25519::class.java) { KeyFactorySpi.Ed25519() }
            add("Signature", "Ed25519", SignatureSpi.Ed25519::class.java) { SignatureSpi.Ed25519() }
            add("KeyPairGenerator", "Ed25519", KeyPairGeneratorSpi.Ed25519::class.java) { KeyPairGeneratorSpi.Ed25519() }
            add("KeyFactory", "X25519", KeyFactorySpi.X25519::class.java) { KeyFactorySpi.X25519() }
            add("KeyAgreement", "X25519", KeyAgreementSpi.X25519::class.java) { KeyAgreementSpi.X25519() }
            add("KeyPairGenerator", "X25519", KeyPairGeneratorSpi.X25519::class.java) { KeyPairGeneratorSpi.X25519() }
        }

        private fun add(type: String, algorithm: String, spi: Class<*>, create: () -> Any) {
            putService(
                object : Service(this, type, algorithm, spi.name, null, null) {
                    override fun newInstance(constructorParameter: Any?): Any = create()
                },
            )
        }
    }
}
