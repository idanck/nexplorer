package com.idanck.nexplorer.core

enum class CaseChange(val label: String) { None("כמו שהוא"), Lower("אותיות קטנות"), Upper("אותיות גדולות") }

/**
 * תבנית לשינוי שם מרובה. `{n}` בכל אחד מהשדות = מספר רץ, מרופד לרוחב של המספר הגדול ביותר
 * (‎01…12‎). הסיומת נשמרת כברירת מחדל — שינוי שלה הופך קובץ ל"לא נפתח".
 */
data class RenamePattern(
    val find: String = "",
    val replace: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val startAt: Int = 1,
    val case: CaseChange = CaseChange.None,
    val keepExtension: Boolean = true,
    val matchCase: Boolean = false,
) {
    val isEmpty: Boolean get() = find.isEmpty() && prefix.isEmpty() && suffix.isEmpty() && case == CaseChange.None
}

object BatchRename {

    /** [problem] != null — השורה לא תבוצע (והחלון מסמן אותה); [to] == [from] — אין מה לשנות. */
    data class Planned(val from: String, val to: String, val problem: String?) {
        val changes: Boolean get() = problem == null && to != from
    }

    /**
     * [names] בסדר שבו הם מוצגים (זה הסדר של המספור). [others] — שמות אחרים בתיקייה, שלא משנים להם שם.
     * ההשוואה בלי תלות ברישיות: האחסון המשותף באנדרואיד לא מבדיל בין `A.jpg` ל-`a.jpg`.
     */
    fun plan(names: List<String>, pattern: RenamePattern, others: Collection<String>): List<Planned> {
        val last = pattern.startAt + names.size - 1
        val width = maxOf(last.toString().length, pattern.startAt.toString().length)
        val proposed = names.mapIndexed { i, name -> name to apply(name, pattern, (pattern.startAt + i).toString().padStart(width, '0')) }

        val taken = others.map { it.lowercase() }.toSet()
        val counts = proposed.groupingBy { it.second.lowercase() }.eachCount()
        return proposed.map { (from, to) ->
            val problem = when {
                to == from -> null
                FileNames.validate(to) != null -> FileNames.validate(to)
                (counts[to.lowercase()] ?: 0) > 1 -> "שני קבצים יקבלו את אותו שם"
                to.lowercase() in taken -> "כבר קיים פריט בשם הזה"
                else -> null
            }
            Planned(from, to, problem)
        }
    }

    fun apply(name: String, pattern: RenamePattern, number: String): String {
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (pattern.keepExtension && dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var next = if (pattern.find.isEmpty()) base else base.replace(pattern.find, pattern.replace, ignoreCase = !pattern.matchCase)
        next = pattern.prefix + next + pattern.suffix
        next = next.replace(NUMBER_TOKEN, number)
        next = when (pattern.case) {
            CaseChange.None -> next
            CaseChange.Lower -> next.lowercase()
            CaseChange.Upper -> next.uppercase()
        }
        return (next + ext).trim()
    }

    const val NUMBER_TOKEN = "{n}"
}
