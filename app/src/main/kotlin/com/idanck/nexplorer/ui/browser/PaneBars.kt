package com.idanck.nexplorer.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.idanck.nexplorer.ui.theme.MonoTextStyle
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/** כותרת חלון (לוח Tablet): פירורי לחם, מספר הפריטים, חיפוש ו-⋮. */
@Composable
fun PaneHeader(
    crumbs: List<Crumb>,
    countText: String,
    onCrumb: (Crumb) -> Unit,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    moreMenu: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = NexplorerDimens.PanePaddingHorizontal, top = NexplorerDimens.Space1, bottom = NexplorerDimens.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Breadcrumbs(
                crumbs,
                onCrumb,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = NexplorerDimens.Space2),
            )
            Spacer(Modifier.width(NexplorerDimens.Space2))
            Text(
                countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "חיפוש") }
            Box {
                IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, contentDescription = "עוד פעולות") }
                moreMenu()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/**
 * תחתית החלון במצב בחירה: "N נבחרו", הגודל, "העתק לחלון השני" ו"העבר".
 * [towardEnd] — החלון השני בצד הסוף (שמאל ב-RTL), והחץ מצביע אליו.
 */
@Composable
fun PaneSelectionFooter(
    count: Int,
    sizeText: String,
    towardEnd: Boolean,
    blockedReason: String?,
    onCopy: () -> Unit,
    onMove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NexplorerDimens.PaneFooterHeight)
            .background(scheme.primaryContainer)
            .padding(horizontal = NexplorerDimens.PanePaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexplorerDimens.Space2),
    ) {
        Text("$count נבחרו", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer, maxLines = 1)
        if (sizeText.isNotEmpty()) {
            Text(sizeText, style = MonoTextStyle, color = scheme.onPrimaryContainer, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (blockedReason != null) {
            Text(
                blockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Button(
                onClick = onCopy,
                shape = NexplorerShape.Pill,
                contentPadding = PaddingValues(horizontal = NexplorerDimens.PanePaddingHorizontal),
                modifier = Modifier.height(NexplorerDimens.PaneButtonHeight),
            ) {
                // ArrowForward משוקף ב-RTL מצביע שמאלה — לסוף; ArrowBack — לימין, להתחלה.
                Icon(
                    if (towardEnd) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(NexplorerDimens.Space4),
                )
                Spacer(Modifier.width(NexplorerDimens.Space1 + NexplorerDimens.Space1 / 2))
                Text("העתק לחלון השני", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Button(
                onClick = onMove,
                shape = NexplorerShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.onPrimaryContainer.copy(alpha = MOVE_BUTTON_ALPHA),
                    contentColor = scheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = NexplorerDimens.PanePaddingHorizontal),
                modifier = Modifier.height(NexplorerDimens.PaneButtonHeight),
            ) {
                Text("העבר", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** הרקע של "העבר" בלוח: onPrimaryContainer ב-12%. */
private const val MOVE_BUTTON_ALPHA = 0.12f

/** שורת הרמז בתחתית החלון השני. */
@Composable
fun PaneHint() {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            "גרירה בין החלונות · לחיצה ארוכה לטווח · קיצורי מקלדת: F5 העתק, F6 העבר, Del מחק",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = NexplorerDimens.PanePaddingHorizontal, vertical = NexplorerDimens.Space3),
        )
    }
}
