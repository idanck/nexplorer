package com.idanck.nexplorer.ui.net

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.idanck.nexplorer.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * החזרה מדף ההתחברות של Microsoft (`<applicationId>://oauth?code=…&state=…`): הכתובת עוברת ל-[OAuthRedirects],
 * והמסך הראשי חוזר לחזית — ה-Custom Tab שמעליו נסגר. בלי ממשק; היא לא נשארת במחסנית.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let(OAuthRedirects::deliver)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

/** התשובה האחרונה מהדפדפן, למסך "הוסף מקום" שמחכה לה. [consume] — אחרי שטופלה. */
object OAuthRedirects {
    private val _latest = MutableStateFlow<Uri?>(null)
    val latest: StateFlow<Uri?> = _latest

    fun deliver(uri: Uri) {
        _latest.value = uri
    }

    fun consume() {
        _latest.value = null
    }
}
