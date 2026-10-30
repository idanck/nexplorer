package com.idanck.nexplorer.ui.net

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import com.idanck.nexplorer.R
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.ui.theme.NexplorerDimens

/** האייקון של חיבור במגירה ובמסילה — אותו אייקון כמו האריח שלו במסך "הוסף מקום". */
val NetProtocol.icon: ImageVector
    get() = when (this) {
        NetProtocol.Smb -> Icons.Outlined.Dns
        NetProtocol.Sftp -> Icons.Outlined.Terminal
        NetProtocol.WebDav -> Icons.Outlined.Language
        NetProtocol.Synology -> NasIcon
        NetProtocol.GoogleDrive, NetProtocol.OneDrive -> Icons.Outlined.Cloud
    }

/**
 * ל-Google Drive יש אייקון בצבעים משלו (`R.drawable.ic_google_drive`, 1.10) — מוצג בלי צביעה, ולכן תמיד על
 * אריח בהיר-ניטרלי ולא על primary.
 */
val NetProtocol.hasOwnIcon: Boolean get() = this == NetProtocol.GoogleDrive

/** האייקון של חיבור: ב-Google Drive — הקובץ ic_google_drive; בשאר — סמל הקו של האריח, בצבע [tint]. */
@Composable
fun ConnectionIcon(protocol: NetProtocol, modifier: Modifier = Modifier, size: Dp = NexplorerDimens.TileGlyph, tint: Color = LocalContentColor.current) {
    if (protocol.hasOwnIcon) {
        Image(painterResource(R.drawable.ic_google_drive), contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(protocol.icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}

/**
 * Synology (שלב 7): מארז NAS — גוף מעוגל, שתי שורות ונורית. קו 2 כמו סמלי ה-Outlined של שאר האריחים; לא הלוגו של
 * Synology.
 */
val NasIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Nas", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("M7.5 3h9A2.5 2.5 0 0 1 19 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18.5v-13A2.5 2.5 0 0 1 7.5 3zM9 8h6M9 12h6").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = PathParser().parsePathString("M12 15.8a1.2 1.2 0 1 1 0 2.4a1.2 1.2 0 1 1 0-2.4z").toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
