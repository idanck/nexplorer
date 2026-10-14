package com.idanck.nexplorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightPrimaryContainer,
    onSecondaryContainer = LightOnPrimaryContainer,
    tertiary = LightAmber,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightOnAmberContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightSurfaceVariant,
    error = LightError,
    onError = LightOnError,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkPrimaryContainer,
    onSecondaryContainer = DarkOnPrimaryContainer,
    tertiary = DarkAmber,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkOnAmberContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkMenuSurface,
    surfaceContainerHighest = DarkMenuSurface,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
)

/**
 * צבעים שאין להם מקום ב-ColorScheme של Material 3.
 * השימוש: `NexplorerTheme.colors.amber`.
 */
@Immutable
data class NexplorerColors(
    /** בתנועה: צ'יפ העברות, פס התקדמות, הדגשת התאמה בחיפוש. */
    val amber: Color,
    val amberContainer: Color,
    val onAmberContainer: Color,
    val success: Color,
    /** רקע של שורה נבחרת במצב בחירה. */
    val selectedRow: Color,
    /** תיקייה (או חלון) שאפשר לשחרר עליה גרירה. */
    val dropTarget: Color,
    /** מציג התמונות: רקע, טקסט ואייקונים, ופס עליון/תחתון שקוף-למחצה. */
    val viewerBackground: Color,
    val onViewer: Color,
    val viewerChrome: Color,
    /** עמעום מתחת לתפריט "חדש" ולמגירה. */
    val scrim: Color,
    val bannerGradientStart: Color,
    val bannerGradientEnd: Color,
    val bannerWaveDeep: Color,
    val bannerWaveMid: Color,
    val bannerWaveLight: Color,
    val bannerGlow: Color,
    val bannerGlowSoft: Color,
    val bannerTitle: Color,
    val bannerSubtitle: Color,
    /** פילוח התפוסה במנתח: וידאו, תמונות, אפליקציות ומערכת, מסמכים, אודיו, אחר. */
    val chartVideo: Color,
    val chartImages: Color,
    val chartApps: Color,
    val chartDocuments: Color,
    val chartAudio: Color,
    val chartOther: Color,
)

private val LightExtended = NexplorerColors(
    amber = LightAmber,
    amberContainer = LightAmberContainer,
    onAmberContainer = LightOnAmberContainer,
    success = LightSuccess,
    selectedRow = LightSelectedRow,
    dropTarget = LightDropTarget,
    viewerBackground = ViewerBackground,
    onViewer = OnViewer,
    viewerChrome = ViewerChrome,
    scrim = Color(0x99000000),
    bannerGradientStart = BannerGradientStart,
    bannerGradientEnd = BannerGradientEnd,
    bannerWaveDeep = BannerWaveDeep,
    bannerWaveMid = BannerWaveMid,
    bannerWaveLight = BannerWaveLight,
    bannerGlow = BannerGlow,
    bannerGlowSoft = BannerGlowSoft,
    bannerTitle = BannerTitle,
    bannerSubtitle = BannerSubtitle,
    chartVideo = LightChartVideo,
    chartImages = LightChartImages,
    chartApps = LightChartApps,
    chartDocuments = LightChartDocuments,
    chartAudio = LightChartAudio,
    chartOther = LightChartOther,
)

private val DarkExtended = LightExtended.copy(
    amber = DarkAmber,
    amberContainer = DarkAmberContainer,
    onAmberContainer = DarkOnAmberContainer,
    success = DarkSuccess,
    selectedRow = DarkSelectedRow,
    dropTarget = DarkDropTarget,
    chartVideo = DarkChartVideo,
    chartImages = DarkChartImages,
    chartApps = DarkChartApps,
    chartDocuments = DarkChartDocuments,
    chartAudio = DarkChartAudio,
    chartOther = DarkChartOther,
)

private val LocalNexplorerColors = staticCompositionLocalOf { LightExtended }

/**
 * ערכת הנושא של nexplorer. אין Dynamic Color — הכחול הוא חלק מהזהות ומהמשמעות
 * (כחול = ניתן לפעולה), ולכן לא מתחלף לפי הטפט.
 */
@Composable
fun NexplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNexplorerColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NexplorerTypography,
            shapes = NexplorerShapes,
            content = content,
        )
    }
}

object NexplorerTheme {
    val colors: NexplorerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNexplorerColors.current
}
