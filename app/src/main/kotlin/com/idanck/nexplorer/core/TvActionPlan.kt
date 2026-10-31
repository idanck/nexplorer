package com.idanck.nexplorer.core

/** פעולה בפאנל הפעולות של Google TV (לוח T3) ובסרגל הבחירה (T4). */
enum class TvOp(val group: Int) {
    Open(0),
    OpenWith(0),
    Select(0),
    Copy(1),
    Cut(1),
    Rename(1),
    Properties(1),
    Delete(2),
}

/**
 * אילו פעולות מופיעות בפאנל של הטלוויזיה (1.15). בלי תלות באנדרואיד — נבדק ב-JVM. הקבוצות ([TvOp.group]) מופרדות
 * בקו; פעולות שהמסך מוסיף (כמו "שלח למכשיר") נכנסות אחרי "גזור".
 */
object TvActionPlan {

    /** [entries] — הפריט שבפוקוס, או הבחירה כולה. [inSelection] — כבר במצב בחירה ("בחר כמה" לא מופיע). */
    fun ops(entries: List<Entry>, inSelection: Boolean): List<TvOp> {
        if (entries.isEmpty()) return emptyList()
        val single = entries.singleOrNull()
        return buildList {
            if (single != null) {
                add(TvOp.Open)
                if (!single.isDirectory) add(TvOp.OpenWith)
                if (!inSelection) add(TvOp.Select)
            }
            add(TvOp.Copy)
            add(TvOp.Cut)
            if (single != null) add(TvOp.Rename)
            add(TvOp.Properties)
            add(TvOp.Delete)
        }
    }

    /** "נגן" לשמע ולווידאו שנפתחים בנגן המובנה; "פתח" לכל השאר. */
    fun openLabel(entry: Entry): String = if (MediaFormats.playable(entry)) "נגן" else "פתח"

    /** סרגל הבחירה בתחתית (T4): בלי "פתח" ו"שנה שם" — אלה בפאנל. */
    val toolbar: List<TvOp> = listOf(TvOp.Copy, TvOp.Cut, TvOp.Delete)
}
