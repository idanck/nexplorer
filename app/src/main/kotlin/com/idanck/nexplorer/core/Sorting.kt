package com.idanck.nexplorer.core

/**
 * מיון "טבעי": `file2` לפני `file10`, בלי תלות ברישיות.
 * מספרים ארוכים מושווים לפי אורך ואז לקסיקוגרפית, כדי שלא יגלשו מ-Long.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }
}

/**
 * תיקיות קודם בכל מיון חוץ מתאריך: במיון לפי תאריך הלוח "מסך ראשי" מערבב,
 * כי שם השאלה היא "מה השתנה לאחרונה" ולא "איפה התיקיות".
 */
fun SortOrder.foldersFirst(): Boolean = key != SortKey.Date

fun List<Entry>.sortedFor(order: SortOrder): List<Entry> {
    val byName = compareBy<Entry, String>(NaturalOrder) { it.name }
    val primary: Comparator<Entry> = when (order.key) {
        SortKey.Name -> byName
        SortKey.Date -> compareBy<Entry> { it.lastModified }.then(byName)
        SortKey.Size -> compareBy<Entry> { it.size }.then(byName)
        SortKey.Type -> compareBy<Entry> { it.kind.ordinal }.thenBy { it.extension }.then(byName)
    }
    val directed = if (order.descending) primary.reversed() else primary
    val comparator = if (order.foldersFirst()) {
        compareBy<Entry> { !it.isDirectory }.then(directed)
    } else {
        directed
    }
    return sortedWith(comparator)
}
