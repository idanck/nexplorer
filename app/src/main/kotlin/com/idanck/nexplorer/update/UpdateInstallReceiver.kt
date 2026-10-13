package com.idanck.nexplorer.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.idanck.nexplorer.NexplorerApp

/**
 * לכאן המערכת מדווחת על תוצאת סשן ההתקנה — כולל בקשה להציג את חלון האישור שלה.
 * בהצלחה התהליך מוחלף בגרסה החדשה, ולכן אין מה לעשות.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val updates = (context.applicationContext as NexplorerApp).container.updates
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    updates.onInstallFailed("המערכת לא הציגה את חלון האישור")
                } else {
                    // המשתמש לחץ "עדכן" לפני רגע, האפליקציה בחזית ולכן מותר לפתוח מכאן מסך.
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { updates.onInstallFailed("לא ניתן להציג את חלון האישור") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> updates.onInstallFailed(messageFor(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    private fun messageFor(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "ההתקנה בוטלה"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "אין מספיק מקום להתקנה"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "המערכת חסמה את ההתקנה"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "העדכון חתום במפתח אחר מהגרסה המותקנת"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "העדכון לא מתאים למכשיר הזה"
        else -> "ההתקנה נכשלה" + (detail?.let { " ($it)" } ?: "")
    }

    companion object {
        const val ACTION_STATUS = "com.idanck.nexplorer.UPDATE_STATUS"
    }
}
