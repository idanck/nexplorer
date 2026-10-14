package com.idanck.nexplorer.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.MediaQueue
import com.idanck.nexplorer.core.sortedFor
import com.idanck.nexplorer.data.files.LocalFiles
import com.idanck.nexplorer.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** רשימת השמעה מהתיקייה של הקובץ, לפי התצוגה שלה (מיון, מוסתרים) — כמו שהיא נראית בחלון הקבצים. */
internal suspend fun folderQueue(settings: SettingsRepository, path: String): MediaQueue = withContext(Dispatchers.IO) {
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
    .setUri(Uri.fromFile(File(entry.path)))
    .setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(entry.name).build())
    .build()

/** שם הקובץ של פריט: מהכותרת לתצוגה, או מהנתיב. */
internal val MediaItem.fileName: String
    get() = mediaMetadata.displayTitle?.toString() ?: File(mediaId).name

internal fun playbackMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "הקובץ לא נמצא"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "אין הרשאה לקרוא את הקובץ"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "הקובץ פגום"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "הפורמט לא נתמך בנגן המובנה"
    else -> "לא ניתן לנגן את הקובץ"
}
