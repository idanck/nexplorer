package com.idanck.nexplorer.ui.viewer

import android.app.Activity
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.sortedFor
import com.idanck.nexplorer.data.files.FileIntents
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.ui.theme.FileNameTextStyle
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ViewerState(
    val folder: String? = null,
    val images: List<Entry> = emptyList(),
    val loading: Boolean = true,
)

/** מציג התמונות: כל התמונות בתיקייה של התמונה שנפתחה, באותו מיון כמו בחלון הקבצים. */
class ViewerViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as NexplorerApp).container.settings
    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state
    private var loadJob: Job? = null

    /** תמיד רושם מחדש — מאז הפעם הקודמת יכלו להתווסף או להימחק תמונות. */
    fun open(path: String) {
        val folder = File(path).parent ?: return
        loadJob?.cancel()
        _state.value = ViewerState(folder = folder)
        loadJob = viewModelScope.launch {
            val view = settings.state.value.viewFor(folder)
            val listed = LocalFiles.list(folder, view.showHidden).getOrNull().orEmpty()
            val images = listed.filter { it.kind == FileKind.Image }.sortedFor(view.sort)
            // תמונה מוסתרת שנפתחה מהחיפוש: בלי "הצג מוסתרים" היא לא ברשימה — מוסיפים אותה.
            val withOpened = if (images.any { it.path == path }) images else listOf(LocalFiles.entryOf(File(path))) + images
            _state.update { if (it.folder == folder) it.copy(images = withOpened, loading = false) else it }
        }
    }
}

/**
 * [path] — התמונה שמוצגת (משתנה בהחלקה; [onPathChange] שומר אותה למקרה של סיבוב מסך או הריגת תהליך).
 * החלקה לתמונה הבאה, צביטה או הקשה כפולה להגדלה, הקשה אחת מסתירה ומציגה את הסרגל.
 */
@Composable
fun ImageViewerScreen(viewModel: ViewerViewModel, path: String, onPathChange: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var chrome by rememberSaveable { mutableStateOf(true) }
    // אחרי הריגת תהליך ה-ViewModel ריק: טוענים מחדש מהנתיב השמור.
    LaunchedEffect(Unit) { if (state.folder == null) viewModel.open(path) }
    BackHandler(onBack = onBack)

    // רקע כהה: אייקוני שורת המצב בהירים, בשתי הערכות.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && before != null) controller.isAppearanceLightStatusBars = before }
    }

    val colors = NexplorerTheme.colors
    Box(Modifier.fillMaxSize().background(colors.viewerBackground)) {
        val images = state.images
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val start = images.indexOfFirst { it.path == path }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start) { images.size }
            val currentPathChange by rememberUpdatedState(onPathChange)
            LaunchedEffect(pager, images) {
                snapshotFlow { pager.currentPage }.collect { page -> images.getOrNull(page)?.let { currentPathChange(it.path) } }
            }
            HorizontalPager(state = pager, key = { images[it].path }, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(images[page], onTap = { chrome = !chrome })
            }
        }

        val current = images.firstOrNull { it.path == path }
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.viewerChrome)
                    // לרוחב שורת הניווט והמגרעת בצד: הכפתורים לא יושבים מתחתיהן.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(horizontal = NexplorerDimens.Space1, vertical = NexplorerDimens.Space1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = IconButtonDefaults.iconButtonColors(contentColor = colors.onViewer)
                IconButton(onClick = onBack, colors = tint) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Column(Modifier.weight(1f).padding(horizontal = NexplorerDimens.Space2)) {
                    Text(
                        File(path).name,
                        style = FileNameTextStyle,
                        color = colors.onViewer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (images.isNotEmpty() && current != null) {
                        Text(
                            "${images.indexOf(current) + 1} / ${images.size}",
                            style = MonoTextStyle,
                            color = colors.onViewer,
                        )
                    }
                }
                IconButton(
                    onClick = { FileIntents.share(context, listOf(path to File(path).name)) },
                    colors = tint,
                ) { Icon(Icons.Outlined.Share, contentDescription = "שתף") }
                IconButton(
                    onClick = { FileIntents.openWith(context, path, File(path).name) },
                    colors = tint,
                ) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "פתיחה באמצעות") }
            }
        }
    }
}

/**
 * תמונה אחת: מותאמת למסך; צביטה (עד ×[MAX_ZOOM]) והקשה כפולה (×[DOUBLE_TAP_ZOOM]) מגדילות.
 * בהגדלה גרירה מזיזה את התמונה; בלי הגדלה היא עוברת ל-Pager — החלקה לתמונה הבאה.
 */
@Composable
private fun ZoomableImage(entry: Entry, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = boxSize.width * (zoom - 1) / 2
        val maxY = boxSize.height * (zoom - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    val currentTap by rememberUpdatedState(onTap)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = DOUBLE_TAP_ZOOM
                            offset = clamp((center - tap) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            val zoom = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            scale = zoom
                            offset = if (zoom <= 1f) Offset.Zero else clamp(offset + event.calculatePan(), zoom)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(File(entry.path)).size(DECODE_MAX_PX).build(),
            contentDescription = entry.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** פענוח עד 2560 פיקסלים בצלע הארוכה: חד בהגדלה סבירה, ובלי 100 MB לתמונה של 50 מגה-פיקסל. */
private const val DECODE_MAX_PX = 2560
