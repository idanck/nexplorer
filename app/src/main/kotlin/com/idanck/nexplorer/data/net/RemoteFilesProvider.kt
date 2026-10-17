package com.idanck.nexplorer.data.net

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.idanck.nexplorer.NexplorerApp
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.data.files.FileIntents
import java.io.FileNotFoundException
import java.io.IOException

/**
 * קובץ ברשת לאפליקציה אחרת ("פתיחה", "פתיחה באמצעות"), בלי עותק במכשיר:
 * `content://<חבילה>.remote/<פרוטוקול>/<חיבור>/<נתיב>` ([NetPaths.parts]). הקובץ עובר כ-FD של proxy
 * ([StorageManager.openProxyFileDescriptor]): כל קריאה של האפליקציה האחרת היא קריאה מהשרת מאותו מקום.
 * קריאה בלבד. לא מיוצא — אפליקציה אחרת מקבלת גישה ל-URI אחד, ב-Intent שהאפליקציה שלחה ([FileIntents]).
 */
class RemoteFilesProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = pathOf(uri)?.let { FileIntents.mimeType(NetPaths.name(it)) }

    /** השם והגודל — מה שאפליקציות שואלות לפני שהן קוראות. רק העמודות האלה, כמו ב-FileProvider. */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val path = pathOf(uri) ?: return null
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        // השרת לא עונה — בלי גודל (מותר), והשגיאה תגיע בפתיחה.
        val size = if (OpenableColumns.SIZE in columns) runCatching { entryOf(path).size }.getOrNull() else null
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) NetPaths.name(path) else size })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if ('w' in mode || 'a' in mode) throw FileNotFoundException("קובץ ברשת נפתח לקריאה בלבד")
        val path = pathOf(uri) ?: throw FileNotFoundException("כתובת לא תקינה")
        val entry = entryOf(path)
        val fs = fsFor(path)
        // חוט לכל קובץ פתוח: כל קריאה מחכה לשרת, ואסור שתעצור קבצים אחרים.
        val thread = HandlerThread("remote-file").apply { start() }
        return try {
            requireNotNull(context).getSystemService(StorageManager::class.java)
                .openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, Reader(RemoteReader(fs, path, entry.size), thread), Handler(thread.looper))
        } catch (e: IOException) {
            thread.quitSafely()
            throw FileNotFoundException(e.message)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    /**
     * בקשה שמגיעה כשהתהליך רק עלה (אפליקציה אחרת קוראת אחרי שהאפליקציה נסגרה) יכולה להקדים את
     * Application.onCreate — ואז אין עדיין חיבורים.
     */
    private fun remote(): RemoteFiles {
        val app = context?.applicationContext as? NexplorerApp
        return app?.takeIf { it.isReady }?.container?.remote ?: throw FileNotFoundException("האפליקציה עוד עולה — נסה שוב")
    }

    /** מה שעובר לאפליקציה האחרת הוא רק FileNotFoundException (עם ההודעה של החיבור). */
    private fun fsFor(path: String): RemoteFs = try {
        remote().fsFor(path)
    } catch (e: IOException) {
        throw FileNotFoundException(e.message)
    }

    private fun entryOf(path: String): Entry {
        val entry = try {
            fsFor(path).stat(path)
        } catch (e: IOException) {
            throw FileNotFoundException(e.message)
        }
        return entry?.takeIf { !it.isDirectory } ?: throw FileNotFoundException("הקובץ לא נמצא בשרת")
    }

    /** הקריאות של האפליקציה האחרת, ברצף, בחוט של הקובץ ([RemoteReader]). כשל — EIO, כמו דיסק שנכשל. */
    private class Reader(private val reader: RemoteReader, private val thread: HandlerThread) : ProxyFileDescriptorCallback() {

        override fun onGetSize(): Long = reader.length

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
            reader.read(offset, size, data)
        } catch (e: IOException) {
            throw ErrnoException("onRead", OsConstants.EIO)
        }

        override fun onRelease() {
            reader.close()
            thread.quitSafely()
        }
    }

    companion object {
        fun uriFor(context: Context, path: String): Uri {
            val parts = requireNotNull(NetPaths.parts(path)) { "not a network path" }
            return Uri.Builder().scheme("content").authority(authority(context)).apply { parts.forEach(::appendPath) }.build()
        }

        private fun authority(context: Context) = "${context.packageName}.remote"

        private fun pathOf(uri: Uri): String? = NetPaths.fromParts(uri.pathSegments)
    }
}
