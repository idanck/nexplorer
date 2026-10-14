package com.idanck.nexplorer.ui.player

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.idanck.nexplorer.core.MediaTime
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * נגן השמע. [path] — הקובץ שנפתח (null כשחוזרים מההתראה); משמש רק לשחזור אחרי הריגת תהליך.
 * למעלה מה שמתנגן (עטיפה, כותרת, מיקום, בקרים), למטה רשימת ההשמעה. לרוחב — זה לצד זה.
 */
@Composable
fun AudioPlayerScreen(viewModel: AudioPlayerViewModel, path: String?, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(path) { if (path != null) viewModel.restore(path) }
    // המיקום לא שולח אירועים: בזמן ניגון מבקשים אותו.
    LaunchedEffect(state.playing) {
        while (state.playing) {
            viewModel.refreshPosition()
            delay(POSITION_TICK_MS)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.padding(start = NexplorerDimens.Space1, end = NexplorerDimens.Space1, top = NexplorerDimens.Space2, bottom = NexplorerDimens.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Column(Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2)) {
                    Text("נגן שמע", style = MaterialTheme.typography.titleLarge)
                    if (state.tracks.isNotEmpty() && state.index >= 0) {
                        Text(
                            "${state.index + 1} / ${state.tracks.size}",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val current = state.current
                if (current != null) {
                    IconButton(onClick = { FileIntents.openWith(context, current.path, current.name) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "פתיחה באמצעות")
                    }
                }
            }

            when {
                (!state.connected && state.error == null) || (state.loading && state.tracks.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                // לא התחבר לשירות, או אין רשימה (חזרה מההתראה אחרי שהשירות נסגר).
                !state.connected || state.tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(NexplorerDimens.Space5), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "אין מה לנגן", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val artSize = minOf(NexplorerDimens.PlayerArtworkMax, maxHeight * ARTWORK_HEIGHT_SHARE)
                    if (maxWidth > maxHeight) {
                        Row(Modifier.fillMaxSize()) {
                            NowPlaying(state, viewModel, artSize, Modifier.weight(1f))
                            Queue(state, viewModel, Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            NowPlaying(state, viewModel, artSize, Modifier.fillMaxWidth())
                            Queue(state, viewModel, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(state: AudioState, viewModel: AudioPlayerViewModel, artSize: Dp, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        // מסך נמוך (לרוחב, או עם הודעת שגיאה): גוללים, ולא חותכים את הבקרים.
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = NexplorerDimens.Space5, vertical = NexplorerDimens.Space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // עטיפה חדשה: הקודמת יורדת מיד, והחדשה מפוענחת ברקע.
        val artBytes = state.artwork
        var art by remember(artBytes) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(artBytes) {
            art = artBytes?.let { bytes -> withContext(Dispatchers.Default) { decodeArtwork(bytes) } }
        }
        Box(
            Modifier
                .sizeIn(maxWidth = artSize, maxHeight = artSize)
                .aspectRatio(1f)
                .clip(NexplorerShape.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = art
            if (image != null) {
                Image(image, contentDescription = "עטיפה", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(NexplorerDimens.PlayerArtworkIcon),
                )
            }
        }
        Spacer(Modifier.size(NexplorerDimens.Space4))
        val tagTitle = state.title
        Text(
            tagTitle ?: state.current?.name.orEmpty(),
            style = if (tagTitle != null) MaterialTheme.typography.titleLarge else FileNameTextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.artist?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            val current = state.current
            if (current != null) {
                TextButton(onClick = { FileIntents.openWith(context, current.path, current.name) }) { Text("פתיחה באמצעות") }
            }
        }

        // ציר זמן ובקרי ניגון: תמיד משמאל לימין, גם בממשק עברי (כמו בכל נגן).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val duration = state.durationMs
                val fraction = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Slider(
                    value = dragging ?: fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { viewModel.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    enabled = duration > 0,
                    modifier = Modifier.semantics { contentDescription = "מיקום" },
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = dragging?.let { (it * duration).toLong() } ?: state.positionMs
                    Text(MediaTime.format(shown), style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(MediaTime.format(duration), style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val active = MaterialTheme.colorScheme.primary
                    val idle = MaterialTheme.colorScheme.onSurfaceVariant
                    IconToggleButton(
                        checked = state.shuffle,
                        onCheckedChange = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(NexplorerDimens.PlayerControl),
                    ) { Icon(Icons.Filled.Shuffle, contentDescription = "סדר אקראי", tint = if (state.shuffle) active else idle) }
                    IconButton(onClick = viewModel::previous, modifier = Modifier.size(NexplorerDimens.PlayerControl)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "הקודם")
                    }
                    FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(NexplorerDimens.PlayerPlayButton)) {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "השהה" else "נגן",
                            modifier = Modifier.size(NexplorerDimens.PlayerPlayIcon),
                        )
                    }
                    IconButton(onClick = viewModel::next, enabled = state.hasNext, modifier = Modifier.size(NexplorerDimens.PlayerControl)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "הבא")
                    }
                    val repeatLabel = when (state.repeat) {
                        Player.REPEAT_MODE_ALL -> "כל הרשימה"
                        Player.REPEAT_MODE_ONE -> "שיר אחד"
                        else -> "כבוי"
                    }
                    IconButton(
                        onClick = viewModel::cycleRepeat,
                        modifier = Modifier.size(NexplorerDimens.PlayerControl).semantics { stateDescription = repeatLabel },
                    ) {
                        Icon(
                            if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "ניגון חוזר",
                            tint = if (state.repeat == Player.REPEAT_MODE_OFF) idle else active,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Queue(state: AudioState, viewModel: AudioPlayerViewModel, modifier: Modifier) {
    val list = rememberLazyListState()
    // השיר הנוכחי גלוי: גוללים אליו כשהוא מחוץ למסך.
    LaunchedEffect(state.index) {
        val visible = list.layoutInfo.visibleItemsInfo.map { it.index }
        if (state.index >= 0 && state.index !in visible) list.scrollToItem(state.index)
    }
    LazyColumn(modifier.fillMaxWidth(), state = list) {
        itemsIndexed(state.tracks, key = { i, track -> "$i:${track.path}" }) { i, track ->
            val current = i == state.index
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NexplorerDimens.CompactRowHeight)
                    .background(if (current) NexplorerTheme.colors.selectedRow else Color.Transparent)
                    .clickable { viewModel.playAt(i) }
                    .padding(horizontal = NexplorerDimens.Space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (current) Icons.Filled.GraphicEq else Icons.Outlined.MusicNote,
                    contentDescription = if (current) "מתנגן" else null,
                    tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(NexplorerDimens.RowGap))
                Text(
                    track.name,
                    style = FileNameTextStyle,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** עטיפה מהתגיות (לרוב JPEG של 500–1500 פיקסלים): מוקטנת בפענוח עד [ARTWORK_DECODE_MAX_PX] בצלע. */
private fun decodeArtwork(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ARTWORK_DECODE_MAX_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private const val POSITION_TICK_MS = 500L
private const val ARTWORK_DECODE_MAX_PX = 1024

/** העטיפה תופסת עד 40% מגובה האזור (טלפון קטן או לרוחב), ולא יותר מ-PlayerArtworkMax. */
private const val ARTWORK_HEIGHT_SHARE = 0.4f
