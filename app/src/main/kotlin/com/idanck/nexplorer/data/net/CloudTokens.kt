package com.idanck.nexplorer.data.net

import android.content.Context

/**
 * ה-refresh tokens של חיבורי OneDrive, לפי המזהה של החיבור — מוצפנים ב-Keystore ([ConnectionSecrets]). לא
 * בתוך [NetConnection]: האסימון מתחלף בכל רענון, וחיבור ש"השתנה" נפתח מחדש ([RemoteFiles.fsFor]). ב-Google
 * אין מה לשמור — ההרשאה אצל Play services. אין גיבוי (allowBackup=false), כמו לסיסמאות.
 */
class CloudTokens(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** של חיבור אחד — ל-[MicrosoftAuth]. כתיבה ב-commit: האסימון הישן עלול להיות כבר לא בתוקף. */
    fun store(id: String): TokenStore = object : TokenStore {
        override fun get(): String? = prefs.getString(id, null)?.let { sealed -> String(ConnectionSecrets.decrypt(sealed)) }

        override fun put(token: String) {
            prefs.edit().putString(id, ConnectionSecrets.encrypt(token.toCharArray())).commit()
        }
    }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
    }

    private companion object {
        const val FILE = "cloud_tokens"
    }
}
