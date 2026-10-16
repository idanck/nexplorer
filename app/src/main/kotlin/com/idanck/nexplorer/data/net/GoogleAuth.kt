package com.idanck.nexplorer.data.net

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Google Drive דרך Google Play services (`AuthorizationClient`): אין client ID בקוד — Google מזהה את האפליקציה
 * לפי שם החבילה וטביעת ה-SHA-1 של מפתח החתימה. האסימון (שעה) נשמר אצל Play services, ו-[token] מחזיר אותו בלי
 * חלון כל עוד ההרשאה בתוקף. [account] — החשבון של החיבור (אחרי ההתחברות ב"הוסף מקום").
 */
class GoogleAuth(context: Context, private val account: String) : CloudAuth {

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    override fun token(): String {
        val result = await { client.authorize(request(Account(account, ACCOUNT_TYPE))) }
        // צריך חלון (ההרשאה בוטלה בחשבון, או שהחשבון הוסר מהמכשיר) — רק "ערוך" במגירה מציג אותו.
        if (result.hasResolution()) throw CloudSignInRequired(SIGN_IN_AGAIN)
        return result.accessToken ?: throw CloudSignInRequired(SIGN_IN_AGAIN)
    }

    override fun invalidate(token: String) {
        runCatching { await { client.clearToken(ClearTokenRequest.builder().setToken(token).build()) } }
    }

    private fun <T> await(task: () -> com.google.android.gms.tasks.Task<T>): T = try {
        Tasks.await(task(), TIMEOUT_S, TimeUnit.SECONDS)
    } catch (e: ExecutionException) {
        val cause = e.cause
        throw if (cause is ApiException) IOException("Google Play services: ${cause.statusCode} — ${cause.message}", cause) else IOException(cause?.message ?: "Google Play services נכשל", cause)
    } catch (e: TimeoutException) {
        throw IOException("Google Play services לא ענה", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("ההתחברות ל-Google נקטעה", e)
    }

    companion object {
        /** כל הקבצים ב-Drive — מנהל קבצים, לא רק מה שהאפליקציה יצרה (`drive.file`). */
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val ACCOUNT_TYPE = "com.google"
        private const val TIMEOUT_S = 30L
        const val SIGN_IN_AGAIN = "ההתחברות ל-Google Drive פגה — ערוך את החיבור והתחבר מחדש"

        /** בלי [account] — Google מציג בחירת חשבון ("הוסף מקום"). */
        fun request(account: Account? = null): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .apply { account?.let { setAccount(it) } }
            .build()
    }
}
