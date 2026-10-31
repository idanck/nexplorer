package com.idanck.nexplorer.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Google TV ו-Android TV (1.15): ממשק לשלט — מסך הקבצים של הטלוויזיה ([TvBrowserScreen]) וערכה כהה תמיד.
 * לפי מצב הממשק של המערכת, ואם הוא לא מדווח — לפי התכונה leanback.
 */
object TvMode {
    fun isTv(context: Context): Boolean {
        val ui = context.getSystemService(UiModeManager::class.java)
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}

/** true בטלוויזיה — מסופק ב-MainActivity. */
val LocalTvMode = staticCompositionLocalOf { false }
