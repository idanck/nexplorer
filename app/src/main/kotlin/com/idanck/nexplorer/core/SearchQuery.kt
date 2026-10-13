package com.idanck.nexplorer.core

/**
 * תחביר החיפוש (לוח "חיפוש"): `*` כל רצף · `?` תו אחד · `a | b` או · `!x` בלי.
 *
 * מילים מופרדות ברווח הן "וגם". מילה בלי תו כללי מחפשת הכלה ("router" ⊂ "my-router.cfg");
 * מילה עם תו כללי מעוגנת לשם כולו, כמו glob ("router*" = מתחיל ב-router, "*.jpg" = נגמר ב-.jpg).
 * בלי רישיות.
 */
class SearchQuery private constructor(private val alternatives: List<Alternative>) {

    private class Term(val raw: String, val negated: Boolean) {
        val hasWildcard = raw.any { it == '*' || it == '?' }
        val regex: Regex? = if (hasWildcard) globToRegex(raw) else null

        fun matches(name: String): Boolean =
            regex?.matches(name) ?: name.contains(raw, ignoreCase = true)

        /** הקטעים המילוליים של המילה, לסימון בענבר. */
        val literals: List<String> = raw.split('*', '?').filter { it.isNotEmpty() }
    }

    private class Alternative(val terms: List<Term>) {
        fun matches(name: String): Boolean = terms.all { it.matches(name) != it.negated }
    }

    val isEmpty: Boolean get() = alternatives.isEmpty()

    fun matches(name: String): Boolean = alternatives.any { it.matches(name) }

    /**
     * טווחי ההתאמה בשם, ממוזגים וממוינים. רק מילים חיוביות מסומנות — אין מה להדגיש ב-"בלי".
     */
    fun highlights(name: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        for (alternative in alternatives) {
            if (!alternative.matches(name)) continue
            for (term in alternative.terms) {
                if (term.negated) continue
                var from = 0
                for (literal in term.literals) {
                    val at = name.indexOf(literal, from, ignoreCase = true)
                    if (at < 0) break
                    ranges += at until at + literal.length
                    from = at + literal.length
                }
            }
        }
        return merge(ranges)
    }

    companion object {
        fun parse(text: String): SearchQuery {
            val alternatives = text.split('|').mapNotNull { part ->
                val terms = part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { word ->
                    val negated = word.startsWith("!")
                    val raw = if (negated) word.drop(1) else word
                    if (raw.isEmpty()) null else Term(raw, negated)
                }
                // "!x" לבד = "כל מה שאין בו x" — חלופה בלי מילה חיובית אחת היא עדיין חלופה תקפה.
                terms.takeIf { it.isNotEmpty() }?.let(::Alternative)
            }
            return SearchQuery(alternatives)
        }

        private fun globToRegex(glob: String): Regex {
            val pattern = buildString {
                val literal = StringBuilder()
                fun flush() {
                    if (literal.isNotEmpty()) append(Regex.escape(literal.toString()))
                    literal.clear()
                }
                for (c in glob) {
                    when (c) {
                        '*' -> { flush(); append(".*") }
                        '?' -> { flush(); append('.') }
                        else -> literal.append(c)
                    }
                }
                flush()
            }
            return Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        }

        private fun merge(ranges: List<IntRange>): List<IntRange> {
            if (ranges.isEmpty()) return emptyList()
            val sorted = ranges.sortedBy { it.first }
            val out = mutableListOf(sorted.first())
            for (r in sorted.drop(1)) {
                val last = out.last()
                if (r.first <= last.last + 1) out[out.lastIndex] = last.first..maxOf(last.last, r.last)
                else out += r
            }
            return out
        }
    }
}
