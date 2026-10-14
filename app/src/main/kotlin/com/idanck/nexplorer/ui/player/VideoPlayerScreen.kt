package com.idanck.nexplorer.ui.player

import android.app.Activity
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/**
 * נגן הווידאו: מסך מלא. הקשה מציגה ומסתירה את הבקרים (של Media3) ואת הסרגל העליון; כשהם מוסתרים
 * גם שורות המערכת נעלמות. [path] — הסרטון שמוצג (משתנה בבא/הקודם; [onPathChange] שומר אותו).
 * יציאה מהאפליקציה עוצרת את הניגון; סיבוב מסך לא.
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
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
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

    // הבקרים של Media3 לא מקבלים את ה-insets דרך Compose: מרפדים אותם ידנית מעל שורת הניווט והמגרעת.
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val padLeft = insets.getLeft(density, LayoutDirection.Ltr)
    val padRight = insets.getRight(density, LayoutDirection.Ltr)
    val padBottom = insets.getBottom(density)

    val colors = NexplorerTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.viewerBackground)
            .semantics { stateDescription = if (state.playing) "מנגן" else "מושהה" },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // ציר הזמן והבקרים משמאל לימין, כמו בכל נגן — גם כשהמכשיר בעברית.
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> chrome = visibility == View.VISIBLE })
                    player = viewModel.player
                    // הסרגל העליון מתחיל גלוי (גם אחרי סיבוב) — הבקרים איתו, ונעלמים יחד אחרי כמה שניות.
                    showController()
                }
            },
            update = { playerView ->
                playerView.keepScreenOn = state.playing
                playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.setPadding(padLeft, 0, padRight, padBottom)
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        state.error?.let { error ->
            Column(Modifier.align(Alignment.Center).padding(NexplorerDimens.Space5), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, style = MaterialTheme.typography.bodyLarge, color = colors.onViewer, textAlign = TextAlign.Center)
                TextButton(onClick = { FileIntents.openWith(context, state.path ?: path, state.name ?: path.substringAfterLast('/')) }) {
                    Text("פתיחה באמצעות", color = colors.onViewer)
                }
            }
        }

        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.viewerChrome)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = NexplorerDimens.Space1, vertical = NexplorerDimens.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = IconButtonDefaults.iconButtonColors(contentColor = colors.onViewer)
                IconButton(onClick = leave, colors = tint) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                val name = state.name ?: path.substringAfterLast('/')
                Column(Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2)) {
                    Text(name, style = FileNameTextStyle, color = colors.onViewer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.count > 1) Text("${state.index + 1} / ${state.count}", style = MonoTextStyle, color = colors.onViewer)
                }
                IconButton(onClick = { FileIntents.openWith(context, state.path ?: path, name) }, colors = tint) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "פתיחה באמצעות")
                }
            }
        }
    }
}
