package com.idanck.nexplorer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.idanck.nexplorer.core.MediaTime
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerColors
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import kotlinx.coroutines.delay

/** כמה זמן הבקרים נשארים גלויים בזמן ניגון בלי מגע. */
private const val CHROME_TIMEOUT_MS = 3_500L

/** כמה זמן משוב ההקשה הכפולה ("+10 שנ׳") נשאר על המסך אחרי ההקשה האחרונה. */
private const val SKIP_FEEDBACK_MS = 800L

/**
 * נגן הווידאו (לוחות "נגן וידאו", 1.9): מסך מלא, הבקרים שלנו (לא של Media3) — שקופים לגמרי, בלי פסים;
 * צל מתחת לטקסט ולאייקונים שומר על קריאות גם על סרטון בהיר.
 *  - הקשה: מציגה ומסתירה את הבקרים (נעלמים לבד אחרי כמה שניות של ניגון). כשהם מוסתרים גם שורות המערכת
 *    נעלמות, ונשאר רק קו התקדמות דק בקצה התחתון.
 *  - במרכז, כשהבקרים גלויים — רק אחורה 10 וקדימה 10.
 *  - הקשה כפולה בכל מקום במסך: קדימה 10 שניות (הקשות נוספות מצטברות: +20, +30).
 *  - למעלה: חזרה, שם הקובץ והמקום ברשימה; מהירות, כתוביות (כשיש), נעילה, פתיחה באמצעות.
 *  - למטה: זמן, פס התקדמות דק, אורך; נגן/השהה, הקודם, הבא; התאמה למסך, סיבוב.
 * הבקרים והזמן משמאל לימין, כמו בכל נגן — גם בממשק בעברית. [path] — הסרטון שמוצג (משתנה בבא/הקודם;
 * [onPathChange] שומר אותו). יציאה מהאפליקציה עוצרת את הניגון; סיבוב מסך לא.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(viewModel: VideoPlayerViewModel, path: String, onPathChange: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // אחרי הריגת תהליך: ממשיכים מאותה נקודה.
    var resumeAt by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { viewModel.open(path, resumeAt) }
    val currentPathChange by rememberUpdatedState(onPathChange)
    LaunchedEffect(state.path) { state.path?.let { currentPathChange(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumeAt = viewModel.positionMs }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.pause()
    }
    val leave = {
        viewModel.close()
        onBack()
    }
    BackHandler(onBack = leave)

    var chrome by remember { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableStateOf(false) }
    // כל מגע בבקרים מאפס את הספירה להסתרה; גרירה בפס מחזיקה אותם גלויים.
    var poke by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(chrome, state.playing, poke, scrubbing) {
        if (chrome && state.playing && !scrubbing) {
            delay(CHROME_TIMEOUT_MS)
            chrome = false
        }
    }

    // המיקום מ-ExoPlayer (אין לו Flow): דגימה כל רבע שנייה.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(viewModel) {
        while (true) {
            position = viewModel.positionMs
            duration = viewModel.durationMs
            delay(250)
        }
    }

    // הקשה כפולה: כמה שניות קדימה הצטברו, ומתי ההקשה האחרונה (המשוב נעלם אחריה).
    var skipped by remember { mutableIntStateOf(0) }
    var skipTap by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipTap) {
        if (skipTap > 0) {
            delay(SKIP_FEEDBACK_MS)
            skipped = 0
        }
    }

    val view = LocalView.current
    val activity = view.context as? Activity
    val window = activity?.window
    val bars = remember(window) { window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(bars) {
        val lightBefore = bars?.isAppearanceLightStatusBars
        val behaviorBefore = bars?.systemBarsBehavior
        bars?.isAppearanceLightStatusBars = false
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            bars?.let {
                it.show(WindowInsetsCompat.Type.systemBars())
                if (lightBefore != null) it.isAppearanceLightStatusBars = lightBefore
                if (behaviorBefore != null) it.systemBarsBehavior = behaviorBefore
            }
        }
    }
    LaunchedEffect(chrome, bars) {
        if (chrome) bars?.show(WindowInsetsCompat.Type.systemBars()) else bars?.hide(WindowInsetsCompat.Type.systemBars())
    }
    // "סיבוב": לרוחב (לפי החיישן); לחיצה נוספת משחררת חזרה לכיוון של המכשיר. ביציאה מהנגן — חוזרים למה שהיה.
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation
        onDispose { if (before != null) activity?.requestedOrientation = before }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val colors = NexplorerTheme.colors
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val name = state.name ?: path.substringAfterLast('/')

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.viewerBackground)
            .semantics { stateDescription = if (state.playing) "מנגן" else "מושהה" },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // הבקרים בצד של Compose; כאן רק התמונה והכתוביות.
                    useController = false
                    player = viewModel.player
                    // אחרי ש-ExoPlayer קיבל את המשטח (הוא נרשם ראשון, ב-player =): פריים מושהה מצויר עליו מחדש.
                    (videoSurfaceView as? SurfaceView)?.holder?.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = viewModel.redrawPausedFrame()
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                        },
                    )
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = state.playing
                playerView.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // שכבת המגע: הקשה — בקרים; הקשה כפולה — קדימה 10 (לא כשנעול).
        Box(
            Modifier
                .fillMaxSize()
                .testTag("videoSurface")
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = {
                            chrome = !chrome
                            poke++
                        },
                        onDoubleTap = {
                            if (!locked) {
                                viewModel.seekBy(VIDEO_SKIP_MS)
                                skipped += (VIDEO_SKIP_MS / 1000).toInt()
                                skipTap++
                            }
                        },
                    )
                },
        )

        state.error?.let { error ->
            Column(Modifier.align(Alignment.Center).padding(NexplorerDimens.Space5), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, style = MaterialTheme.typography.bodyLarge, color = colors.onViewer, textAlign = TextAlign.Center)
                TextButton(onClick = { FileIntents.openWith(context, state.path ?: path, name) }) {
                    Text("פתיחה באמצעות", color = colors.onViewer)
                }
            }
        }

        // בקרים מוסתרים: קו התקדמות דק בקצה התחתון.
        if (!chrome && duration > 0) {
            EdgeProgress(
                fraction = position.toFloat() / duration,
                trackColor = colors.playerTrack,
                progressColor = colors.playerProgress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SkipFeedback(skipped, colors, Modifier.align(Alignment.Center))

        AnimatedVisibility(visible = chrome && locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top)).padding(top = NexplorerDimens.Space3)) {
                GlyphButton(Icons.Outlined.LockOpen, "בטל נעילה", colors, outlined = true) {
                    locked = false
                    poke++
                }
            }
        }

        AnimatedVisibility(visible = chrome && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // למעלה (לפי כיוון הממשק): חזרה ושם בצד ההתחלה, הכפתורים בצד הסוף.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = NexplorerDimens.Space2, vertical = NexplorerDimens.Space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphButton(Icons.AutoMirrored.Filled.ArrowBack, "חזרה", colors, onClick = leave)
                    Column(Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2)) {
                        Text(
                            name,
                            style = FileNameTextStyle.copy(fontWeight = FontWeight.SemiBold).withShadow(colors),
                            color = colors.onViewer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.count > 1) {
                            Text("${state.index + 1} / ${state.count}", style = MonoTextStyle.withShadow(colors), color = colors.onViewer)
                        }
                    }
                    SpeedButton(state.speed, colors) {
                        viewModel.cycleSpeed()
                        poke++
                    }
                    if (state.subtitlesAvailable) {
                        GlyphButton(
                            if (state.subtitlesOn) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                            if (state.subtitlesOn) "הסתר כתוביות" else "הצג כתוביות",
                            colors,
                        ) {
                            viewModel.toggleSubtitles()
                            poke++
                        }
                    }
                    GlyphButton(Icons.Outlined.Lock, "נעילת מסך", colors) {
                        locked = true
                        poke++
                    }
                    GlyphButton(Icons.AutoMirrored.Outlined.OpenInNew, "פתיחה באמצעות", colors) {
                        FileIntents.openWith(context, state.path ?: path, name)
                    }
                }

                // במרכז: רק אחורה 10 וקדימה 10 — לפי כיוון הזמן (אחורה משמאל).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.VideoSkipGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphButton(Icons.Filled.Replay10, "אחורה 10 שניות", colors, size = NexplorerDimens.VideoSkipButton, iconSize = NexplorerDimens.VideoSkipIcon) {
                            viewModel.seekBy(-VIDEO_SKIP_MS)
                            poke++
                        }
                        GlyphButton(Icons.Filled.Forward10, "קדימה 10 שניות", colors, size = NexplorerDimens.VideoSkipButton, iconSize = NexplorerDimens.VideoSkipIcon) {
                            viewModel.seekBy(VIDEO_SKIP_MS)
                            poke++
                        }
                    }
                }

                // למטה: זמן · פס · אורך, ומתחתם הכפתורים. משמאל לימין.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(start = NexplorerDimens.Space4, end = NexplorerDimens.Space4, bottom = NexplorerDimens.Space1),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(MediaTime.format(position), style = MonoTextStyle.withShadow(colors), color = colors.onViewer)
                            ThinSeekBar(
                                positionMs = position,
                                durationMs = duration,
                                onSeek = { target ->
                                    viewModel.seekTo(target)
                                    position = target
                                    poke++
                                },
                                trackColor = colors.playerTrack,
                                progressColor = colors.playerProgress,
                                thumbColor = colors.onViewer,
                                onScrub = { scrubbing = it },
                                modifier = Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space3),
                            )
                            Text(MediaTime.format(duration), style = MonoTextStyle.withShadow(colors), color = colors.onViewer)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphButton(
                                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.playing) "השהה" else "נגן",
                                colors,
                                iconSize = NexplorerDimens.Space6 - NexplorerDimens.Space1,
                            ) {
                                viewModel.togglePlay()
                                poke++
                            }
                            if (state.count > 1) {
                                GlyphButton(Icons.Filled.SkipPrevious, "הקודם", colors, enabled = state.hasPrevious) {
                                    viewModel.previous()
                                    poke++
                                }
                                GlyphButton(Icons.Filled.SkipNext, "הבא", colors, enabled = state.hasNext) {
                                    viewModel.next()
                                    poke++
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            GlyphButton(
                                if (zoom) Icons.Outlined.FitScreen else Icons.Outlined.Fullscreen,
                                if (zoom) "התאמה למסך" else "מילוי המסך",
                                colors,
                            ) {
                                zoom = !zoom
                                poke++
                            }
                            GlyphButton(Icons.Outlined.ScreenRotation, "סיבוב מסך", colors) {
                                activity?.requestedOrientation =
                                    if (landscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                poke++
                            }
                        }
                    }
                }
            }
        }
    }
}

/** צל רך מתחת לטקסט — אין פס מאחוריו. */
private fun TextStyle.withShadow(colors: NexplorerColors): TextStyle =
    copy(shadow = Shadow(colors.playerGlyphShadow, Offset(0f, 2f), blurRadius = 6f))

/**
 * כפתור אייקון בנגן: לבן, עם עותק כהה מוזח מתחתיו במקום פס רקע. [outlined] — עם עיגול מתאר (כפתור
 * "בטל נעילה", שעומד לבד על המסך).
 */
@Composable
private fun GlyphButton(
    icon: ImageVector,
    description: String,
    colors: NexplorerColors,
    size: Dp = NexplorerDimens.BottomBarButton,
    iconSize: Dp = NexplorerDimens.Space5,
    enabled: Boolean = true,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (outlined) Modifier.border(NexplorerDimens.Space1 / 4, colors.onViewer, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.playerGlyphShadow, modifier = Modifier.size(iconSize).offset(y = 1.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.onViewer else colors.onViewer.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** כפתור המהירות: "1.25×" בגלולה עם מתאר; הקשה עוברת למהירות הבאה. */
@Composable
private fun SpeedButton(speed: Float, colors: NexplorerColors, onClick: () -> Unit) {
    val label = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"
    Box(
        Modifier
            .padding(horizontal = NexplorerDimens.Space1)
            .height(NexplorerDimens.TouchTarget)
            .clip(NexplorerShape.Pill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "מהירות ניגון"
                stateDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MonoTextStyle.withShadow(colors),
            color = colors.onViewer,
            modifier = Modifier
                .border(NexplorerDimens.Space1 / 4 + NexplorerDimens.Space1 / 8, colors.onViewer, NexplorerShape.Pill)
                .padding(horizontal = NexplorerDimens.Space2 + NexplorerDimens.Space1 / 2, vertical = NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2),
        )
    }
}

/** משוב ההקשה הכפולה: שני גלים, חצים ו"+10 שנ׳" (מצטבר). */
@Composable
private fun SkipFeedback(seconds: Int, colors: NexplorerColors, modifier: Modifier) {
    AnimatedVisibility(visible = seconds > 0, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(NexplorerDimens.VideoRippleOuter).background(colors.playerRipple, CircleShape))
            Box(Modifier.size(NexplorerDimens.VideoRippleInner).background(colors.playerRipple, CircleShape))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Box {
                    Icon(Icons.Filled.FastForward, contentDescription = null, tint = colors.playerGlyphShadow, modifier = Modifier.size(NexplorerDimens.Space6).offset(y = 1.dp))
                    Icon(Icons.Filled.FastForward, contentDescription = null, tint = colors.onViewer, modifier = Modifier.size(NexplorerDimens.Space6))
                }
                Text(
                    "${"+$seconds".isolated()} שנ׳",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).withShadow(colors),
                    color = colors.onViewer,
                )
            }
        }
    }
}
