package com.idanck.nexplorer.data.net

import com.idanck.nexplorer.core.NetProtocol

/**
 * "פרטי חיבור" (לוח "סטטוס חיבור ופרטי חיבור", גיליון 5): מה שנשמר בחיבור, ואיך הוא עובר עכשיו — שורות של תווית
 * וערך. [mono] — ערך שהוא כתובת, מזהה או שם משתמש (LTR, גופן קבוע). בלי סיסמאות ובלי מפתחות.
 */
object ConnectionFacts {

    data class Fact(val label: String, val value: String, val mono: Boolean = false)

    /** איך מתחברים — לשורת הסטטוס במגירה ולכותרת. Synology: "רשת ביתית" או "QuickConnect"; אחרת — הפרוטוקול. */
    fun kind(c: NetConnection): String = when {
        c.isQuickConnect -> "QuickConnect"
        c.protocol == NetProtocol.Synology -> "רשת ביתית"
        else -> c.protocol.label
    }

    /** [now] ו-[status] — ל"נבדק" (QuickConnect). */
    fun of(c: NetConnection, status: SynologyFs.Status?, now: Long): List<Fact> = buildList {
        add(Fact("סוג", if (c.protocol == NetProtocol.Synology) "Synology · ${kind(c)}" else c.protocol.label))
        when {
            c.protocol.cloud -> add(Fact("חשבון", c.user.ifBlank { "—" }, mono = true))
            c.isQuickConnect -> add(Fact("מזהה", c.quickConnect, mono = true))
            c.protocol == NetProtocol.WebDav -> add(Fact("כתובת", c.displayUrl(c.root), mono = true))
            else -> add(Fact("שרת", c.host + if (c.port != null && c.port != c.defaultPort) ":${c.port}" else "", mono = true))
        }
        when (c.protocol) {
            NetProtocol.Smb, NetProtocol.Synology -> if (!c.isQuickConnect || c.share.isNotBlank()) {
                add(Fact("שיתוף", c.share.ifBlank { "כל השיתופים" }, mono = c.share.isNotBlank()))
            }
            NetProtocol.Sftp -> add(Fact("תיקייה", "/" + c.share, mono = true))
            else -> Unit
        }
        if (!c.protocol.cloud) add(Fact("משתמש", c.user.ifBlank { "אורח" }, mono = c.user.isNotBlank()))
        when {
            status != null -> add(Fact("כתובת בפועל", status.host + if (status.port != 443) ":${status.port}" else "", mono = true))
            c.protocol == NetProtocol.Synology && !c.isQuickConnect ->
                add(Fact("כתובת בפועל", "${if (c.plainHttp) "http" else "https"}://${c.host}:${c.effectivePort}", mono = true))
        }
        security(c)?.let { add(Fact("אבטחה", it)) }
        if (status != null) add(Fact("נבדק", ago(now - status.checkedAt)))
    }

    private fun security(c: NetConnection): String? = when (c.protocol) {
        NetProtocol.Smb -> null
        NetProtocol.Sftp -> if (c.hostKey != null) "SSH · מפתח השרת אושר" else "SSH"
        NetProtocol.GoogleDrive, NetProtocol.OneDrive -> "https · התחברות בדף של השירות"
        NetProtocol.WebDav, NetProtocol.Synology -> when {
            c.plainHttp -> "http — בלי הצפנה"
            c.isQuickConnect -> "https · תעודה תקפה"
            c.hostKey != null -> "https · תעודה שאושרה"
            else -> "https · תעודה תקפה"
        }
    }

    /** "עכשיו", "לפני 5 דק׳", "לפני 2 שע׳". */
    fun ago(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0) / 60_000)
        return when {
            minutes < 1 -> "עכשיו"
            minutes < 60 -> "לפני $minutes דק׳"
            else -> "לפני ${minutes / 60} שע׳"
        }
    }
}
