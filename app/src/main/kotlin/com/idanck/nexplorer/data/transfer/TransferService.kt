package com.idanck.nexplorer.data.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.R
import com.idanck.nexplorer.core.SizeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * שירות חזית (dataSync) שמחזיק את התהליך בחיים בזמן העברה, עם התראת התקדמות.
 * מתחיל כשנכנסת פעולה לתור ועוצר את עצמו כשאין יותר פעולות פעילות.
 */
@OptIn(FlowPreview::class)
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as NexplorerApp).container.transfers
        ensureChannel()
        promote(buildNotification(manager.transfers.value))
        if (watcher == null) {
            watcher = scope.launch {
                manager.transfers.sample(500).collectLatest { list ->
                    if (list.none { it.isActive }) {
                        ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notify(buildNotification(list))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promote(notification: android.app.Notification) {
        // ServiceCompat מתעלם מהסוג מתחת ל-Android 10.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notify(notification: android.app.Notification) {
        // בלי הרשאת התראות (Android 13+) ההתראה פשוט לא מוצגת; השירות ממשיך לרוץ.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // ההרשאה נלקחה בין הבדיקה לקריאה.
        }
    }

    private fun buildNotification(list: List<Transfer>): android.app.Notification {
        val active = list.filter { it.isActive }
        val running = active.firstOrNull { it.status == TransferStatus.Running || it.status == TransferStatus.Paused }
        val title = when {
            active.isEmpty() -> "העברות"
            active.size == 1 -> "${active.first().kind.verb}: ${active.first().title}"
            else -> "${active.size} העברות"
        }
        val text = running?.let {
            if (it.totalBytes > 0) "${SizeFormat.short(it.doneBytes)} / ${SizeFormat.short(it.totalBytes)}"
            else it.currentName.orEmpty()
        } ?: "בתור"
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setProgress(100, ((running?.fraction ?: 0f) * 100).toInt(), running == null)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "העברות קבצים", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "transfers"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
            }
        }
    }
}
