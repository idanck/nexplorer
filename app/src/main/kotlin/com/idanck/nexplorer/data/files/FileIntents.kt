package com.idanck.nexplorer.data.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.idanck.nexplorer.MainActivity
import com.idanck.nexplorer.R
import java.io.File

/** פתיחה, שיתוף וקיצורי דרך — כל מה שעובר לאפליקציה אחרת דרך FileProvider. */
object FileIntents {

    const val ACTION_OPEN_FOLDER = "com.idanck.nexplorer.action.OPEN_FOLDER"
    const val EXTRA_PATH = "path"

    /** כל URI עובר ב-Intent שלוש פעמים (EXTRA_STREAM, ClipData, ה-chooser); מעבר לזה — חריגה ממגבלת ה-Binder. */
    const val MAX_SHARE = 500

    fun mimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "md", "log", "cfg", "conf", "ini", "toml", "yml", "yaml", "kt", "kts", "properties" -> "text/plain"
                else -> "application/octet-stream"
            }
    }

    private fun uriFor(context: Context, path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))

    private fun viewIntent(context: Context, path: String, name: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, path), mimeType(name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    /** false כשאין אפליקציה שמטפלת בסוג הזה — הממשק מציג הודעה במקום קריסה. */
    fun open(context: Context, path: String, name: String): Boolean = launch(context, viewIntent(context, path, name))

    fun openWith(context: Context, path: String, name: String): Boolean =
        launch(context, Intent.createChooser(viewIntent(context, path, name), "פתיחה באמצעות"))

    /** תיקיות לא משותפות; מחזיר כמה קבצים נשלחו בפועל. */
    fun share(context: Context, files: List<Pair<String, String>>): Int {
        if (files.isEmpty()) return 0
        val uris = files.map { (path, _) -> uriFor(context, path) }
        val types = files.map { (_, name) -> mimeType(name) }.distinct()
        val type = types.singleOrNull() ?: types.map { it.substringBefore('/') }.distinct().singleOrNull()
            ?.let { "$it/*" } ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = type
        // ההרשאה עוברת דרך ClipData; בלעדיו חלק מהאפליקציות מקבלות URI שאין להן גישה אליו.
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (launch(context, Intent.createChooser(intent, "שיתוף"))) uris.size else 0
    }

    fun canPinShortcut(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** קיצור דרך במסך הבית שפותח את התיקייה ישירות. */
    fun pinFolderShortcut(context: Context, path: String, label: String): Boolean {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_FOLDER)
            .putExtra(EXTRA_PATH, path)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val info = ShortcutInfoCompat.Builder(context, "folder:$path")
            .setShortLabel(label.ifBlank { path })
            .setLongLabel(path)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: RuntimeException) {
        // TransactionTooLargeException מגיע עטוף ב-RuntimeException — הודעה, לא קריסה.
        false
    }
}
