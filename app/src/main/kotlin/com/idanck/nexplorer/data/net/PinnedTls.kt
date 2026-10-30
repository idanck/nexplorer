package com.idanck.nexplorer.data.net

import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * https לשרת בבית (WebDAV, ‏Synology): תעודה שהמערכת מכירה — כמו בדפדפן. תעודה עצמית, או תעודה לשם אחר (גישה
 * בכתובת IP לשרת עם תעודה לדומיין) — רק אם היא זו שאושרה ([pinned], "x509-sha256 …"). את מה שהוצג שומר
 * ([offered]) — לשאלה "לסמוך על השרת?" ([HostKeyUntrusted]).
 */
internal class PinnedTls(private val pinned: String?) {

    @Volatile
    var offered: String? = null
        private set

    private val trust = Trust()

    /** מנהל האמון ובדיקת השם — על [builder] של חיבור ב-https. */
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return builder
            .sslSocketFactory(context.socketFactory, trust)
            // תעודה שאושרה — גם כשהשם שבה אינו הכתובת (תעודה עצמית ל-"nas.local", גישה ב-IP).
            .hostnameVerifier { host, session -> isPinned(session) || verifyName(host, session) }
    }

    /**
     * כשל בלחיצת היד בגלל התעודה (או השם שבה) — [HostKeyUntrusted] עם התעודה שהוצגה, אם היא לא זו שאושרה.
     * null — לא כשל כזה (ניתוק באמצע העברה הוא גם SSLException).
     */
    fun untrusted(e: Throwable): HostKeyUntrusted? {
        val chain = generateSequence(e) { it.cause }.toList()
        if (chain.none { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) return null
        val key = offered ?: return null
        return if (key != pinned) HostKeyUntrusted(key, pinned != null) else null
    }

    private fun isPinned(session: SSLSession): Boolean =
        pinned != null && (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::certKey) == pinned

    private fun verifyName(host: String, session: SSLSession): Boolean {
        if (OkHostnameVerifier.verify(host, session)) return true
        // תעודה של רשות מוכרת, אבל לשם אחר — גם היא ניתנת לאישור (כתובת IP לשרת עם תעודה לדומיין).
        offered = (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::certKey)
        return false
    }

    /**
     * תעודה שהמערכת מכירה, או זו שאושרה. Extended: באנדרואיד, עם network_security_config שיש בו domain-config,
     * מנהל האמון של המערכת דוחה כל בדיקה בלי שם השרת — וכל תעודה של רשות מוכרת הייתה נראית "עצמית".
     */
    private inner class Trust : X509ExtendedTrustManager() {
        private val system: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

        private fun check(chain: Array<X509Certificate>, systemCheck: () -> Unit) {
            val key = certKey(chain.first())
            if (key == pinned) return
            try {
                systemCheck()
            } catch (e: CertificateException) {
                offered = key
                throw e
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = check(chain) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: system.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = check(chain) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: system.checkServerTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = check(chain) {
            system.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = system.checkClientTrusted(chain, authType)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = system.checkClientTrusted(chain, authType)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    companion object {
        /** "x509-sha256 AB:CD:…" — טביעת ה-SHA-256 של התעודה, כמו שדפדפן מציג אותה. */
        fun certKey(certificate: X509Certificate): String =
            "x509-sha256 " + MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
    }
}
