package com.idanck.nexplorer.data.files

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** האוספים במגירה: תיקיות וירטואליות מתוך MediaStore. "הורדות" היא תיקייה אמיתית ולכן לא כאן. */
enum class MediaCollection(val label: String) {
    Images("תמונות"),
    Video("וידאו"),
    Audio("מוזיקה"),
}

object MediaCollections {

    /** תקרה סבירה לרשימה אחת; מעליה המיון והמדורים בממשק מתחילים להרגיש. */
    private const val LIMIT = 5000

    suspend fun list(context: Context, collection: MediaCollection): Result<List<Entry>> = withContext(Dispatchers.IO) {
        val uri: Uri = when (collection) {
            MediaCollection.Images -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaCollection.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaCollection.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        @Suppress("DEPRECATION") // DATA עדיין הדרך היחידה לקבל נתיב; עם גישה לכל הקבצים הוא תקף.
        val data = MediaStore.MediaColumns.DATA
        val projection = arrayOf(
            data,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        runCatching {
            val out = ArrayList<Entry>()
            context.contentResolver.query(
                uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val iPath = cursor.getColumnIndexOrThrow(data)
                val iName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iSize = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDate = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && out.size < LIMIT) {
                    val path = cursor.getString(iPath) ?: continue
                    val name = cursor.getString(iName) ?: path.substringAfterLast('/')
                    out += Entry(
                        path = path,
                        name = name,
                        isDirectory = false,
                        size = cursor.getLong(iSize),
                        childCount = -1,
                        lastModified = cursor.getLong(iDate) * 1000,
                        isHidden = name.startsWith("."),
                        kind = FileKinds.of(name, false),
                    )
                }
            }
            out
        }
    }
}
