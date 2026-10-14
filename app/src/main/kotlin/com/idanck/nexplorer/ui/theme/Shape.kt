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
 *   pill — כפתורים וגלולות תפריט "חדש", כותרת הבחירה וסרגל הפעולות הצפים
 *   circle — הכפתור הצף (עיגול מלא, לא ריבוע מעוגל)
 *   20 — תפריט צף (⋮)
 *   12 — אריח מקום במגירה
 *   7  — תיבת סימון במצב בחירה
 *   20 — חלון בשני חלונות · 18 — פריט במסילה · 12 — יעד שחרור בגרירה
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

    /** תפריט צף (⋮ לפריט, תפריט overflow). */
    val Menu = RoundedCornerShape(20.dp)

    /** חלון "אפשרויות תצוגה". */
    val Popover = RoundedCornerShape(24.dp)

    val PlaceTile = RoundedCornerShape(12.dp)
    val Checkbox = RoundedCornerShape(7.dp)

    /** כרטיסי "מה אפשר לפנות" ו"תיקיות גדולות" במנתח, ונקודת המקרא של הפילוח. */
    val FindingCard = RoundedCornerShape(20.dp)
    val LegendDot = RoundedCornerShape(3.dp)

    /** שני חלונות (לוח Tablet). */
    val Pane = RoundedCornerShape(20.dp)
    val RailItem = RoundedCornerShape(18.dp)
    val DropTarget = RoundedCornerShape(12.dp)

    val Pill = CircleShape
    val Fab = CircleShape
}
