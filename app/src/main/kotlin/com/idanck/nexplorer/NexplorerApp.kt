package com.idanck.nexplorer

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.idanck.nexplorer.data.net.RemoteThumb
import com.idanck.nexplorer.data.net.RemoteThumbFetcher
import com.idanck.nexplorer.data.net.RemoteThumbKeyer
import com.idanck.nexplorer.data.crypto.EncryptedPreview
import com.idanck.nexplorer.data.crypto.VaultKeys
import com.idanck.nexplorer.data.net.CloudAuths
import com.idanck.nexplorer.data.net.CloudTokens
import com.idanck.nexplorer.data.net.ConnectionSecrets
import com.idanck.nexplorer.data.net.RemoteFiles
import com.idanck.nexplorer.data.settings.SettingsRepository
import com.idanck.nexplorer.data.transfer.TransferManager
import com.idanck.nexplorer.ui.keys.KeyBus
import com.idanck.nexplorer.update.UpdateManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** השירותים שחיים כל עוד התהליך חי: העדפות, תור ההעברות ומנהל העדכונים. */
class AppContainer(app: Application) {
    /** שגיאה בעבודת רקע (בדיקת עדכון, שמירת העדפה) נרשמת ביומן — לא מפילה את האפליקציה. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.w("nexplorer", "background work failed", e) },
    )
    val settings = SettingsRepository(app, scope)
    /** תיקיות מוצפנות שנפתחו — בזיכרון בלבד. */
    val vaults = VaultKeys()
    /** ענן (D4): ההרשאות של Google ב-Play services, וה-refresh tokens של OneDrive ב-Keystore. */
    val cloud = CloudAuths(app, CloudTokens(app))
    /** מקומות ברשת (D3) וענן: החיבורים מההעדפות, והסיסמאות מה-Keystore — רק כשמתחברים. */
    val remote = RemoteFiles(
        connectionOf = { id -> settings.state.value.connections.firstOrNull { it.id == id } },
        decrypt = ConnectionSecrets::decrypt,
        cloudAuth = cloud::forConnection,
    )
    val transfers = TransferManager(app, scope, vaults, remote)
    val updates = UpdateManager(app, settings, transfers)
    val keys = KeyBus()
}

class NexplorerApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    /** ספק תוכן נקרא כבר לפני onCreate (‏RemoteFilesProvider) — אז עוד אין [container]. */
    val isReady: Boolean get() = ::container.isInitialized

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // עותקים זמניים מהריצה הקודמת: מפוענחים ("פתח" בקובץ מוצפן) ומהרשת.
        container.scope.launch(Dispatchers.IO) {
            EncryptedPreview.clearStale(cacheDir)
            container.remote.clearStale(cacheDir)
        }
    }

    /**
     * תמונות ממוזערות ברשת ובגלריה, כולל פריים ראשון של וידאו; ומ-Synology — התמונה שה-NAS מכין ([RemoteThumb]).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(RemoteThumbKeyer())
                add(RemoteThumbFetcher.Factory { container.remote })
            }
            .crossfade(true)
            .build()
}
