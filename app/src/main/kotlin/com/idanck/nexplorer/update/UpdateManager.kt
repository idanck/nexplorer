package com.idanck.nexplorer.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.idanck.nexplorer.BuildConfig
import com.idanck.nexplorer.data.apk.PackageSigners
import com.idanck.nexplorer.data.settings.SettingsRepository
import com.idanck.nexplorer.data.transfer.TransferManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** למה התקנה לא יכולה להתחיל — כל אחת היא משפט אחר למשתמש, וחלקן עם כפתור משלהן. */
enum class UpdateBlocker(val message: String) {
    InstallPermission("צריך לאשר ל-nexplorer להתקין עדכונים"),
    Metered("ההגדרה היא לעדכן רק ב-Wi-Fi"),
    NeedsNewerAndroid("הגרסה החדשה דורשת אנדרואיד חדש יותר"),
    NoSpace("אין מספיק מקום פנוי להורדה"),
    TransferActive("יש העברה פעילה — התקנה עכשיו תעצור אותה"),
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val bytes: Long) : UpdateState {
        val fraction: Float get() = (bytes.toFloat() / manifest.sizeBytes).coerceIn(0f, 1f)
    }
    data class Verifying(val manifest: UpdateManifest) : UpdateState
    /**
     * הסשן נמסר למערכת; היא אחראית מכאן. [startedAt] — כדי שמצב תקוע יפוג.
     * [awaitingUser] — המערכת ביקשה אישור, והחלון לא בהכרח מוצג (נחסם ברקע, או שהמשתמש יצא ממנו).
     */
    data class Installing(val manifest: UpdateManifest, val startedAt: Long, val awaitingUser: Boolean = false) : UpdateState
    data class Failed(val message: String, val manifest: UpdateManifest?) : UpdateState
    data class Blocked(val manifest: UpdateManifest, val blocker: UpdateBlocker) : UpdateState
}

/**
 * עדכון מתוך האפליקציה (D5): האפליקציה מופצת מחוץ לחנות, ולכן עושה את עבודת החנות בעצמה —
 * שואלת את המניפסט אם יש גרסה חדשה, מורידה, מוכיחה שזה הקובץ שהמניפסט מתאר, ומוסרת ל-PackageInstaller.
 *
 * שלושה דברים לא נלקחים כמובנים: המניפסט רק ב-HTTPS ובפענוח קפדני; הקובץ נבדק מול SHA-256
 * לפני כל דבר אחר; ותעודת החתימה של ה-APK מושווית לזו של האפליקציה המותקנת, כדי שאי-התאמה
 * תופיע כהודעה ברורה ולא כ-INSTALL_FAILED_UPDATE_INCOMPATIBLE בסוף הדרך.
 *
 * מבוסס על המנגנון של TiviCopy (אותו מבנה, אותן החלטות), כולל הלקח מ-issue #29 שם:
 * חסימה בגלל הרשאת התקנה חייבת להוביל למסך ההרשאה, לא לכפתור "אישור" שרק סוגר.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val transfers: TransferManager,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _canInstall = MutableStateFlow(canRequestInstalls())
    /** ההרשאה ניתנת במסך של המערכת, ולכן נקראת מחדש בכל חזרה לאפליקציה. */
    val canInstall: StateFlow<Boolean> = _canInstall

    private val updateDir: File get() = File(context.cacheDir, "updates")
    private val installing = AtomicBoolean(false)

    /** הסשן האחרון שנמסר; תוצאות של סשנים ישנים (שננטשו) לא נוגעות במצב. */
    @Volatile private var sessionId: Int = NO_SESSION

    /** חלון האישור של המערכת לסשן הנוכחי — כדי להציג אותו שוב אם לא הוצג או נסגר. */
    @Volatile private var confirmIntent: Intent? = null

    val enabled: Boolean get() = BuildConfig.UPDATES_ENABLED

    /**
     * נקרא בכל חזרה לאפליקציה. מאשר שההרשאה ניתנה (אז "צריך לאשר" הופך שוב ל"עדכן"),
     * ומפקיע התקנה שהמערכת לא השלימה ולא דיווחה עליה.
     */
    fun refreshInstallPermission(): Boolean = canRequestInstalls().also { granted ->
        _canInstall.value = granted
        when (val s = _state.value) {
            is UpdateState.Blocked -> if (granted && s.blocker == UpdateBlocker.InstallPermission) {
                _state.value = UpdateState.Available(s.manifest)
            }
            is UpdateState.Installing -> if (System.currentTimeMillis() - s.startedAt >= INSTALL_HANDOFF_TIMEOUT_MS) {
                abandonCurrentSession()
                _state.value = UpdateState.Failed("ההתקנה לא הושלמה", s.manifest)
            }
            else -> Unit
        }
    }

    /**
     * [force] = המשתמש לחץ "בדוק עכשיו". בלעדיו — לכל היותר פעם ביום, ושקט בכשל:
     * בדיקת רקע שלא הצליחה להגיע לשרת לא מציגה שגיאה שאף אחד לא ביקש.
     */
    suspend fun check(force: Boolean) {
        if (!enabled || inFlight()) return
        val now = System.currentTimeMillis()
        val current = settings.state.value
        if (!force) {
            if (!current.updateAutoCheck) return
            if (now - current.updateLastCheckUtc in 0 until CHECK_INTERVAL_MS) {
                restorePending()
                return
            }
        }
        if (force) _state.value = UpdateState.Checking
        val fetched = withContext(Dispatchers.IO) { fetchManifest() }
        // בזמן שהבדיקה רצה המשתמש יכול היה ללחוץ "עדכן" — אז ההורדה קובעת, לא הבדיקה.
        if (installing.get() || inFlight()) return
        fetched.onFailure { e ->
            // בדיקת רקע שנכשלה לא מוחקת באנר "יש גרסה" שכבר מוצג.
            if (force) _state.value = UpdateState.Failed(e.message ?: "הבדיקה נכשלה", null)
            return
        }
        val (manifest, json) = fetched.getOrThrow()
        settings.update { it.copy(updateLastCheckUtc = now, updateLastManifest = json) }
        val newer = manifest.isNewerThan(BuildConfig.VERSION_CODE)
        if (installing.get() || inFlight()) return
        withContext(Dispatchers.IO) { prune(keep = manifest.versionCode.takeIf { newer }) }
        _state.value = when {
            newer && (force || !isSkipped(manifest)) -> UpdateState.Available(manifest)
            force -> UpdateState.UpToDate(now)
            else -> UpdateState.Idle
        }
    }

    /** הבדיקה האחרונה מצאה גרסה חדשה לפני כמה שעות — מציגים אותה בלי לשאול את השרת שוב. */
    private fun restorePending() {
        val json = settings.state.value.updateLastManifest ?: return
        val manifest = UpdateManifest.parse(json).getOrNull() ?: return
        if (!manifest.isNewerThan(BuildConfig.VERSION_CODE) || isSkipped(manifest)) return
        if (_state.value is UpdateState.Idle) _state.value = UpdateState.Available(manifest)
    }

    private fun isSkipped(manifest: UpdateManifest) =
        settings.state.value.updateSkippedVersionCode >= manifest.versionCode

    suspend fun skip(manifest: UpdateManifest) {
        settings.update { it.copy(updateSkippedVersionCode = manifest.versionCode) }
        _state.value = UpdateState.Idle
    }

    /** "אחר כך": מסתיר עד הבדיקה הבאה, בלי לדלג על הגרסה. */
    fun dismiss() {
        when (val s = _state.value) {
            is UpdateState.Available, is UpdateState.Failed, is UpdateState.UpToDate, is UpdateState.Blocked ->
                _state.value = UpdateState.Idle
            // רק כשהמערכת מחכה למשתמש: ביטול כאן נוטש את הסשן, ו"עדכן" יתחיל מחדש מהקובץ שכבר ירד.
            is UpdateState.Installing -> if (s.awaitingUser) {
                abandonCurrentSession()
                _state.value = UpdateState.Available(s.manifest)
            }
            else -> Unit
        }
    }

    private fun inFlight(): Boolean = when (val s = _state.value) {
        is UpdateState.Downloading, is UpdateState.Verifying -> true
        is UpdateState.Installing -> System.currentTimeMillis() - s.startedAt < INSTALL_HANDOFF_TIMEOUT_MS
        else -> false
    }

    /** כל מה שיכול לסרב — מסרב לפני שבית אחד ירד. */
    fun blockerFor(manifest: UpdateManifest): UpdateBlocker? = when {
        !refreshInstallPermission() -> UpdateBlocker.InstallPermission
        transfers.hasActive -> UpdateBlocker.TransferActive
        settings.state.value.updateWifiOnly && !isUnmetered() -> UpdateBlocker.Metered
        manifest.minSdk > Build.VERSION.SDK_INT -> UpdateBlocker.NeedsNewerAndroid
        context.cacheDir.usableSpace < manifest.sizeBytes * 2 -> UpdateBlocker.NoSpace
        else -> null
    }

    suspend fun downloadAndInstall(manifest: UpdateManifest) {
        // שני מקומות מפעילים את זה (הבאנר וההגדרות), ואפשר ללחוץ פעמיים.
        if (inFlight() || !installing.compareAndSet(false, true)) return
        try {
            blockerFor(manifest)?.let {
                _state.value = UpdateState.Blocked(manifest, it)
                return
            }
            _state.value = UpdateState.Downloading(manifest, 0)
            val file = withContext(Dispatchers.IO) {
                download(manifest) { bytes -> _state.value = UpdateState.Downloading(manifest, bytes) }
            }.getOrElse {
                _state.value = UpdateState.Failed(it.message ?: "ההורדה נכשלה", manifest)
                return
            }
            _state.value = UpdateState.Verifying(manifest)
            withContext(Dispatchers.IO) { verifySignature(file, manifest) }.onFailure {
                file.delete()
                _state.value = UpdateState.Failed(it.message ?: "האימות נכשל", manifest)
                return
            }
            _state.value = UpdateState.Installing(manifest, System.currentTimeMillis())
            withContext(Dispatchers.IO) { commit(file) }.onFailure {
                file.delete()
                _state.value = UpdateState.Failed(it.message ?: "ההתקנה נכשלה", manifest)
            }
        } finally {
            installing.set(false)
        }
    }

    /** מה-receiver. [session] = ‏EXTRA_SESSION_ID; תוצאה של סשן אחר — ישן, שננטש — לא רלוונטית. */
    internal fun isCurrentSession(session: Int): Boolean = session != NO_SESSION && session == sessionId

    internal fun onUserActionNeeded(confirm: Intent) {
        confirmIntent = confirm
        val s = _state.value as? UpdateState.Installing ?: return
        _state.value = s.copy(awaitingUser = true)
    }

    internal fun onInstallFailed(message: String) {
        confirmIntent = null
        sessionId = NO_SESSION
        val manifest = (_state.value as? UpdateState.Installing)?.manifest
        _state.value = UpdateState.Failed(message, manifest)
    }

    /** "המשך התקנה": פותח שוב את חלון האישור של המערכת. false — אין חלון כזה, וצריך להתחיל מחדש. */
    fun continueInstall(launcher: Context): Boolean {
        val confirm = confirmIntent ?: return false
        return runCatching { launcher.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun abandonCurrentSession() {
        val id = sessionId
        sessionId = NO_SESSION
        confirmIntent = null
        if (id != NO_SESSION) runCatching { context.packageManager.packageInstaller.abandonSession(id) }
    }

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** מסך המערכת "התקנת אפליקציות לא מוכרות" של nexplorer. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun prune(keep: Int?) {
        val wanted = keep?.let { apkName(it) }
        updateDir.listFiles()?.forEach { if (it.name != wanted) it.delete() }
    }

    private fun apkName(versionCode: Int) = "nexplorer-$versionCode.apk"

    private fun open(url: String, connectTimeout: Int, readTimeout: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "nexplorer/${BuildConfig.VERSION_NAME}")
        return connection
    }

    private fun fetchManifest(): Result<Pair<UpdateManifest, String>> = try {
        val connection = open(BuildConfig.UPDATE_MANIFEST_URL, 15_000, 20_000)
        try {
            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    UpdateManifest.parse(body).map { it to body }
                }
                404 -> Result.failure(UpdateException("עדיין לא פורסמה גרסה"))
                else -> Result.failure(UpdateException("שרת העדכונים החזיר $code"))
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Result.failure(UpdateException("אין חיבור לשרת העדכונים", e))
    }

    /** מוריד לזיכרון המטמון ומחשב SHA-256 תוך כדי, כך שהקובץ נקרא פעם אחת. */
    private suspend fun download(manifest: UpdateManifest, onProgress: (Long) -> Unit): Result<File> {
        updateDir.mkdirs()
        val target = File(updateDir, apkName(manifest.versionCode))
        updateDir.listFiles()?.forEach { if (it.name != target.name) it.delete() }
        // ניסיון חוזר אחרי התקנה שנכשלה: הקובץ כבר כאן, ובדיקת ה-hash זולה מהורדה.
        if (target.length() == manifest.sizeBytes && hashOf(target) == manifest.sha256) {
            onProgress(manifest.sizeBytes)
            return Result.success(target)
        }
        target.delete()
        return try {
            val connection = open(manifest.apkUrl, 20_000, 60_000)
            try {
                if (connection.responseCode != 200) {
                    return Result.failure(UpdateException("ההורדה החזירה ${connection.responseCode}"))
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastReport = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > manifest.sizeBytes) throw UpdateException("הקובץ גדול ממה שהמניפסט מתאר")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            if (total - lastReport >= 256 * 1024) {
                                lastReport = total
                                onProgress(total)
                            }
                        }
                    }
                }
                onProgress(total)
                when {
                    total != manifest.sizeBytes -> throw UpdateException("ההורדה נקטעה")
                    digest.digest().hex() != manifest.sha256 -> throw UpdateException("הקובץ שירד לא תואם למניפסט")
                }
                Result.success(target)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: UpdateException) {
            target.delete()
            Result.failure(e)
        } catch (e: Exception) {
            target.delete()
            Result.failure(UpdateException("ההורדה נכשלה", e))
        }
    }

    private fun hashOf(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().hex()
    }.getOrNull()

    /**
     * אנדרואיד מחליף חבילה רק כשה-APK החדש חתום באותה תעודה, וכשל בשלב ה-commit הוא קוד סטטוס בלבד.
     * ההשוואה כאן הופכת "נחתם במפתח אחר" למשהו שאפשר להבין ולפעול לפיו.
     */
    private fun verifySignature(file: File, manifest: UpdateManifest): Result<Unit> = try {
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageSigners.archiveFlags)
        val installed = pm.getPackageInfo(context.packageName, PackageSigners.installedFlags)
        val candidateCerts = candidate?.let { PackageSigners.of(it) }.orEmpty()
        when {
            candidate == null -> Result.failure(UpdateException("הקובץ שירד אינו APK תקין"))
            candidate.packageName != context.packageName -> Result.failure(UpdateException("הקובץ שייך לאפליקציה אחרת"))
            // אחרת המכשיר "מתעדכן" לאותה גרסה בלולאה, או נכשל על הורדת גרסה.
            PackageInfoCompat.getLongVersionCode(candidate) != manifest.versionCode.toLong() ->
                Result.failure(UpdateException("גרסת הקובץ לא תואמת למניפסט"))
            candidateCerts.isEmpty() -> Result.failure(UpdateException("לא נמצאה חתימה בקובץ שירד"))
            candidateCerts != PackageSigners.of(installed) ->
                Result.failure(UpdateException("העדכון חתום במפתח אחר מהגרסה המותקנת"))
            else -> Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(UpdateException("לא ניתן לאמת את החתימה", e))
    }

    private fun commit(file: File): Result<Unit> {
        val installer = context.packageManager.packageInstaller
        // סשנים שנפתחו ולא נסגרו מחזיקים עותק של ה-APK, ומגבלת הסשנים לאפליקציה קטנה.
        sessionId = NO_SESSION
        confirmIntent = null
        runCatching { installer.mySessions.forEach { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(file.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // עדכון עצמי בלי חלון אישור (דורש UPDATE_PACKAGES_WITHOUT_USER_ACTION במניפסט). באנדרואיד 8–11,
            // או כשבעלות העדכון שייכת לחנות אחרת (14+), המערכת מציגה אישור — ה-receiver מטפל בזה.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (e: Exception) {
            return Result.failure(UpdateException("לא ניתן לפתוח סשן התקנה", e))
        }
        return try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("nexplorer-update", 0, file.length()).use { sink ->
                    file.inputStream().use { it.copyTo(sink) }
                    session.fsync(sink)
                }
                this.sessionId = sessionId
                session.commit(statusIntent(sessionId).intentSender)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            Result.failure(UpdateException("ההתקנה נכשלה", e))
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        // רכיב מפורש: broadcast מרומז לא מגיע ל-receiver במניפסט, וכך התוצאה מגיעה רק אלינו.
        val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION_STATUS)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val INSTALL_HANDOFF_TIMEOUT_MS = 15 * 60 * 1000L
        private const val NO_SESSION = -1
    }
}

internal fun ByteArray.hex(): String = joinToString("") { ((it.toInt() and 0xff) or 0x100).toString(16).substring(1) }
