package com.idanck.nexplorer.ui.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.media.PlaybackService
import com.idanck.nexplorer.media.fileName
import com.idanck.nexplorer.media.folderQueue
import com.idanck.nexplorer.media.mediaItemFor
import com.idanck.nexplorer.media.playbackMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Track(val path: String, val name: String)

data class AudioState(
    /** עד שה-MediaController מתחבר לשירות — אין מה להציג. */
    val connected: Boolean = false,
    /** טוען את התיקייה לרשימת ההשמעה. */
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val index: Int = -1,
    /** "מנגן" מבחינת המשתמש: גם בזמן טעינה. */
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** מהתגיות; בלי תגית — null, והמסך מציג את שם הקובץ. */
    val title: String? = null,
    val artist: String? = null,
    val artwork: ByteArray? = null,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    /** יש הבא — לפי הסדר האקראי וההחזרה, לא רק המיקום ברשימה. */
    val hasNext: Boolean = false,
    val error: String? = null,
) {
    val current: Track? get() = tracks.getOrNull(index)
}

/**
 * הממשק של נגן השמע. הניגון עצמו ב-[PlaybackService] (ממשיך כשיוצאים מהמסך או מהאפליקציה);
 * כאן MediaController שמראה את מצבו ושולח פקודות. הרשימה נקראת מהשירות, ולכן גם אחרי שהמסך נסגר
 * וחזרו אליו מההתראה היא אותה רשימה.
 */
class AudioPlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as NexplorerApp).container.settings
    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state

    private val future: ListenableFuture<MediaController> =
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null

    /** פעולות שנשלחו לפני שהחיבור הסתיים. */
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var loadJob: Job? = null
    private var tracks: List<Track> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) tracks = readTracks(player)
            publish()
        }
    }

    init {
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull()
            if (connected == null) {
                _state.update { it.copy(error = "הנגן לא נפתח") }
                return@addListener
            }
            controller = connected
            connected.addListener(listener)
            tracks = readTracks(connected)
            pending.forEach { it(connected) }
            pending.clear()
            publish()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    /**
     * מנגן את [path]. אם הוא כבר הנוכחי — לא מתחיל מחדש (פתיחה חוזרת מהרשימה); אם הוא ברשימה —
     * קופץ אליו; אחרת הרשימה מתחלפת בקבצי השמע של התיקייה שלו.
     */
    fun open(path: String) {
        withController { c ->
            val index = tracks.indexOfFirst { it.path == path }
            if (index < 0) {
                load(c, path)
            } else if (index != c.currentMediaItemIndex) {
                c.seekTo(index, 0)
                start(c)
            } else if (c.playbackState == Player.STATE_ENDED || c.playbackState == Player.STATE_IDLE) {
                start(c)
            }
        }
    }

    /** אחרי הריגת תהליך: השירות ריק — טוענים מחדש את מה שהיה פתוח. כשיש רשימה לא נוגעים בה. */
    fun restore(path: String) {
        withController { c -> if (c.mediaItemCount == 0 && loadJob?.isActive != true) load(c, path) }
    }

    private fun load(c: MediaController, path: String) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            val queue = folderQueue(settings, path)
            c.setMediaItems(queue.items.map(::mediaItemFor), queue.start, 0)
            start(c)
            _state.update { it.copy(loading = false) }
        }
    }

    /** נגן: אחרי שגיאה (IDLE) מכינים מחדש, ובסוף הרשימה (ENDED) מתחילים את הנוכחי מההתחלה. */
    private fun start(c: Player) {
        when (c.playbackState) {
            Player.STATE_IDLE -> c.prepare()
            Player.STATE_ENDED -> c.seekToDefaultPosition()
        }
        c.play()
    }

    fun toggle() {
        withController { c -> if (playing(c)) c.pause() else start(c) }
    }

    fun next() {
        withController { c ->
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
            }
        }
    }

    /** בתחילת שיר — לקודם; באמצע — לתחילת השיר (כמו בכל נגן). */
    fun previous() {
        withController { c ->
            c.seekToPrevious()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
        }
    }

    fun seekTo(positionMs: Long) {
        withController { c ->
            c.seekTo(positionMs)
            publish()
        }
    }

    fun playAt(index: Int) {
        withController { c ->
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0)
                start(c)
            }
        }
    }

    /** כבוי → כל הרשימה → שיר אחד → כבוי. */
    fun cycleRepeat() {
        withController { c ->
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleShuffle() {
        withController { c -> c.shuffleModeEnabled = !c.shuffleModeEnabled }
    }

    /** המיקום לא שולח אירועים — המסך מבקש אותו כל חצי שנייה בזמן ניגון. */
    fun refreshPosition() {
        val c = controller ?: return
        _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
    }

    /** מבחינת המשתמש: ביקש לנגן, והנגן לא נעצר בסוף או בשגיאה (בטעינה — כן מנגן). */
    private fun playing(c: Player): Boolean =
        c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE

    private fun readTracks(player: Player): List<Track> = (0 until player.mediaItemCount).map { i ->
        val item: MediaItem = player.getMediaItemAt(i)
        Track(path = item.mediaId, name = item.fileName)
    }

    private fun publish() {
        val c = controller ?: return
        val metadata = c.mediaMetadata
        val duration = c.duration
        _state.update {
            it.copy(
                connected = true,
                tracks = tracks,
                index = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex,
                playing = playing(c),
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (duration == C.TIME_UNSET) 0 else duration,
                title = metadata.title?.toString(),
                artist = (metadata.artist ?: metadata.albumArtist)?.toString(),
                artwork = metadata.artworkData,
                repeat = c.repeatMode,
                shuffle = c.shuffleModeEnabled,
                hasNext = c.hasNextMediaItem(),
                error = c.playerError?.let(::playbackMessage),
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        controller = null
    }
}
