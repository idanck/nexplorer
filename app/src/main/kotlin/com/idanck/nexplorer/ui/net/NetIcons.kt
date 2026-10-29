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
import com.idanck.nexplorer.R
import com.idanck.nexplorer.core.NetProtocol
import com.idanck.nexplorer.ui.theme.NexplorerDimens

/** האייקון של חיבור במגירה ובמסילה — אותו אייקון כמו האריח שלו במסך "הוסף מקום". */
val NetProtocol.icon: ImageVector
    get() = when (this) {
        NetProtocol.Smb -> Icons.Outlined.Dns
        NetProtocol.Sftp -> Icons.Outlined.Terminal
        NetProtocol.WebDav -> Icons.Outlined.Language
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
