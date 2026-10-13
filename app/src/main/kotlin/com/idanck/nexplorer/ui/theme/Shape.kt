package com.idanck.nexplorer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * צורות מלוח "טוקנים ורכיבים":
 *   8  — צ'יפ                -> extraSmall
 *   14 — אריח אייקון         -> small
 *   16 — שורה מודגשת במגירה   -> medium
 *   24 — כרטיס / גיליון תחתון -> large
 *   28 — מגירה              -> extraLarge
 *   pill — כפתורים וגלולות תפריט "חדש"
 *   circle — הכפתור הצף (עיגול מלא, לא ריבוע מעוגל)
 */
val NexplorerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object NexplorerShape {
    val Chip = RoundedCornerShape(8.dp)
    val IconTile = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(24.dp)

    /** משטח התוכן נפתח מעל הרקע עם פינות עליונות בלבד. */
    val ContentSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** מגירה נפתחת מצד ההתחלה (ימין ב-RTL) — פינות מעוגלות בצד הסוף. */
    val Drawer = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

    val Pill = CircleShape
    val Fab = CircleShape
}
