package com.idanck.nexplorer.ui.keys

import android.view.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList

/** קיצורי מקלדת (מקלדת פיזית): לוח Tablet — F5 העתק, F6 העבר, Del מחק — ועוד כמה מקובלים. */
enum class KeyCommand {
    /** F5: העתקת הבחירה לתיקייה של החלון השני. */
    CopyToOther,

    /** F6: העברת הבחירה לחלון השני. */
    MoveToOther,

    /** Delete: מחיקה (עם אישור לפי ההגדרות). */
    Delete,

    /** F2: שינוי שם לפריט בודד. */
    Rename,

    /** Ctrl+A. */
    SelectAll,

    /** Esc: ביטול הבחירה. */
    ClearSelection,

    /** Backspace: לתיקייה שמעל. */
    GoUp,

    /** Tab: מעבר לחלון השני. */
    SwitchPane,

    /** Ctrl+R (F5 שמור להעתקה, כמו במנהלי קבצים בשני חלונות). */
    Refresh,

    /** Ctrl+F. */
    Search,

    /** MENU: תפריט ⋮ (של הפריטים הנבחרים, או של התיקייה). */
    Menu,
}

object KeyShortcuts {
    /**
     * [editing] — שדה טקסט בפוקוס (סינון, חיפוש): שם Delete, Backspace, Tab ו-Ctrl+A הם עריכה,
     * ולכן רק מקשי F ממופים.
     */
    fun commandFor(keyCode: Int, ctrl: Boolean, alt: Boolean, editing: Boolean): KeyCommand? {
        when (keyCode) {
            KeyEvent.KEYCODE_F5 -> return KeyCommand.CopyToOther
            KeyEvent.KEYCODE_F6 -> return KeyCommand.MoveToOther
            KeyEvent.KEYCODE_F2 -> return KeyCommand.Rename
        }
        if (editing || alt) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_FORWARD_DEL -> KeyCommand.Delete
            KeyEvent.KEYCODE_DEL -> if (ctrl) null else KeyCommand.GoUp
            KeyEvent.KEYCODE_ESCAPE -> KeyCommand.ClearSelection
            KeyEvent.KEYCODE_TAB -> if (ctrl) null else KeyCommand.SwitchPane
            KeyEvent.KEYCODE_A -> if (ctrl) KeyCommand.SelectAll else null
            KeyEvent.KEYCODE_R -> if (ctrl) KeyCommand.Refresh else null
            KeyEvent.KEYCODE_F -> if (ctrl) KeyCommand.Search else null
            KeyEvent.KEYCODE_MENU -> KeyCommand.Menu
            else -> null
        }
    }
}

/**
 * מעביר פקודות מקלדת מה-Activity למסך שמחזיק אותן כרגע (החלון הפעיל, ובשני חלונות גם המעבר ביניהם).
 * הכל על ה-main thread: המטפלים משנים מצב Compose ישירות. מטפל שנרשם אחרון נשאל ראשון.
 */
class KeyBus {
    private val handlers = CopyOnWriteArrayList<(KeyCommand) -> Boolean>()

    fun register(handler: (KeyCommand) -> Boolean): () -> Unit {
        handlers.add(0, handler)
        return { handlers.remove(handler) }
    }

    /** true = מישהו טיפל, והמקש לא ממשיך הלאה. */
    fun dispatch(command: KeyCommand): Boolean = handlers.any { it(command) }
}
