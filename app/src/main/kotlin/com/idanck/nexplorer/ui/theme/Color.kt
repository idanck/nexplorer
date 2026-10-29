package com.idanck.nexplorer.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * פלטת "סלע ואות" (1.9: כיוון Material 3 Expressive — רקע טונאלי, רשימות מקובצות). מקור האמת: docs/design/tokens.json.
 * שינוי כאן מחייב שינוי שם (ולהפך).
 *
 * משמעות הצבעים — בלי יוצאים מהכלל:
 *   כחול (primary) = ניתן לפעולה
 *   ענבר (amber)   = בתנועה (העברות, משימות רקע)
 *   אדום (error)   = בלתי הפיך (מחיקה, דריסה)
 */

// ---------- בהיר ----------
internal val LightPrimary = Color(0xFF2B6FEE)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFD5E3FE)
internal val LightOnPrimaryContainer = Color(0xFF1E4DA8)
internal val LightBackground = Color(0xFFF4F6FB)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFECF0F7)
/** סרגל החיפוש (גלולה) בראש המסך הראשי. */
internal val LightSearchBar = Color(0xFFE4E9F2)
internal val LightOutlineVariant = Color(0xFFDDE3EC)
internal val LightOutline = Color(0xFFC3CBD6)
internal val LightOnSurface = Color(0xFF141B24)
internal val LightOnSurfaceVariant = Color(0xFF4F5B69)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)

internal val LightAmber = Color(0xFFB4640B)
internal val LightAmberContainer = Color(0xFFFBE7C9)
internal val LightOnAmberContainer = Color(0xFF5C3203)
internal val LightSuccess = Color(0xFF2E7D32)
internal val LightSelectedRow = Color(0xFFE3ECFF)
/** יעד שחרור בגרירה (לוח Tablet): מילוי השורה; המסגרת המקווקוות ב-amber והטקסט ב-onAmberContainer. */
internal val LightDropTarget = Color(0xFFFFF7EA)

// ---------- כהה ----------
internal val DarkPrimary = Color(0xFF7FB0FF)
internal val DarkOnPrimary = Color(0xFF002B6B)
internal val DarkPrimaryContainer = Color(0xFF1C3F7A)
internal val DarkOnPrimaryContainer = Color(0xFFD5E3FE)
internal val DarkBackground = Color(0xFF0E1215)
internal val DarkSurface = Color(0xFF171D22)
internal val DarkSurfaceVariant = Color(0xFF1F262D)
internal val DarkSearchBar = Color(0xFF283039)
internal val DarkOutlineVariant = Color(0xFF283039)
internal val DarkOutline = Color(0xFF3A454E)
internal val DarkOnSurface = Color(0xFFE6ECF1)
internal val DarkOnSurfaceVariant = Color(0xFFA3AFBA)

/** משטח של תפריטים צפים במצב כהה (תפריט ⋮, תת-תפריט). */
internal val DarkMenuSurface = Color(0xFF232C33)

internal val DarkAmber = Color(0xFFF2B35B)
internal val DarkAmberContainer = Color(0xFF3D2A0E)
internal val DarkOnAmberContainer = Color(0xFFF2B35B)

// לא עוצבו עדיין למצב כהה — ערכים זמניים. ראה docs/design/design-language.md, "פתוח".
internal val DarkError = Color(0xFFF2B8B5)
internal val DarkOnError = Color(0xFF601410)
internal val DarkSuccess = Color(0xFF81C995)
internal val DarkSelectedRow = Color(0xFF1A2A40)
internal val DarkDropTarget = Color(0xFF2A1F0E)

// ---------- פילוח התפוסה במנתח האחסון (לוח Analyzer) ----------
internal val LightChartVideo = Color(0xFF2B6FEE)
internal val LightChartImages = Color(0xFF7FB0FF)
internal val LightChartApps = Color(0xFFB4640B)
internal val LightChartDocuments = Color(0xFF8B98A5)
internal val LightChartAudio = Color(0xFFC7CED5)
internal val LightChartOther = Color(0xFFEEF1F4)
// לא עוצבו עדיין למצב כהה — ערכים זמניים (אותו סדר בהירות, על רקע כהה).
internal val DarkChartVideo = Color(0xFF7FB0FF)
internal val DarkChartImages = Color(0xFF3B7FF5)
internal val DarkChartApps = Color(0xFFF2B35B)
internal val DarkChartDocuments = Color(0xFF6B7885)
internal val DarkChartAudio = Color(0xFF4A5661)
internal val DarkChartOther = Color(0xFF2C363E)

// ---------- אריחי סוגי קבצים (1.10) ----------
// לכל סוג גוון משלו: מיכל טונאלי (90 בבהיר, 15–20 בכהה) וסמל מלא (700–800 בבהיר, 80 בכהה). ניגודיות הסמל
// מול המיכל — 5:1 לפחות בשתי הערכות (נדרש 3:1 לאייקון שנושא משמעות). התיקייה — primaryContainer;
// טקסט, קוד, מוצפן ואחר — surfaceVariant. הצבע לא לבד: לכל סוג גם סמל משלו (docs/design/design-language.md, §2).
internal val LightImageTile = Color(0xFFFCDDEB)
internal val LightOnImageTile = Color(0xFFA3195B)
internal val LightVideoTile = Color(0xFFE9DDFB)
internal val LightOnVideoTile = Color(0xFF5B2EA6)
internal val LightAudioTile = Color(0xFFFFE0CF)
internal val LightOnAudioTile = Color(0xFFA1400A)
internal val LightPdfTile = Color(0xFFFBDCD9)
internal val LightOnPdfTile = Color(0xFFB3261E)
internal val LightDocTile = Color(0xFFD0EEF3)
internal val LightOnDocTile = Color(0xFF00626D)
internal val LightSheetTile = Color(0xFFD4F0DA)
internal val LightOnSheetTile = Color(0xFF1B6B33)
internal val LightSlidesTile = Color(0xFFF8EAB2)
internal val LightOnSlidesTile = Color(0xFF6E5700)
internal val LightArchiveTile = Color(0xFFEFE1D4)
internal val LightOnArchiveTile = Color(0xFF6F4B2E)
internal val LightApkTile = Color(0xFFE3F1C6)
internal val LightOnApkTile = Color(0xFF3F5B00)

internal val DarkImageTile = Color(0xFF43192D)
internal val DarkOnImageTile = Color(0xFFF9AFCF)
internal val DarkVideoTile = Color(0xFF2F2148)
internal val DarkOnVideoTile = Color(0xFFCDB6F7)
internal val DarkAudioTile = Color(0xFF432313)
internal val DarkOnAudioTile = Color(0xFFFFB68F)
internal val DarkPdfTile = Color(0xFF45201D)
internal val DarkOnPdfTile = Color(0xFFF2B8B5)
internal val DarkDocTile = Color(0xFF0F3438)
internal val DarkOnDocTile = Color(0xFF7FD4DE)
internal val DarkSheetTile = Color(0xFF16331E)
internal val DarkOnSheetTile = Color(0xFF8FD6A2)
internal val DarkSlidesTile = Color(0xFF352D0B)
internal val DarkOnSlidesTile = Color(0xFFE9D06B)
internal val DarkArchiveTile = Color(0xFF35281D)
internal val DarkOnArchiveTile = Color(0xFFDDBC9C)
internal val DarkApkTile = Color(0xFF27310F)
internal val DarkOnApkTile = Color(0xFFBBD97C)

// ---------- באנר המגירה (זהה בבהיר ובכהה) ----------
internal val BannerGradientStart = Color(0xFF1A47A0)
internal val BannerGradientEnd = Color(0xFF2B6FEE)
internal val BannerWaveDeep = Color(0xFF163D8A)
internal val BannerWaveMid = Color(0xFF2457C4)
internal val BannerWaveLight = Color(0xFF3B7FF5)
internal val BannerGlow = Color(0xFF5C94F7)
internal val BannerGlowSoft = Color(0xFF7FB0FF)
internal val BannerTitle = Color(0xFFD5E3FE)
internal val BannerSubtitle = Color(0xFFC4D8FF)

// ---------- מציג התמונות: רקע כהה בשתי הערכות, כמו בכל מציג ----------
internal val ViewerBackground = Color(0xFF000000)
internal val OnViewer = Color(0xFFFFFFFF)
internal val ViewerChrome = Color(0x99000000)

/** צל מתחת לטקסט ולאייקונים של נגן הווידאו — הסרגלים שקופים לגמרי, והצל שומר על קריאות על סרטון בהיר. */
internal val PlayerGlyphShadow = Color(0xB3000000)
/** פס ההתקדמות בנגן הווידאו: המסלול (לבן שקוף) מתחת לחלק שנוגן (primary הכהה, בולט על כל סרטון). */
internal val PlayerTrack = Color(0x59FFFFFF)
internal val PlayerProgress = Color(0xFF7FB0FF)
/** גלי ההקשה הכפולה ("+10 שנ׳"). */
internal val PlayerRipple = Color(0x24FFFFFF)
