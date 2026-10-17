package com.idanck.nexplorer.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.NetPaths

/**
 * ניגון שמע ברקע: ExoPlayer בתוך MediaSession — התראה עם בקרים, מסך הנעילה, מקשי אוזניות ו-Bluetooth.
 * הממשק (`AudioPlayerViewModel`) שולט בו דרך MediaController. השירות לא מיוצא: רק האפליקציה מתחברת
 * אליו; ההתראה והמקשים עוברים דרך ה-MediaSession של המערכת.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // ברשת — ישירות מהשרת, בלי עותק במכשיר.
        val player = ExoPlayer.Builder(this, mediaSources(this, (application as NexplorerApp).container.remote))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                // שיחה, ניווט או וידאו — השמע עוצר וחוזר בעצמו.
                true,
            )
            // אוזניות שנותקו: עוצרים, לא ממשיכים ברמקול.
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            // שיר מהרשת כשהמסך כבוי: בלי WifiLock ה-Wi-Fi נרדם והניגון נקטע. נשמר רק בזמן ניגון.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val remote = mediaItem?.mediaId?.let(NetPaths::isRemote) == true
                player.setWakeMode(if (remote) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
            }
        })
        val showPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ACTION_SHOW_PLAYER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(TitledNotifications(this))
        session = MediaSession.Builder(this, player)
            .setSessionActivity(showPlayer)
            .setCallback(OwnItems(packageName))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // הסרה מרשימת האחרונות: ברירת המחדל של MediaSessionService — ממשיכים רק כשמנגנים (השירות בחזית).

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * פריטים שנשלחים מ-MediaController לא תמיד מגיעים עם ה-URI (Media3 מוריד אותו בין תהליכים);
     * מזהה הפריט הוא הנתיב (במכשיר או ברשת), וממנו נבנה ה-URI מחדש. רק האפליקציה עצמה מוסיפה פריטים —
     * שלט חיצוני (Bluetooth, רכב) מנגן, עוצר ומדלג, אבל לא מבקש לנגן נתיב משלו.
     */
    private class OwnItems(private val ownPackage: String) : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            if (controller.packageName != ownPackage) {
                Futures.immediateFailedFuture(UnsupportedOperationException())
            } else {
                Futures.immediateFuture(mediaItems.map { it.buildUpon().setUri(mediaUri(it.mediaId)).build() }.toMutableList())
            }
    }

    /** קובץ בלי תגית כותרת (WAV, הקלטה): בהתראה שם הקובץ, ולא שורה ריקה. */
    @OptIn(UnstableApi::class)
    private class TitledNotifications(context: Context) : DefaultMediaNotificationProvider(context) {
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? = metadata.title ?: metadata.displayTitle
    }

    companion object {
        /** הקשה על ההתראה: חזרה לנגן. */
        const val ACTION_SHOW_PLAYER = "com.idanck.nexplorer.action.SHOW_PLAYER"
    }
}
