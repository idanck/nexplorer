package com.idanck.nexplorer.data.net

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.idanck.nexplorer.BuildConfig
import com.idanck.nexplorer.core.NetProtocol
import java.io.IOException

/**
 * האסימון של כל חיבור ענן: Google — Play services ([GoogleAuth]); OneDrive — refresh token מ-[CloudTokens]
 * ([MicrosoftAuth]). נקרא כש-[RemoteFiles] פותח חיבור.
 */
class CloudAuths(private val context: Context, val tokens: CloudTokens) {

    fun forConnection(connection: NetConnection): CloudAuth {
        override?.let { return it(connection) }
        return when (connection.protocol) {
            NetProtocol.GoogleDrive -> GoogleAuth(context, connection.user)
            NetProtocol.OneDrive -> microsoft(tokens.store(connection.id))
            else -> throw IOException("${connection.protocol.label} אינו שירות ענן")
        }
    }

    companion object {
        /**
         * בבדיקות על האמולטור: אסימון לשרת המדומה במקום התחברות אמיתית (אין חשבון באמולטור). רק בדיקות מציבות
         * אותו.
         */
        @VisibleForTesting
        @Volatile
        var override: ((NetConnection) -> CloudAuth)? = null

        /** ה-client ID של האפליקציה אצל Microsoft (`nexplorer.microsoftClientId` ב-gradle.properties); ריק — לא רשומה. */
        val microsoftClientId: String get() = BuildConfig.MICROSOFT_CLIENT_ID

        /** החזרה מדף ההתחברות של Microsoft אל [com.idanck.nexplorer.ui.net.OAuthRedirectActivity]. */
        val microsoftRedirect: String get() = "${BuildConfig.APPLICATION_ID}://oauth"

        fun microsoft(store: TokenStore): MicrosoftAuth {
            if (microsoftClientId.isBlank()) throw IOException("OneDrive עוד לא מוגדר בגרסה הזו")
            return MicrosoftAuth(microsoftClientId, store)
        }
    }
}

/** מה שחוזר מההתחברות ב"הוסף מקום", לפני שהחיבור נבדק ונשמר. */
sealed interface CloudGrant {
    /** Google: אסימון גישה מ-Play services (ההרשאה עצמה נשארת שם). */
    data class Google(val accessToken: String) : CloudGrant

    /** Microsoft: הקוד מהדפדפן — מוחלף באסימונים עם ה-verifier של [pkce]. */
    data class Microsoft(val code: String, val pkce: Pkce, val redirectUri: String) : CloudGrant
}
