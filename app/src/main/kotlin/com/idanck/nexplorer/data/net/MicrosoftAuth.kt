package com.idanck.nexplorer.data.net

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/** PKCE (RFC 7636, ‏S256): הקוד שחוזר מהדפדפן שווה משהו רק עם [verifier], שלא יצא מהאפליקציה. */
data class Pkce(val verifier: String, val challenge: String, val state: String) {
    companion object {
        fun create(random: SecureRandom = SecureRandom()): Pkce {
            fun token() = ByteArray(32).also(random::nextBytes).let(::base64Url)
            val verifier = token()
            return Pkce(verifier, challengeOf(verifier), token())
        }

        fun challengeOf(verifier: String): String = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** ה-refresh token של חיבור אחד: מוצפן ב-Keystore ([CloudTokens]); בבדיקות — בזיכרון. */
interface TokenStore {
    fun get(): String?

    fun put(token: String)
}

/**
 * התחברות ל-Microsoft (OneDrive): OAuth 2.0 עם PKCE, בלי MSAL. [authorizeUrl] נפתח בדפדפן, והקוד שחוזר
 * ל-[redirectUri] מוחלף ב-[exchange]. אחר כך [token] מרענן לבד: ה-refresh token מתחלף בכל רענון, והחדש נשמר.
 * [tenant] — `consumers` (חשבונות Microsoft אישיים, כמו שהאפליקציה רשומה). בלי תלות באנדרואיד.
 */
class MicrosoftAuth(
    private val clientId: String,
    private val store: TokenStore,
    private val login: HttpUrl = LOGIN,
    private val tenant: String = TENANT,
    private val now: () -> Long = System::currentTimeMillis,
) : CloudAuth {

    private var access: String? = null
    private var expiresAt = 0L

    @Synchronized
    override fun token(): String {
        access?.takeIf { now() < expiresAt - EARLY_MS }?.let { return it }
        val refresh = store.get() ?: throw CloudSignInRequired(SIGN_IN_AGAIN)
        val tokens = request(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("scope", SCOPES)
                .build(),
        )
        tokens.refresh?.let(store::put)
        access = tokens.access
        expiresAt = now() + tokens.expiresInMs
        return tokens.access
    }

    @Synchronized
    override fun invalidate(token: String) {
        if (access == token) access = null
    }

    /** הדף שנפתח בדפדפן. `prompt=select_account` — גם בחיבור שני, לבחור חשבון. */
    fun authorizeUrl(redirectUri: String, pkce: Pkce): HttpUrl = endpoint("authorize").newBuilder()
        .addQueryParameter("client_id", clientId)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("response_mode", "query")
        .addQueryParameter("scope", SCOPES)
        .addQueryParameter("state", pkce.state)
        .addQueryParameter("code_challenge", pkce.challenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("prompt", "select_account")
        .build()

    /** הקוד מהדפדפן ← אסימונים. ה-refresh token נשמר ב-[store]. */
    @Synchronized
    fun exchange(code: String, redirectUri: String, pkce: Pkce) {
        val tokens = request(
            FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", pkce.verifier)
                .add("scope", SCOPES)
                .build(),
        )
        store.put(tokens.refresh ?: throw IOException("Microsoft לא החזיר הרשאה קבועה (offline_access)"))
        access = tokens.access
        expiresAt = now() + tokens.expiresInMs
    }

    private class Tokens(val access: String, val refresh: String?, val expiresInMs: Long)

    private fun request(form: FormBody): Tokens {
        val response = try {
            client.newCall(Request.Builder().url(endpoint("token")).post(form).build()).execute()
        } catch (e: IOException) {
            throw CloudHttp.networkError("Microsoft", e)
        }
        response.use {
            val json = runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrNull() ?: JSONObject()
            if (it.code == 200) {
                val access = json.optString("access_token").ifEmpty { null } ?: throw IOException("Microsoft לא החזיר אסימון")
                return Tokens(access, json.optString("refresh_token").ifEmpty { null }, json.optLong("expires_in", 3600) * 1000)
            }
            // invalid_grant: ההרשאה בוטלה, פגה (90 יום בלי שימוש) או שהסיסמה שונתה.
            throw when (json.optString("error")) {
                "invalid_grant", "interaction_required", "consent_required" -> CloudSignInRequired(SIGN_IN_AGAIN)
                "invalid_client", "unauthorized_client" -> IOException("האפליקציה לא רשומה נכון אצל Microsoft (${json.optString("error")})")
                else -> IOException("ההתחברות ל-Microsoft נכשלה (${it.code}${json.optString("error").ifEmpty { null }?.let { e -> ", $e" } ?: ""})")
            }
        }
    }

    private fun endpoint(name: String): HttpUrl = login.newBuilder().addPathSegment(tenant).addPathSegments("oauth2/v2.0").addPathSegment(name).build()

    companion object {
        val LOGIN: HttpUrl = "https://login.microsoftonline.com/".toHttpUrl()
        const val TENANT = "consumers"
        const val SCOPES = "Files.ReadWrite User.Read offline_access"
        private const val EARLY_MS = 2 * 60 * 1000L
        const val SIGN_IN_AGAIN = "ההתחברות ל-OneDrive פגה — ערוך את החיבור והתחבר מחדש"

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
