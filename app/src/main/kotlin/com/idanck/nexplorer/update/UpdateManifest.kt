package com.idanck.nexplorer.update

import org.json.JSONObject

/**
 * `latest.json` שה-CI מפרסם ל-nexplorer-updates.idanck.com עם כל build של main.
 *
 * הפענוח קפדני בכוונה: המסמך הזה מחליט איזה קובץ יותקן מעל האפליקציה, ולכן שדה חסר,
 * פגום או כתובת בלי HTTPS מכשילים את כל הבדיקה במקום לקבל ברירת מחדל.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** SHA-256 בהקס קטן; נבדק אחרי ההורדה. */
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val notes: String?,
) {
    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

    companion object {
        /** ה-APK בסביבות 10 MB. סדר גודל מעל זה אומר שהמניפסט שגוי — לא ממלאים את המכשיר. */
        const val MAX_SIZE_BYTES: Long = 200L * 1024 * 1024

        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        fun parse(raw: String): Result<UpdateManifest> {
            val json = try {
                JSONObject(raw)
            } catch (e: Exception) {
                return invalid("המניפסט אינו JSON")
            }
            val versionCode = json.optInt("versionCode", 0)
            if (versionCode <= 0) return invalid("versionCode")
            val versionName = json.optString("versionName").trim()
            if (versionName.isEmpty()) return invalid("versionName")
            val apkUrl = json.optString("apkUrl").trim()
            // HTTP פשוט היה מאפשר לכל מי שבדרך להחליף את הקובץ.
            if (!apkUrl.startsWith("https://", ignoreCase = true)) return invalid("apkUrl")
            val sha256 = json.optString("sha256").trim().lowercase()
            if (!SHA256_HEX.matches(sha256)) return invalid("sha256")
            val sizeBytes = json.optLong("sizeBytes", 0L)
            if (sizeBytes <= 0L || sizeBytes > MAX_SIZE_BYTES) return invalid("sizeBytes")
            val notes = json.optJSONObject("notes")?.let { it.optString("he").ifBlank { it.optString("en") } }
                ?.trim()?.takeIf { it.isNotEmpty() }
            return Result.success(
                UpdateManifest(
                    versionCode = versionCode,
                    versionName = versionName,
                    apkUrl = apkUrl,
                    sha256 = sha256,
                    sizeBytes = sizeBytes,
                    minSdk = json.optInt("minSdk", 0),
                    notes = notes,
                ),
            )
        }

        private fun invalid(what: String): Result<UpdateManifest> =
            Result.failure(UpdateException("מניפסט עדכון פגום ($what)"))
    }
}

class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)
