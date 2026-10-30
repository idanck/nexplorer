package com.idanck.nexplorer.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * הסמלים המקוריים של nexplorer לאריחי הקבצים (שפת העיצוב 1.11, כיוון ג׳ — "אריח + סמלים מקוריים").
 * צוירו לאפליקציה: רשת 48, קו 2.4 (2–2.2 בפרטים), קצוות ופינות מעוגלים. כל סמל בצבע אחד — `Icon` צובע אותו
 * ב-`tint`, והשקיפות של גב התיקייה (55%) נשמרת. חורים (העדשה של המצלמה) — בכלל evenOdd, בלי צבע רקע.
 *
 * כל וקטור ממלא את האריח כולו (viewport 48): [Tile] — הסמל באריח של קובץ; [FolderPlace] — התיקייה; [BadgePlace] —
 * סמל קטן על חזית התיקייה (בצבע המיכל, כמו חיתוך). אותם נתיבים בלוח docs/design/boards/FileIcons.dc.html.
 */
object FileGlyphs {

    private class Spec(val d: String, val stroke: Float = 0f, val alpha: Float = 1f, val evenOdd: Boolean = false)

    private class Placement(val tx: Float, val ty: Float, val scale: Float)

    /** הסמל באריח של קובץ: מוגדל פי 1.2 וממורכז. */
    private val Tile = Placement(-4.8f, -9.6f, 1.2f)

    /** התיקייה באריח: 60%. */
    private val FolderPlace = Placement(9.6f, 9.6f, 0.6f)

    /** תג על חזית התיקייה: 47% (78% מסמל, בתוך התיקייה של 60%). */
    private val BadgePlace = Placement(12.768f, 13.476f, 0.468f)

    private fun vector(name: String, at: Placement, specs: List<Spec>): ImageVector =
        ImageVector.Builder(name, 48.dp, 48.dp, 48f, 48f).apply {
            group(scaleX = at.scale, scaleY = at.scale, translationX = at.tx, translationY = at.ty) {
                specs.forEach { s ->
                    addPath(
                        pathData = addPathNodes(s.d),
                        pathFillType = if (s.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = if (s.stroke == 0f) SolidColor(Color.Black) else null,
                        fillAlpha = s.alpha,
                        stroke = if (s.stroke > 0f) SolidColor(Color.Black) else null,
                        strokeLineWidth = s.stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()

    // ---------- הנתיבים (בקואורדינטות של סמל ברשת 48) ----------

    /** תמונה: הרים ושמש. */
    private val imagePaths = listOf(
        Spec("M15 36l6.2-8.2a1.2 1.2 0 0 1 1.9 0l3.6 4.7 2.4-2.9a1.2 1.2 0 0 1 1.9.1L35 36z"),
        Spec("M26.9 21.5a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0z"),
    )

    /** וידאו: משולש ניגון מעוגל. */
    private val videoPaths = listOf(
        Spec("M20 20.6v14.8a1.4 1.4 0 0 0 2.1 1.2l11.8-7.4a1.4 1.4 0 0 0 0-2.4l-11.8-7.4A1.4 1.4 0 0 0 20 20.6z"),
    )

    /** שמע: חמישה פסי גל. */
    private val audioPaths = listOf(
        Spec("M15.5 25h0a1.35 1.35 0 0 1 1.35 1.35v3.3a1.35 1.35 0 0 1 -1.35 1.35h-0a1.35 1.35 0 0 1 -1.35 -1.35v-3.3a1.35 1.35 0 0 1 1.35 -1.35z"),
        Spec("M19.75 22h0a1.35 1.35 0 0 1 1.35 1.35v9.3a1.35 1.35 0 0 1 -1.35 1.35h-0a1.35 1.35 0 0 1 -1.35 -1.35v-9.3a1.35 1.35 0 0 1 1.35 -1.35z"),
        Spec("M24 19.5h0a1.35 1.35 0 0 1 1.35 1.35v14.3a1.35 1.35 0 0 1 -1.35 1.35h-0a1.35 1.35 0 0 1 -1.35 -1.35v-14.3a1.35 1.35 0 0 1 1.35 -1.35z"),
        Spec("M28.25 23h0a1.35 1.35 0 0 1 1.35 1.35v7.3a1.35 1.35 0 0 1 -1.35 1.35h-0a1.35 1.35 0 0 1 -1.35 -1.35v-7.3a1.35 1.35 0 0 1 1.35 -1.35z"),
        Spec("M32.5 25h0a1.35 1.35 0 0 1 1.35 1.35v3.3a1.35 1.35 0 0 1 -1.35 1.35h-0a1.35 1.35 0 0 1 -1.35 -1.35v-3.3a1.35 1.35 0 0 1 1.35 -1.35z"),
    )

    /** מסמך: שורות, האחרונה קצרה. */
    private val docPaths = listOf(
        Spec("M16 21h16M16 26h16M16 31h16M16 36h9", stroke = 2.4f),
    )

    /** טקסט: שלוש שורות דקות. */
    private val textPaths = listOf(
        Spec("M16 22h16M16 27h16M16 32h12", stroke = 2.0f),
    )

    /** גיליון: טבלה. */
    private val sheetPaths = listOf(
        Spec("M17.2 20h13.6a2.2 2.2 0 0 1 2.2 2.2v11.6a2.2 2.2 0 0 1 -2.2 2.2h-13.6a2.2 2.2 0 0 1 -2.2 -2.2v-11.6a2.2 2.2 0 0 1 2.2 -2.2z", stroke = 2.4f),
        Spec("M15 25.5h18M15 30.5h18M21.5 20v16", stroke = 2.0f),
    )

    /** מצגת: מסך עם גרף עמודות על מעמד. */
    private val slidesPaths = listOf(
        Spec("M16.7 19.5h14.6a2.2 2.2 0 0 1 2.2 2.2v8.6a2.2 2.2 0 0 1 -2.2 2.2h-14.6a2.2 2.2 0 0 1 -2.2 -2.2v-8.6a2.2 2.2 0 0 1 2.2 -2.2z", stroke = 2.4f),
        Spec("M24 32.5v4M20 37h8", stroke = 2.4f),
        Spec("M19 29v-2.5M23 29v-5M27 29v-3.5", stroke = 2.2f),
    )

    /** קוד: < / >. */
    private val codePaths = listOf(
        Spec("M19.5 22.5L14.5 28l5 5.5M28.5 22.5l5 5.5-5 5.5M25.6 20.5l-3.2 15", stroke = 2.4f),
    )

    /** ספר: ספר פתוח. */
    private val ebookPaths = listOf(
        Spec("M24 23.5c-2.6-2-5.8-2.6-9-2.2v13.4c3.2-.4 6.4.2 9 2.2 2.6-2 5.8-2.6 9-2.2V21.3c-3.2-.4-6.4.2-9 2.2zM24 23.5v13.4", stroke = 2.4f),
    )

    /** ארכיון: רוכסן עם לשונית. */
    private val archivePaths = listOf(
        Spec("M21.4 16h2a0.6 0.6 0 0 1 0.6 0.6v1a0.6 0.6 0 0 1 -0.6 0.6h-2a0.6 0.6 0 0 1 -0.6 -0.6v-1a0.6 0.6 0 0 1 0.6 -0.6z"),
        Spec("M23.8 19h2a0.6 0.6 0 0 1 0.6 0.6v1a0.6 0.6 0 0 1 -0.6 0.6h-2a0.6 0.6 0 0 1 -0.6 -0.6v-1a0.6 0.6 0 0 1 0.6 -0.6z"),
        Spec("M21.4 22h2a0.6 0.6 0 0 1 0.6 0.6v1a0.6 0.6 0 0 1 -0.6 0.6h-2a0.6 0.6 0 0 1 -0.6 -0.6v-1a0.6 0.6 0 0 1 0.6 -0.6z"),
        Spec("M23.8 25h2a0.6 0.6 0 0 1 0.6 0.6v1a0.6 0.6 0 0 1 -0.6 0.6h-2a0.6 0.6 0 0 1 -0.6 -0.6v-1a0.6 0.6 0 0 1 0.6 -0.6z"),
        Spec("M22 28.4h3a1.8 1.8 0 0 1 1.8 1.8v5a1.8 1.8 0 0 1 -1.8 1.8h-3a1.8 1.8 0 0 1 -1.8 -1.8v-5a1.8 1.8 0 0 1 1.8 -1.8z", stroke = 2.2f),
        Spec("M23.5 32.4v1.6", stroke = 2.2f),
    )

    /** אפליקציה: ארבעה אריחים, אחד עגול. */
    private val apkPaths = listOf(
        Spec("M17.7 19.5h3a2.2 2.2 0 0 1 2.2 2.2v3a2.2 2.2 0 0 1 -2.2 2.2h-3a2.2 2.2 0 0 1 -2.2 -2.2v-3a2.2 2.2 0 0 1 2.2 -2.2z"),
        Spec("M28.8 19.5h0a3.7 3.7 0 0 1 3.7 3.7v0a3.7 3.7 0 0 1 -3.7 3.7h-0a3.7 3.7 0 0 1 -3.7 -3.7v-0a3.7 3.7 0 0 1 3.7 -3.7z"),
        Spec("M17.7 29.1h3a2.2 2.2 0 0 1 2.2 2.2v3a2.2 2.2 0 0 1 -2.2 2.2h-3a2.2 2.2 0 0 1 -2.2 -2.2v-3a2.2 2.2 0 0 1 2.2 -2.2z"),
        Spec("M27.3 29.1h3a2.2 2.2 0 0 1 2.2 2.2v3a2.2 2.2 0 0 1 -2.2 2.2h-3a2.2 2.2 0 0 1 -2.2 -2.2v-3a2.2 2.2 0 0 1 2.2 -2.2z"),
    )

    /** מוצפן: מנעול. */
    private val lockPaths = listOf(
        Spec("M19.5 25v-3a4.5 4.5 0 0 1 9 0v3", stroke = 2.4f),
        Spec("M19.1 25h9.8a2.6 2.6 0 0 1 2.6 2.6v6.3a2.6 2.6 0 0 1 -2.6 2.6h-9.8a2.6 2.6 0 0 1 -2.6 -2.6v-6.3a2.6 2.6 0 0 1 2.6 -2.6z"),
    )

    /** תג DCIM/Camera: מצלמה. */
    private val cameraPaths = listOf(
        Spec("M15 24a2 2 0 0 1 2-2h2.6l1.6-2.4h5.6l1.6 2.4H31a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H17a2 2 0 0 1-2-2zM20.4 28.3a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0z", evenOdd = true),
        Spec("M22.3 28.3a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0z"),
    )

    /** תג Download: חץ למטה. */
    private val downloadPaths = listOf(
        Spec("M24 19v11.5M19 26l5 5 5-5M17 35.5h14", stroke = 2.4f),
    )

    /** תג Recordings: מיקרופון. */
    private val micPaths = listOf(
        Spec("M24 18.5h0a3.5 3.5 0 0 1 3.5 3.5v5a3.5 3.5 0 0 1 -3.5 3.5h-0a3.5 3.5 0 0 1 -3.5 -3.5v-5a3.5 3.5 0 0 1 3.5 -3.5z"),
        Spec("M17.5 27a6.5 6.5 0 0 0 13 0M24 33.5v3", stroke = 2.4f),
    )

    /** קובץ בלי סיומת: דף עם פינה מקופלת. */
    private val filePaths = listOf(
        Spec("M18 18h7.5l5.5 5.5V36a2 2 0 0 1-2 2H18a2 2 0 0 1-2-2V20a2 2 0 0 1 2-2z", stroke = 2.2f),
        Spec("M25.5 18v5.5H31", stroke = 2.2f),
    )

    /** תיקייה: גב עם לשונית (55%) וחזית מלאה. */
    private val folderPaths = listOf(
        Spec("M5 12a4 4 0 0 1 4-4h9.3a4 4 0 0 1 2.9 1.2L24 12h15a4 4 0 0 1 4 4v8H5z", alpha = 0.55f),
        Spec("M5 19a4 4 0 0 1 4-4h30a4 4 0 0 1 4 4v18a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z"),
    )

    // ---------- הסמלים ----------

    val Folder: ImageVector by lazy { vector("folder", FolderPlace, folderPaths) }
    val Image: ImageVector by lazy { vector("image", Tile, imagePaths) }
    val Video: ImageVector by lazy { vector("video", Tile, videoPaths) }
    val Audio: ImageVector by lazy { vector("audio", Tile, audioPaths) }
    val Doc: ImageVector by lazy { vector("doc", Tile, docPaths) }
    val Text: ImageVector by lazy { vector("text", Tile, textPaths) }
    val Sheet: ImageVector by lazy { vector("sheet", Tile, sheetPaths) }
    val Slides: ImageVector by lazy { vector("slides", Tile, slidesPaths) }
    val Code: ImageVector by lazy { vector("code", Tile, codePaths) }
    val Ebook: ImageVector by lazy { vector("ebook", Tile, ebookPaths) }
    val Archive: ImageVector by lazy { vector("archive", Tile, archivePaths) }
    val Apk: ImageVector by lazy { vector("apk", Tile, apkPaths) }
    val Lock: ImageVector by lazy { vector("lock", Tile, lockPaths) }
    val File: ImageVector by lazy { vector("file", Tile, filePaths) }

    // תגים על חזית התיקייה.
    val CameraBadge: ImageVector by lazy { vector("badge-camera", BadgePlace, cameraPaths) }
    val ImageBadge: ImageVector by lazy { vector("badge-image", BadgePlace, imagePaths) }
    val DownloadBadge: ImageVector by lazy { vector("badge-download", BadgePlace, downloadPaths) }
    val AudioBadge: ImageVector by lazy { vector("badge-audio", BadgePlace, audioPaths) }
    val VideoBadge: ImageVector by lazy { vector("badge-video", BadgePlace, videoPaths) }
    val DocBadge: ImageVector by lazy { vector("badge-doc", BadgePlace, docPaths) }
    val ApkBadge: ImageVector by lazy { vector("badge-apk", BadgePlace, apkPaths) }
    val MicBadge: ImageVector by lazy { vector("badge-mic", BadgePlace, micPaths) }
    val LockBadge: ImageVector by lazy { vector("badge-lock", BadgePlace, lockPaths) }
}
