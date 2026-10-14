package com.idanck.nexplorer.data.apk

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** תעודות החתימה של חבילה, כ-SHA-256 — לעדכון העצמי ולגיליון ה-APK. */
object PackageSigners {

    /** לחבילה מותקנת. */
    val installedFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }

    /**
     * ל-getPackageArchiveInfo (קובץ APK). ב-API 28–29 הוא אוסף תעודות רק עם GET_SIGNATURES (ה-parser
     * החדש תיקן ב-30); בלעדיו signingInfo ריק, וכל APK נראה "בלי חתימה".
     */
    @Suppress("DEPRECATION")
    val archiveFlags: Int = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
        installedFlags or PackageManager.GET_SIGNATURES
    } else {
        installedFlags
    }

    @SuppressLint("PackageManagerGetSignatures") // משווים את כל התעודות, לא סומכים על אחת מהן.
    fun of(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().mapNotNull { it?.toByteArray()?.let { bytes -> digest.digest(bytes).hex() } }.toSet()
    }

    private fun ByteArray.hex(): String = joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
}
