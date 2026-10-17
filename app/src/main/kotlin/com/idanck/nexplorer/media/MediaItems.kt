package com.idanck.nexplorer.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import com.idanck.nexplorer.core.MediaQueue
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.sortedFor
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * רשימת השמעה מהתיקייה של הקובץ, לפי התצוגה שלה (מיון, מוסתרים) — כמו שהיא נראית בחלון הקבצים.
 * ברשת: הרשימה היא בקשה לשרת; כשהיא נכשלת — רק הקובץ שנפתח (והנגן יראה אם השרת עונה).
 */
internal suspend fun folderQueue(settings: SettingsRepository, remote: RemoteFiles, path: String): MediaQueue = withContext(Dispatchers.IO) {
    if (NetPaths.isRemote(path)) {
        val folder = NetPaths.parent(path)
        val view = folder?.let { settings.state.value.viewFor(it) }
        val listed = folder?.let { remote.list(it).getOrNull() }.orEmpty().filter { view?.showHidden == true || !it.isHidden }
        val name = NetPaths.name(path)
        val opened = listed.firstOrNull { it.path == path } ?: Entry(path, name, false, 0, -1, 0, false, FileKinds.of(name, false))
        return@withContext if (view == null) MediaQueue(listOf(opened), 0) else MediaQueue.of(listed.sortedFor(view.sort), opened)
    }
    val file = File(path)
    val opened = LocalFiles.entryOf(file, countChildren = false)
    val folder = file.parent ?: return@withContext MediaQueue(listOf(opened), 0)
    val view = settings.state.value.viewFor(folder)
    val listed = LocalFiles.list(folder, view.showHidden).getOrNull().orEmpty()
    MediaQueue.of(listed.sortedFor(view.sort), opened)
}

/** המזהה הוא הנתיב (ממנו השירות בונה את ה-URI מחדש); שם הקובץ — כותרת לתצוגה כשאין תגית. */
internal fun mediaItemFor(entry: Entry): MediaItem = MediaItem.Builder()
    .setMediaId(entry.path)
    .setUri(mediaUri(entry.path))
    .setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(entry.name).build())
    .build()

/** קובץ במכשיר — `file://`; ברשת — נקרא ישירות מהשרת ([RemoteDataSource]). */
internal fun mediaUri(path: String): Uri = if (NetPaths.isRemote(path)) RemoteDataSource.uriOf(path) else Uri.fromFile(File(path))

/** המקורות של הנגנים: מה ש-Media3 מכיר (קבצים במכשיר), ומה שלא — ברשת. */
@OptIn(UnstableApi::class)
internal fun mediaSources(context: Context, remote: RemoteFiles): MediaSource.Factory =
    DefaultMediaSourceFactory(DefaultDataSource.Factory(context, RemoteDataSource.Factory(remote)))

/** שם הקובץ של פריט: מהכותרת לתצוגה, או מהנתיב. */
internal val MediaItem.fileName: String
    get() = mediaMetadata.displayTitle?.toString() ?: File(mediaId).name

internal fun playbackMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "הקובץ לא נמצא"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "אין הרשאה לקרוא את הקובץ"
    // ההודעה של השרת, כבר בעברית ("השרת לא עונה", "שם המשתמש או הסיסמה שגויים").
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> error.cause?.message ?: "החיבור לשרת נכשל"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "הקובץ פגום"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "הפורמט לא נתמך בנגן המובנה"
    else -> "לא ניתן לנגן את הקובץ"
}
