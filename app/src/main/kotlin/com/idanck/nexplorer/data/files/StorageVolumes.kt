package com.idanck.nexplorer.data.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/** מקום אחסון במגירה: זיכרון פנימי, כרטיס SD, USB. */
data class StorageVolumeInfo(
    val path: String,
    val label: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else usedBytes.toFloat() / totalBytes
}

object StorageVolumes {

    const val PRIMARY_LABEL = "זיכרון פנימי"

    fun primaryPath(): String = Environment.getExternalStorageDirectory().absolutePath

    fun list(context: Context): List<StorageVolumeInfo> {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val roots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
                .mapNotNull { volume -> volume.directory?.let { it to volume } }
        } else {
            // לפני Android 11 אין StorageVolume.directory: גוזרים את השורש מתיקיית האפליקציה בכל נפח.
            context.getExternalFilesDirs(null).filterNotNull().mapNotNull { appDir ->
                val root = appDir.absolutePath.substringBefore("/Android/data/")
                val volume = manager.getStorageVolume(appDir) ?: return@mapNotNull null
                File(root) to volume
            }
        }
        return roots
            .distinctBy { it.first.absolutePath }
            .map { (dir, volume) ->
                val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
                StorageVolumeInfo(
                    path = dir.absolutePath,
                    label = if (volume.isPrimary) PRIMARY_LABEL else volume.getDescription(context),
                    isPrimary = volume.isPrimary,
                    isRemovable = volume.isRemovable,
                    totalBytes = stat?.totalBytes ?: 0,
                    freeBytes = stat?.availableBytes ?: 0,
                )
            }
            .sortedByDescending { it.isPrimary }
    }

    /** הנפח שמכיל את [path] — הארוך ביותר שהוא קידומת, כדי ש-SD לא יתפרש כפנימי. */
    fun volumeOf(path: String, volumes: List<StorageVolumeInfo>): StorageVolumeInfo? =
        volumes.filter { path == it.path || path.startsWith(it.path + "/") }.maxByOrNull { it.path.length }
}
