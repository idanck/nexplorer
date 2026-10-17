package com.idanck.nexplorer.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.data.net.RemoteFiles
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * הנגנים קוראים קובץ ברשת ישירות מהשרת, בלי עותק במכשיר: כל פתיחה (גם דילוג) — זרם חדש מהמקום
 * המבוקש ([com.idanck.nexplorer.data.net.RemoteFs.openRead]). ה-URI הוא הנתיב ברשת, מקודד ([uriOf]):
 * בנתיב יכולים להיות `#`, `?` ו-`%`.
 */
@OptIn(UnstableApi::class)
class RemoteDataSource(private val remote: RemoteFiles) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var input: InputStream? = null
    private var remaining = 0L
    private var opened = false
    /** הגודל — בקשה לשרת; פעם אחת לקובץ, ולא בכל דילוג. */
    private var known: Pair<String, Long>? = null

    override fun open(dataSpec: DataSpec): Long {
        val path = pathOf(dataSpec.uri) ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val fs = remote.fsFor(path)
            val size = known?.takeIf { it.first == path }?.second
                ?: (fs.stat(path) ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)).size.also { known = path to it }
            // גודל 0: שרת WebDAV שלא דיווח — קוראים עד הסוף.
            if (size > 0 && dataSpec.position > size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            // הנגן קורא בגושים קטנים (כותרות, פריים); SFTP שולח בקשה לפי גודל הקריאה.
            input = BufferedInputStream(fs.openRead(path, dataSpec.position), BUFFER)
            remaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size > 0 -> size - dataSpec.position
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: DataSourceException) {
            throw e
        } catch (e: IOException) {
            throw failed(e)
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = input ?: return C.RESULT_END_OF_INPUT
        val wanted = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(remaining, length.toLong()).toInt()
        val read = try {
            stream.read(buffer, offset, wanted)
        } catch (e: IOException) {
            throw failed(e)
        }
        if (read < 0) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        // דילוג מבטל את הטעינה ב-interrupt; הסגירה בשרת (SMB CLOSE) מחכה לתשובה, ובלי זה נכשלת מייד
        // והקובץ נשאר פתוח בשרת עד סוף החיבור.
        val interrupted = Thread.interrupted()
        try {
            input?.close()
        } catch (e: IOException) {
            throw failed(e)
        } finally {
            input = null
            if (interrupted) Thread.currentThread().interrupt()
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    /** ההודעה — מ-[com.idanck.nexplorer.data.net.RemoteFs], כבר בעברית ("השרת לא עונה"); ראה [playbackMessage]. */
    private fun failed(e: IOException) =
        DataSourceException(e.message ?: "החיבור לשרת נכשל", e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

    @OptIn(UnstableApi::class)
    class Factory(private val remote: RemoteFiles) : DataSource.Factory {
        override fun createDataSource(): DataSource = RemoteDataSource(remote)
    }

    companion object {
        /** לא `file` ולא scheme ש-DefaultDataSource מכיר: מה שהוא לא מכיר מגיע ל-[Factory]. */
        private const val SCHEME = "nexplorer-net"
        private const val BUFFER = 256 * 1024

        fun uriOf(path: String): Uri = Uri.fromParts(SCHEME, path, null)

        fun pathOf(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.schemeSpecificPart?.takeIf(NetPaths::isRemote)
    }
}
