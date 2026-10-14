package com.idanck.nexplorer.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/**
 * מודל הגישה הזמני (D2): "גישה לכל הקבצים" באנדרואיד 11+, והרשאות האחסון הרגילות ב-8–10.
 * Shizuku ו-Root נשארים להחלטה של עידן.
 */
object StorageAccess {

    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** מסך ההגדרה של "גישה לכל הקבצים" לאפליקציה הזו, ואם הוא לא קיים — הרשימה הכללית. */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(specific) }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** בלי גישה אין מה להציג — מסך אחד שמסביר ומוביל להגדרה. הבדיקה חוזרת בכל חזרה לאפליקציה. */
@Composable
fun StorageAccessScreen(onGrantedLegacy: () -> Unit) {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onGrantedLegacy() else StorageAccess.openSettings(context)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(NexplorerDimens.Space5), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexplorerDimens.Space4)) {
                Surface(shape = NexplorerShape.Card, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(NexplorerDimens.Space5).size(NexplorerDimens.Fab),
                    )
                }
                Text("גישה לקבצים", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Text(
                    "כדי לנהל את כל הקבצים במכשיר — כולל העתקה, העברה ומחיקה בכל תיקייה — nexplorer צריך " +
                        "הרשאת \"גישה לכל הקבצים\". ההרשאה נשארת במכשיר; האפליקציה לא שולחת קבצים לשום מקום.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(NexplorerDimens.Space2))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            StorageAccess.openSettings(context)
                        } else {
                            legacyLauncher.launch(
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            )
                        }
                    },
                    shape = NexplorerShape.Pill,
                    modifier = Modifier.fillMaxWidth().height(NexplorerDimens.BottomBarButton),
                ) { Text("אפשר גישה") }
            }
        }
    }
}
