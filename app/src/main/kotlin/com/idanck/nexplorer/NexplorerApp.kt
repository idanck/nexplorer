package com.idanck.nexplorer

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.idanck.nexplorer.data.settings.SettingsRepository
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.update.UpdateManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** השירותים שחיים כל עוד התהליך חי: העדפות, תור ההעברות ומנהל העדכונים. */
class AppContainer(app: Application) {
    /** שגיאה בעבודת רקע (בדיקת עדכון, שמירת העדפה) נרשמת ביומן — לא מפילה את האפליקציה. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("nexplorer", "background work failed", e) },
    )
    val settings = SettingsRepository(app, scope)
    val transfers = TransferManager(app, scope)
    val updates = UpdateManager(app, settings, transfers)
}

class NexplorerApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** תמונות ממוזערות ברשת ובגלריה, כולל פריים ראשון של וידאו. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
