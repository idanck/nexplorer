package com.idanck.nexplorer.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

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

/**
 * Google TV: הפריט שמקבל את הפוקוס כשמסך נפתח (הגדרות, חיפוש, מנתח…) — בלי זה הפוקוס נשאר על המסך שנסגר, ושום
 * לחיצה בשלט לא מגיעה למסך החדש. NexplorerRoot מספק אותו ומבקש עליו פוקוס; מחוץ לטלוויזיה — null.
 */
val LocalScreenEntry = staticCompositionLocalOf<FocusRequester?> { null }

/** הפריט הראשון שהשלט מגיע אליו במסך (בדרך כלל "חזרה"). */
fun Modifier.screenEntry(): Modifier = composed {
    val entry = LocalScreenEntry.current
    if (entry != null) focusRequester(entry) else this
}
