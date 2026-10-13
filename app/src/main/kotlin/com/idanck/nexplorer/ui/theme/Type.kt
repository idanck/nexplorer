package com.idanck.nexplorer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.idanck.nexplorer.R

/**
 * Noto Sans Hebrew — פונט הממשק (עברית + לטינית בסיסית).
 * נגזר מהפונט המשתנה של Google Fonts ל-4 משקלים סטטיים (wdth=100). רישיון: OFL.
 */
val NotoSansHebrew = FontFamily(
    Font(R.font.noto_sans_hebrew_regular, FontWeight.Normal),
    Font(R.font.noto_sans_hebrew_medium, FontWeight.Medium),
    Font(R.font.noto_sans_hebrew_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_hebrew_bold, FontWeight.Bold),
)

/** JetBrains Mono — נתיבים, גדלים, hash, כתובות שרת. רישיון: OFL. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/*
 * סולם הטיפוגרפיה מלוח "טוקנים ורכיבים":
 *   כותרת מסך          700 · 32/36  -> headlineLarge
 *   כותרת סרגל          600 · 20/24  -> titleLarge
 *   שם קובץ ותפריטים   500 · 16/20  -> titleMedium, bodyLarge
 *   שורה משנית          400 · 13/16  -> bodySmall
 *   כותרת מדור          600 · 12/16 · +4%  -> labelMedium
 * שאר הסגנונות משלימים את הסולם של Material 3 באותו פונט.
 */
private fun noto(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = NotoSansHebrew,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val NexplorerTypography = Typography(
    displayLarge = noto(FontWeight.Normal, 57, 64),
    displayMedium = noto(FontWeight.Normal, 45, 52),
    displaySmall = noto(FontWeight.Normal, 36, 44),
    headlineLarge = noto(FontWeight.Bold, 32, 36),
    headlineMedium = noto(FontWeight.Bold, 28, 34),
    headlineSmall = noto(FontWeight.SemiBold, 24, 30),
    titleLarge = noto(FontWeight.SemiBold, 20, 24),
    titleMedium = noto(FontWeight.Medium, 16, 20),
    titleSmall = noto(FontWeight.Medium, 14, 20),
    bodyLarge = noto(FontWeight.Medium, 16, 20),
    bodyMedium = noto(FontWeight.Normal, 14, 20),
    bodySmall = noto(FontWeight.Normal, 13, 16),
    labelLarge = noto(FontWeight.SemiBold, 14, 20),
    labelMedium = noto(FontWeight.SemiBold, 12, 16).copy(letterSpacing = 0.04.em),
    labelSmall = noto(FontWeight.Medium, 11, 16),
)

/**
 * סגנון ערכים טכניים: נתיבים, גדלים, hash.
 * תמיד LTR — כלל BiDi: שמות קבצים, נתיבים ומספרים-עם-יחידות מבודדים.
 */
val MonoTextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    textDirection = TextDirection.Ltr,
)

/** "nexplorer" בבאנר המגירה: 500 · 34/38, תמיד LTR. */
val BannerTitleTextStyle = NexplorerTypography.headlineLarge.copy(
    fontSize = 34.sp,
    lineHeight = 38.sp,
    fontWeight = FontWeight.Medium,
    textDirection = TextDirection.Ltr,
)

/**
 * המספר הגדול בכרטיס הסיכום של מנתח האחסון ("133"): mono 700 · 32/36, LTR.
 * JetBrains Mono ארוז רק ב-400/500 — ה-700 מסונתז (FontSynthesis), וזה מה שהלוח מראה.
 */
val UsageFigureTextStyle = MonoTextStyle.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)

/** שם קובץ בשורת רשימה: פונט הממשק, כיוון LTR. */
val FileNameTextStyle = NexplorerTypography.bodyLarge.copy(textDirection = TextDirection.Ltr)
