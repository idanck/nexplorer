package com.idanck.nexplorer.data.files

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build

/**
 * עד Android 10 שינויים דרך java.io לא מגיעים ל-MediaStore, והאוספים (תמונות, וידאו, מוזיקה)
 * מציגים קבצים שנמחקו ולא מציגים חדשים. מ-11 ה-FUSE מעדכן לבד, ולכן זה לא עושה כלום שם.
 * נתיב שכבר לא קיים — הסורק מוחק את הרשומה שלו.
 */
object MediaScan {
    fun changed(context: Context, paths: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null, null) }
    }
}
