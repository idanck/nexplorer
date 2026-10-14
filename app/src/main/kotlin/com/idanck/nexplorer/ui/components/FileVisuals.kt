package com.idanck.nexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.HebrewDates
import com.idanck.nexplorer.core.SizeFormat
import com.idanck.nexplorer.core.isolated
import com.idanck.nexplorer.data.crypto.EncryptedFiles
import com.idanck.nexplorer.ui.theme.JetBrainsMono
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape
import com.idanck.nexplorer.ui.theme.NexplorerTheme
import java.io.File
import java.time.ZoneId

fun iconFor(entry: Entry): ImageVector = when (entry.kind) {
    FileKind.Folder -> if (entry.isVault) Icons.Outlined.Lock else Icons.Outlined.Folder
    FileKind.Image -> Icons.Outlined.Image
    FileKind.Video -> Icons.Outlined.Movie
    FileKind.Audio -> Icons.Outlined.MusicNote
    FileKind.Document -> if (entry.extension == "pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Description
    FileKind.Text -> Icons.AutoMirrored.Outlined.Article
    FileKind.Archive -> Icons.Outlined.FolderZip
    FileKind.Apk -> Icons.Outlined.Android
    FileKind.Other -> if (EncryptedFiles.hasEncryptedName(entry.name)) Icons.Outlined.Lock else Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** צבעי האריח לפי הלוחות: תיקייה בכחול, תמונה/וידאו בענבר, PDF באדום, השאר אפור. */
data class TileColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun tileColors(entry: Entry): TileColors {
    val scheme = MaterialTheme.colorScheme
    val ext = NexplorerTheme.colors
    return when {
        entry.kind == FileKind.Folder -> TileColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        entry.kind == FileKind.Image || entry.kind == FileKind.Video -> TileColors(ext.amberContainer, ext.onAmberContainer)
        entry.extension == "pdf" -> TileColors(scheme.surfaceVariant, scheme.error)
        else -> TileColors(scheme.surfaceVariant, scheme.onSurface)
    }
}

@Composable
fun FileIconTile(entry: Entry, modifier: Modifier = Modifier, size: Dp = NexplorerDimens.IconTile) {
    val colors = tileColors(entry)
    Box(
        modifier = modifier
            .size(size)
            .clip(NexplorerShape.IconTile)
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(entry), contentDescription = null, tint = colors.content, modifier = Modifier.size(size / 2))
    }
}

/** ברשת אין תמונות ממוזערות (שלב 5א): כל אחת הייתה הורדה של הקובץ כולו. */
val Entry.hasThumbnail: Boolean get() = (kind == FileKind.Image || kind == FileKind.Video) && !NetPaths.isRemote(path)

/** תמונה ממוזערת (Coil, כולל פריים מווידאו) על רקע האריח; עד שנטענת — האייקון. */
@Composable
fun FileThumbnail(entry: Entry, modifier: Modifier = Modifier) {
    val colors = tileColors(entry)
    Box(modifier.background(colors.container), contentAlignment = Alignment.Center) {
        Icon(iconFor(entry), contentDescription = null, tint = colors.content)
        if (entry.hasThumbnail) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(entry.path)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** תיבת הסימון של מצב בחירה (24, פינות 7): מלאה בכחול כשנבחר, מתאר אפור כשלא. */
@Composable
fun SelectionBox(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(NexplorerDimens.Checkbox)
            .clip(NexplorerShape.Checkbox)
            .then(
                if (selected) Modifier.background(scheme.primary)
                else Modifier.border(NexplorerDimens.Space1 / 2, scheme.outline, NexplorerShape.Checkbox),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "נבחר", tint = scheme.onPrimary, modifier = Modifier.size(NexplorerDimens.Space4))
    }
}

/** כותרת מדור ("השבוע", "תיקיות"), עם טקסט אופציונלי בצד הסוף ("מיון: תאריך ↓"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null, first: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = NexplorerDimens.Space5 - NexplorerDimens.Space1,
                end = NexplorerDimens.Space5 - NexplorerDimens.Space1,
                top = if (first) NexplorerDimens.Space2 else NexplorerDimens.Space3 + NexplorerDimens.Space1 / 2,
                bottom = NexplorerDimens.Space1,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (trailing != null) {
            Spacer(Modifier.width(NexplorerDimens.Space2))
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = TextUnit.Unspecified),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "670 פריטים · 12 באוק׳, 10:12" או "3.2 MB · 12 באוק׳, 10:34" — הגודל ב-JetBrains Mono ומבודד. */
/** [folderSize] — "גודל תיקיות": הגודל שחושב לתיקייה (null — לא מוצג או עוד לא חושב). */
fun metaLine(entry: Entry, now: Long, zone: ZoneId = ZoneId.systemDefault(), folderSize: Long? = null): AnnotatedString = buildAnnotatedString {
    if (entry.isDirectory) {
        if (entry.childCount >= 0) {
            append(Counts.items(entry.childCount))
            append(" · ")
        }
        if (folderSize != null) {
            withStyle(SpanStyle(fontFamily = JetBrainsMono)) { append(SizeFormat.short(folderSize).isolated()) }
            append(" · ")
        }
    } else {
        withStyle(SpanStyle(fontFamily = JetBrainsMono)) { append(SizeFormat.short(entry.size).isolated()) }
        append(" · ")
    }
    append(HebrewDates.short(entry.lastModified, now, zone))
}

/** טקסט עם קטעים מודגשים בענבר — התאמת חיפוש (לוח "חיפוש"). */
fun highlighted(text: String, ranges: List<IntRange>, highlight: SpanStyle): AnnotatedString = buildAnnotatedString {
    var at = 0
    ranges.forEach { r ->
        if (r.first > at) append(text.substring(at, r.first))
        withStyle(highlight) { append(text.substring(r.first, (r.last + 1).coerceAtMost(text.length))) }
        at = r.last + 1
    }
    if (at < text.length) append(text.substring(at))
}
