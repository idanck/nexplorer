package com.idanck.nexplorer.data.apk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import com.idanck.nexplorer.core.ApkInstallState
import com.idanck.nexplorer.core.ApkStatus
import java.io.IOException

/** מה שאפשר לדעת על APK בלי להתקין אותו, ומה מותקן במכשיר באותה חבילה. */
data class ApkDetails(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: Int,
    /** SHA-256 של תעודות החתימה; ריק = לא חתום (המערכת לא תתקין). */
    val signers: Set<String>,
    val installed: InstalledApp?,
    val state: ApkInstallState,
    val icon: Bitmap?,
)

data class InstalledApp(val versionName: String?, val versionCode: Long, val launchable: Boolean)

object ApkInspector {

    /**
     * קורא את ה-APK (בלי להריץ ממנו כלום). חבילה מותקנת נראית לנו רק אם יש לה סמל במסך הבית
     * (`<queries>` במניפסט) — כך לא צריך את ההרשאה הרגישה QUERY_ALL_PACKAGES.
     */
    fun read(context: Context, path: String): ApkDetails {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(path, PackageSigners.FLAGS or PackageManager.GET_PERMISSIONS)
            ?: throw IOException("זה לא קובץ APK תקין")
        val app = info.applicationInfo ?: throw IOException("זה לא קובץ APK תקין")
        // בלי הנתיב השם והסמל לא נטענים מהקובץ — מתקבלים שם החבילה וסמל ברירת המחדל.
        app.sourceDir = path
        app.publicSourceDir = path
        val versionCode = PackageInfoCompat.getLongVersionCode(info)
        val signers = PackageSigners.of(info)
        val installed = installedInfo(pm, info.packageName)
        val installedSigners = installed?.let { PackageSigners.of(it) }.orEmpty()
        val sameSigner = if (signers.isEmpty() || installedSigners.isEmpty()) null else signers == installedSigners
        val installedVersion = installed?.let { PackageInfoCompat.getLongVersionCode(it) }
        return ApkDetails(
            label = runCatching { app.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            permissions = info.requestedPermissions?.size ?: 0,
            signers = signers,
            installed = installed?.let {
                InstalledApp(it.versionName, installedVersion ?: 0, pm.getLaunchIntentForPackage(it.packageName) != null)
            },
            state = ApkStatus.of(versionCode, installedVersion, sameSigner),
            icon = runCatching { app.loadIcon(pm).toBitmap(ICON_PX, ICON_PX) }.getOrNull(),
        )
    }

    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    /** האם המערכת תתקין: SDK מינימלי. */
    fun runsHere(details: ApkDetails): Boolean = details.minSdk <= Build.VERSION.SDK_INT

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? = try {
        pm.getPackageInfo(packageName, PackageSigners.FLAGS)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private const val ICON_PX = 192
}
