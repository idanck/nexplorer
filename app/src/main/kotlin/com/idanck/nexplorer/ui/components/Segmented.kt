package com.idanck.nexplorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.idanck.nexplorer.ui.theme.NexplorerDimens
import com.idanck.nexplorer.ui.theme.NexplorerShape

/**
 * קבוצה של כרטיסים צמודים (רשימה מקובצת, 1.9): פריטים עם רווח של 3 ביניהם, פינות 6 בין פריט לפריט
 * ו-24 בקצוות הקבוצה. הקבוצה עצמה חותכת ב-24, ולכן הפריט הראשון והאחרון מקבלים את הפינות הגדולות
 * בלי לדעת את מקומם. כל פריט בתוכה: [segment].
 */
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(NexplorerShape.GroupOuter),
        verticalArrangement = Arrangement.spacedBy(NexplorerDimens.GroupGap),
        content = content,
    )
}

/** פריט בקבוצה: כרטיס בצבע [color] (בדרך כלל `colorScheme.surface`; פריט פעיל — primaryContainer), פינות 6. */
fun Modifier.segment(color: Color): Modifier =
    this.fillMaxWidth().clip(NexplorerShape.GroupInner).background(color)
