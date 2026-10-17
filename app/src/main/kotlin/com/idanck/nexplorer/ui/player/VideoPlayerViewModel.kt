package com.idanck.nexplorer.ui.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.media.fileName
import com.idanck.nexplorer.media.folderQueue
import com.idanck.nexplorer.media.mediaItemFor
import com.idanck.nexplorer.media.mediaSources
import com.idanck.nexplorer.media.playbackMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VideoState(
    val path: String? = null,
    val name: String? = null,
    val index: Int = 0,
    val count: Int = 0,
    val playing: Boolean = false,
    val error: String? = null,
)

/**
 * נגן הווידאו. ה-Player חי ב-ViewModel: סיבוב מסך בונה מחדש את המסך, והניגון ממשיך מאותה נקודה.
 * רשימת ההשמעה — הסרטונים בתיקייה; בסוף סרטון עוצרים (לא עוברים לבא בלי שביקשו).
 */
@OptIn(UnstableApi::class)
class VideoPlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as NexplorerApp).container
    private val settings = container.settings

    // ברשת — ישירות מהשרת, בלי עותק במכשיר.
    val player: ExoPlayer = ExoPlayer.Builder(app, mediaSources(app, container.remote))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            // וידאו לוקח את השמע: נגן המוזיקה עוצר.
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { pauseAtEndOfMediaItems = true }

    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state
    private var loadJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()
        })
    }

    /**
     * פותח את [path] מ-[positionMs]. כשהוא כבר הנוכחי (סיבוב מסך, חזרה למסך) — ממשיכים כמו שהוא.
     */
    fun open(path: String, positionMs: Long = 0) {
        if (player.currentMediaItem?.mediaId == path || (loadJob?.isActive == true && _state.value.path == path)) return
        loadJob?.cancel()
        _state.value = VideoState(path = path, name = path.substringAfterLast('/'))
        loadJob = viewModelScope.launch {
            val queue = folderQueue(settings, container.remote, path)
            player.setMediaItems(queue.items.map(::mediaItemFor), queue.start, positionMs)
            player.prepare()
            player.play()
        }
    }

    /** יציאה מהמסך: עוצרים ומשחררים את המפענח; כניסה הבאה טוענת מחדש. */
    fun close() {
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = VideoState()
    }

    fun pause() = player.pause()

    /**
     * משטח ציור חדש (סיבוב מסך, חזרה מהרקע) ריק עד הפריים הבא — ובנגן מושהה אין פריים הבא, והמסך
     * נשאר שחור. חיפוש לאותה נקודה מפענח ומצייר אותה מחדש, בלי להתחיל לנגן.
     */
    fun redrawPausedFrame() {
        if (!player.playWhenReady && player.playbackState == Player.STATE_READY) player.seekTo(player.currentPosition)
    }

    /** לשמירה למקרה של הריגת תהליך. */
    val positionMs: Long get() = player.currentPosition

    private fun publish() {
        val item = player.currentMediaItem ?: return
        _state.value = VideoState(
            path = item.mediaId,
            name = item.fileName,
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            playing = player.isPlaying,
            error = player.playerError?.let(::playbackMessage),
        )
    }

    override fun onCleared() {
        player.release()
    }
}
