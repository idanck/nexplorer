package com.idanck.nexplorer.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
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
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/**
 * מודל הגישה הזמני (D2): "גישה לכל הקבצים" (Android 11 ומעלה — minSdk 30, D9).
 * Shizuku ו-Root נשארים להחלטה של עידן.
 */
object StorageAccess {

    fun granted(): Boolean = Environment.isExternalStorageManager()

    /** מסך ההגדרה של "גישה לכל הקבצים" לאפליקציה הזו, ואם הוא לא קיים — הרשימה הכללית. */
    fun openSettings(context: Context) {
        val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(specific) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

/** בלי גישה אין מה להציג — מסך אחד שמסביר ומוביל להגדרה. הבדיקה חוזרת בכל חזרה לאפליקציה. */
@Composable
fun StorageAccessScreen() {
    val context = LocalContext.current
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
                    onClick = { StorageAccess.openSettings(context) },
                    shape = NexplorerShape.Pill,
                    modifier = Modifier.fillMaxWidth().height(NexplorerDimens.BottomBarButton),
                ) { Text("אפשר גישה") }
            }
        }
    }
}
