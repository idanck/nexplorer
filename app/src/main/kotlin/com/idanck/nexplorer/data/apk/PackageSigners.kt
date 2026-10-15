package com.idanck.nexplorer.data.apk

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/** תעודות החתימה של חבילה, כ-SHA-256 — לעדכון העצמי ולגיליון ה-APK. */
object PackageSigners {

    /**
     * לחבילה מותקנת ול-getPackageArchiveInfo (קובץ APK). ב-API 28–29 קובץ APK קיבל תעודות רק עם
     * GET_SIGNATURES — ה-parser החדש תיקן ב-30, שהוא ה-minSdk (D9).
     */
    const val FLAGS: Int = PackageManager.GET_SIGNING_CERTIFICATES

    /** כל התעודות, לא אחת מהן — משווים את כולן. */
    fun of(info: PackageInfo): Set<String> {
        val signatures = info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().mapNotNull { it?.toByteArray()?.let { bytes -> digest.digest(bytes).hex() } }.toSet()
    }

    private fun ByteArray.hex(): String = joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
}
