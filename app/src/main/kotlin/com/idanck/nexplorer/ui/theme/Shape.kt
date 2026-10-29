package com.idanck.nexplorer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * צורות מלוח "טוקנים ורכיבים":
 *   8  — צ'יפ                -> extraSmall
 *   16 — אריח אייקון         -> small
 *   16 — שורה מודגשת במגירה   -> medium
 *   24 — כרטיס / גיליון תחתון -> large
 *   28 — מגירה              -> extraLarge
 *   pill — כפתורים וגלולות תפריט "חדש", כותרת הבחירה וסרגל הפעולות הצפים
 *   circle — הכפתור הצף (עיגול מלא, לא ריבוע מעוגל)
 *   20 — תפריט צף (⋮)
 *   14 — אריח מקום במגירה
 *   8  — תיבת סימון במצב בחירה
 *   24/6 — רשימה מקובצת: קצוות הקבוצה / בין פריטים (1.9)
 *   20 — חלון בשני חלונות · 18 — פריט במסילה · 12 — יעד שחרור בגרירה
 *   "הוסף מקום": 16 — אריח סוג · 20 — כרטיס · 12 — שדה
 */
val NexplorerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object NexplorerShape {
    val Chip = RoundedCornerShape(8.dp)
    val IconTile = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(24.dp)

    /** משטח התוכן נפתח מעל הרקע עם פינות עליונות בלבד. */
    val ContentSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /**
     * רשימה מקובצת (1.9): כל פריט כרטיס, עם פינות 6 בין פריטים ו-24 בקצוות הקבוצה.
     * בקבוצה רגילה (Column) — [GroupOuter] על הקבוצה ו-[GroupInner] על כל פריט; ב-LazyColumn — [groupItem].
     */
    val GroupOuter = RoundedCornerShape(24.dp)
    val GroupInner = RoundedCornerShape(6.dp)
    fun groupItem(first: Boolean, last: Boolean): RoundedCornerShape {
        val outer = 24.dp
        val inner = 6.dp
        return RoundedCornerShape(
            topStart = if (first) outer else inner,
            topEnd = if (first) outer else inner,
            bottomStart = if (last) outer else inner,
            bottomEnd = if (last) outer else inner,
        )
    }

    /** כפתור נגן/השהה בנגן השמע: מלבן עם פינות 28 (לא עיגול). */
    val PlayButton = RoundedCornerShape(28.dp)

    /** מגירה נפתחת מצד ההתחלה (ימין ב-RTL) — פינות מעוגלות בצד הסוף. */
    val Drawer = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

    /** תפריט צף (⋮ לפריט, תפריט overflow). */
    val Menu = RoundedCornerShape(20.dp)

    /** חלון "אפשרויות תצוגה". */
    val Popover = RoundedCornerShape(24.dp)

    val PlaceTile = RoundedCornerShape(14.dp)
    val Checkbox = RoundedCornerShape(8.dp)

    /** כרטיסי "מה אפשר לפנות" ו"תיקיות גדולות" במנתח, ונקודת המקרא של הפילוח. */
    val FindingCard = RoundedCornerShape(20.dp)
    val LegendDot = RoundedCornerShape(3.dp)

    /** שני חלונות (לוח Tablet). */
    val Pane = RoundedCornerShape(20.dp)
    val RailItem = RoundedCornerShape(18.dp)
    val DropTarget = RoundedCornerShape(12.dp)

    /** "הוסף מקום" (לוח "חיבור חדש"). */
    val ConnectTypeTile = RoundedCornerShape(16.dp)
    val ConnectCard = RoundedCornerShape(20.dp)
    val ConnectField = RoundedCornerShape(12.dp)

    val Pill = CircleShape
    val Fab = CircleShape
}
