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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.idanck.nexplorer.core.Counts
import com.idanck.nexplorer.core.Entry
import com.idanck.nexplorer.core.FileKind
import com.idanck.nexplorer.core.FileType
import com.idanck.nexplorer.core.FileTypes
import com.idanck.nexplorer.core.NetPaths
import com.idanck.nexplorer.core.SpecialFolder
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

/** הסוג של האריח (1.10): גוון וסמל לכל סוג. */
fun typeOf(entry: Entry): FileType = FileTypes.of(entry, encrypted = EncryptedFiles.hasEncryptedName(entry.name))

/**
 * הסמל המקורי של הסוג (1.11, `FileGlyphs`) — וקטור שממלא את האריח כולו. PDF מוצג כטקסט "PDF" ([FileGlyph]);
 * כאן — שורות המסמך, למי שצריך וקטור.
 */
fun iconFor(type: FileType): ImageVector = when (type) {
    FileType.Folder -> FileGlyphs.Folder
    FileType.Image -> FileGlyphs.Image
    FileType.Video -> FileGlyphs.Video
    FileType.Audio -> FileGlyphs.Audio
    FileType.Pdf, FileType.Doc -> FileGlyphs.Doc
    FileType.Ebook -> FileGlyphs.Ebook
    FileType.Sheet -> FileGlyphs.Sheet
    FileType.Slides -> FileGlyphs.Slides
    FileType.Text -> FileGlyphs.Text
    FileType.Code -> FileGlyphs.Code
    FileType.Archive -> FileGlyphs.Archive
    FileType.Apk -> FileGlyphs.Apk
    FileType.Encrypted -> FileGlyphs.Lock
    FileType.Other -> FileGlyphs.File
}

fun iconFor(entry: Entry): ImageVector = iconFor(typeOf(entry))

/** התג על חזית התיקייה המוכרת (באותו גודל כמו התיקייה — הוקטור כבר ממקם אותו). */
fun badgeFor(folder: SpecialFolder): ImageVector = when (folder) {
    SpecialFolder.Camera -> FileGlyphs.CameraBadge
    SpecialFolder.Pictures -> FileGlyphs.ImageBadge
    SpecialFolder.Download -> FileGlyphs.DownloadBadge
    SpecialFolder.Music -> FileGlyphs.AudioBadge
    SpecialFolder.Movies -> FileGlyphs.VideoBadge
    SpecialFolder.Documents -> FileGlyphs.DocBadge
    SpecialFolder.Android -> FileGlyphs.ApkBadge
    SpecialFolder.Recordings -> FileGlyphs.MicBadge
    SpecialFolder.Vault -> FileGlyphs.LockBadge
}

/** צבעי האריח — מהפלטה של סוגי הקבצים (`NexplorerTheme.colors.fileTypes`). */
data class TileColors(val container: Color, val content: Color)

@Composable
@ReadOnlyComposable
fun tileColors(type: FileType): TileColors {
    val p = NexplorerTheme.colors.fileTypes
    val tone = when (type) {
        FileType.Folder -> p.folder
        FileType.Image -> p.image
        FileType.Video -> p.video
        FileType.Audio -> p.audio
        FileType.Pdf -> p.pdf
        FileType.Doc, FileType.Ebook -> p.doc
        FileType.Sheet -> p.sheet
        FileType.Slides -> p.slides
        FileType.Archive -> p.archive
        FileType.Apk -> p.apk
        FileType.Text, FileType.Code, FileType.Encrypted, FileType.Other -> p.neutral
    }
    return TileColors(tone.container, tone.content)
}

@Composable
@ReadOnlyComposable
fun tileColors(entry: Entry): TileColors = tileColors(typeOf(entry))

/**
 * הסמל של הפריט על אריח בגודל [tile] (הוקטורים של `FileGlyphs` ממלאים את האריח). תיקייה מוכרת — עם תג על החזית
 * בצבע המיכל (כמו חיתוך); PDF וקובץ "אחר" עם סיומת קצרה — טקסט ב-Mono ("PDF", "ISO") במקום סמל.
 */
@Composable
fun FileGlyph(entry: Entry, colors: TileColors, tile: Dp) {
    val type = typeOf(entry)
    val special = FileTypes.specialFolder(entry)
    val label = when (type) {
        FileType.Pdf -> "PDF"
        FileType.Other -> FileTypes.badge(entry)
        else -> null
    }
    when {
        label != null -> Text(
            label,
            color = colors.content,
            maxLines = 1,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = (tile.value * NexplorerDimens.TileLabelRatio).sp,
                letterSpacing = 0.sp,
            ),
        )
        else -> Box(contentAlignment = Alignment.Center) {
            Icon(iconFor(type), contentDescription = null, tint = colors.content, modifier = Modifier.size(tile))
            if (special != null) {
                Icon(badgeFor(special), contentDescription = null, tint = colors.container, modifier = Modifier.size(tile))
            }
        }
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
        FileGlyph(entry, colors, size)
    }
}

/** ברשת אין תמונות ממוזערות (שלב 5א): כל אחת הייתה הורדה של הקובץ כולו. */
val Entry.hasThumbnail: Boolean get() = (kind == FileKind.Image || kind == FileKind.Video) && !NetPaths.isRemote(path)

/** תמונה ממוזערת (Coil, כולל פריים מווידאו) על רקע האריח; עד שנטענת — האייקון. */
@Composable
fun FileThumbnail(entry: Entry, modifier: Modifier = Modifier) {
    val colors = tileColors(entry)
    Box(modifier.background(colors.container), contentAlignment = Alignment.Center) {
        FileGlyph(entry, colors, NexplorerDimens.ThumbnailGlyphTile)
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
    fun separate() {
        if (length > 0) append(" · ")
    }
    if (entry.isDirectory) {
        if (entry.childCount >= 0) append(Counts.items(entry.childCount))
        if (folderSize != null) {
            separate()
            withStyle(SpanStyle(fontFamily = JetBrainsMono)) { append(SizeFormat.short(folderSize).isolated()) }
        }
    } else {
        withStyle(SpanStyle(fontFamily = JetBrainsMono)) { append(SizeFormat.short(entry.size).isolated()) }
    }
    when {
        entry.lastModified > 0 -> {
            separate()
            append(HebrewDates.short(entry.lastModified, now, zone))
        }
        // שיתוף ברשת: לשיתוף אין תאריך (השרת מחזיר 0 — "1 בינו׳ 1970").
        NetPaths.isShare(entry.path) -> {
            separate()
            append("שיתוף")
        }
    }
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
