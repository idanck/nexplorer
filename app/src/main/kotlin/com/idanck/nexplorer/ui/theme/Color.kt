package com.idanck.nexplorer.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * פלטת "סלע ואות". מקור האמת: docs/design/tokens.json.
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
internal val LightBackground = Color(0xFFF3F5F7)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFE6EAEE)
internal val LightOutline = Color(0xFFC7CED5)
internal val LightOnSurface = Color(0xFF16202A)
internal val LightOnSurfaceVariant = Color(0xFF52606D)
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)

internal val LightAmber = Color(0xFFB4640B)
internal val LightAmberContainer = Color(0xFFFBE7C9)
internal val LightOnAmberContainer = Color(0xFF5C3203)
internal val LightSuccess = Color(0xFF2E7D32)
internal val LightSelectedRow = Color(0xFFEEF3FF)
/** יעד שחרור בגרירה (לוח Tablet): מילוי השורה; המסגרת המקווקוות ב-amber והטקסט ב-onAmberContainer. */
internal val LightDropTarget = Color(0xFFFFF7EA)

// ---------- כהה ----------
internal val DarkPrimary = Color(0xFF7FB0FF)
internal val DarkOnPrimary = Color(0xFF002B6B)
internal val DarkPrimaryContainer = Color(0xFF1C3F7A)
internal val DarkOnPrimaryContainer = Color(0xFFD5E3FE)
internal val DarkBackground = Color(0xFF0F1417)
internal val DarkSurface = Color(0xFF161D22)
internal val DarkSurfaceVariant = Color(0xFF232C33)
internal val DarkOutline = Color(0xFF3A454E)
internal val DarkOnSurface = Color(0xFFE6ECF1)
internal val DarkOnSurfaceVariant = Color(0xFF9AA7B2)

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
