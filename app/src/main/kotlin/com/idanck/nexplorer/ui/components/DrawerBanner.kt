package com.idanck.nexplorer.ui.components

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idanck.nexplorer.ui.theme.NexplorerColors
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerTheme

/**
 * הבאנר בראש מגירת המקומות: מעבר צבע כחול עם גלים ועיגולים בכמה גוונים,
 * שם האפליקציה, ושם המכשיר + גרסת אנדרואיד. זהה במצב בהיר וכהה.
 * עיצוב: לוח "מגירת מקומות — באנר" (docs/design/screens/Drawer.webp).
 */
@Composable
fun DrawerBanner(
    onSettings: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    deviceName: String = rememberDeviceName(),
) {
    val c = NexplorerTheme.colors
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NexplorerDimens.DrawerBannerHeight)
            .background(
                Brush.linearGradient(
                    colors = listOf(c.bannerGradientStart, c.bannerGradientEnd),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawBannerShapes(c) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 14.dp, top = 24.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(c.bannerTitle.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = c.bannerTitle, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "nexplorer",
                    color = c.bannerTitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Medium,
                        textDirection = TextDirection.Ltr,
                    ),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isTablet) Icons.Outlined.TabletAndroid else Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = c.bannerSubtitle,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = deviceName,
                    color = c.bannerSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, textDirection = TextDirection.Ltr),
                    maxLines = 1,
                )
                Text(
                    text = "·",
                    color = c.bannerSubtitle.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
                Text(
                    text = "Android ${Build.VERSION.RELEASE}",
                    color = c.bannerSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "הגדרות", tint = c.bannerTitle)
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "עוד", tint = c.bannerTitle)
                }
            }
        }
    }
}

/**
 * שם המכשיר כפי שהמשתמש קבע בהגדרות ("שם המכשיר"), ואם ריק — דגם המכשיר.
 * כך בטאבלט מופיע שם הטאבלט ובטלפון שם הטלפון, בלי הגדרה נוספת.
 */
@Composable
fun rememberDeviceName(): String {
    val context = LocalContext.current
    return remember(context) { deviceName(context) }
}

fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

/** הצורות של הבאנר, מתורגמות מ-viewBox של 312×168 בלוח העיצוב. */
private fun DrawScope.drawBannerShapes(c: NexplorerColors) {
    val sx = size.width / 312f
    val sy = size.height / 168f
    fun p(x: Float, y: Float) = Offset(x * sx, y * sy)

    // גל תחתון כהה
    drawPath(
        Path().apply {
            moveTo(p(-10f, 128f).x, p(-10f, 128f).y)
            cubicTo(p(50f, 70f).x, p(50f, 70f).y, p(120f, 160f).x, p(120f, 160f).y, p(200f, 100f).x, p(200f, 100f).y)
            cubicTo(p(280f, 40f).x, p(280f, 40f).y, p(290f, 40f).x, p(290f, 40f).y, p(322f, 80f).x, p(322f, 80f).y)
            lineTo(p(322f, 168f).x, p(322f, 168f).y)
            lineTo(p(-10f, 168f).x, p(-10f, 168f).y)
            close()
        },
        color = c.bannerWaveDeep,
        alpha = 0.85f,
    )
    // גל עליון בהיר
    drawPath(
        Path().apply {
            moveTo(p(-10f, 46f).x, p(-10f, 46f).y)
            cubicTo(p(60f, -10f).x, p(60f, -10f).y, p(150f, 96f).x, p(150f, 96f).y, p(230f, 36f).x, p(230f, 36f).y)
            cubicTo(p(310f, -24f).x, p(310f, -24f).y, p(300f, 6f).x, p(300f, 6f).y, p(322f, 44f).x, p(322f, 44f).y)
            lineTo(p(322f, -10f).x, p(322f, -10f).y)
            lineTo(p(-10f, -10f).x, p(-10f, -10f).y)
            close()
        },
        color = c.bannerWaveLight,
        alpha = 0.5f,
    )
    drawCircle(c.bannerGlow, radius = 74f * sx, center = p(262f, 34f), alpha = 0.22f)
    // גל אמצעי
    drawPath(
        Path().apply {
            moveTo(p(110f, 168f).x, p(110f, 168f).y)
            cubicTo(p(170f, 118f).x, p(170f, 118f).y, p(240f, 150f).x, p(240f, 150f).y, p(322f, 116f).x, p(322f, 116f).y)
            lineTo(p(322f, 168f).x, p(322f, 168f).y)
            close()
        },
        color = c.bannerWaveMid,
        alpha = 0.9f,
    )
    drawCircle(c.bannerGlowSoft, radius = 46f * sx, center = p(40f, 150f), alpha = 0.18f)
}
