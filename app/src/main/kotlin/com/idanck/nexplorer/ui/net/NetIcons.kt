package com.idanck.nexplorer.ui.net

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.idanck.nexplorer.core.NetProtocol

/** האייקון של חיבור במגירה ובמסילה — אותו אייקון כמו האריח שלו במסך "הוסף מקום". */
val NetProtocol.icon: ImageVector
    get() = when (this) {
        NetProtocol.Smb -> Icons.Outlined.Dns
        NetProtocol.Sftp -> Icons.Outlined.Terminal
        NetProtocol.WebDav -> Icons.Outlined.Language
    }
