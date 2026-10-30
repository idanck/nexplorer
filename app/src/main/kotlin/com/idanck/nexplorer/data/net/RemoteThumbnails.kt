package com.idanck.nexplorer.data.net

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.Buffer
import java.io.IOException

/**
 * תמונה ממוזערת של קובץ ברשת שהשרת מכין בעצמו (Synology, שלב 7) — מה ש-Coil טוען במקום הקובץ. [modified] — חלק
 * מהמפתח: קובץ שהשתנה מקבל תמונה חדשה, ולא את זו שבמטמון.
 */
data class RemoteThumb(val path: String, val large: Boolean, val modified: Long)

/** המפתח במטמון שבזיכרון. */
class RemoteThumbKeyer : Keyer<RemoteThumb> {
    override fun key(data: RemoteThumb, options: Options): String = keyOf(data)
}

/** מהמטמון בדיסק של Coil, ואם אין — מהשרת ([RemoteFiles.thumbnail]), ונשמר בו. */
class RemoteThumbFetcher(
    private val data: RemoteThumb,
    private val remote: RemoteFiles,
    private val options: Options,
    private val loader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = keyOf(data)
        val cache = loader.diskCache
        cache?.openSnapshot(key)?.let { snapshot ->
            return SourceResult(ImageSource(snapshot.data, cache.fileSystem, key, snapshot), null, DataSource.DISK)
        }
        val bytes = remote.thumbnail(data.path, data.large) ?: throw IOException("אין תמונה ממוזערת")
        cache?.openEditor(key)?.let { editor ->
            try {
                cache.fileSystem.write(editor.data) { write(bytes) }
                editor.commitAndOpenSnapshot()?.close()
            } catch (e: Exception) {
                runCatching { editor.abort() }
            }
        }
        return SourceResult(ImageSource(Buffer().write(bytes), options.context), null, DataSource.NETWORK)
    }

    class Factory(private val remote: () -> RemoteFiles) : Fetcher.Factory<RemoteThumb> {
        override fun create(data: RemoteThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            RemoteThumbFetcher(data, remote(), options, imageLoader)
    }
}

private fun keyOf(data: RemoteThumb) = "nx-thumb:${if (data.large) "m" else "s"}:${data.modified}:${data.path}"
