package com.idanck.nexplorer.ui.theme

import androidx.compose.ui.unit.dp

/** מידות ומרווחים. סולם מרווחים: 4 · 8 · 12 · 16 · 24 · 32. */
object NexplorerDimens {
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 24.dp
    val Space6 = 32.dp

    /** גובה מינימלי של שורה ברשימת קבצים. */
    val RowHeight = 64.dp

    /** יעד מגע מינימלי לכל רכיב לחיץ. */
    val TouchTarget = 44.dp

    /** אריח האייקון בשורת קובץ. */
    val IconTile = 44.dp

    /** קוטר הכפתור הצף "חדש" במצב רגיל. */
    val Fab = 56.dp

    /** קוטר הכפתור הצף כשתפריט "חדש" פתוח (✕). */
    val FabOpen = 64.dp

    /** מרחק הכפתור הצף מקצות המסך (צד הסוף = שמאל ב-RTL). */
    val FabMargin = 20.dp

    /** גובה גלולה בתפריט "חדש". */
    val SpeedDialItem = 56.dp

    /** רוחב מגירת המקומות בטלפון. */
    val DrawerWidth = 312.dp

    /** גובה הבאנר בראש המגירה (בלי שורת המצב, שנוספת מעליו). */
    val DrawerBannerHeight = 168.dp

    /** תוכן הבאנר: ריפוד, אריח הסמל, ושורת המכשיר. */
    val BannerPaddingStart = 20.dp
    val BannerPaddingEnd = 14.dp
    val BannerPaddingTop = 24.dp
    val BannerPaddingBottom = 10.dp
    val BannerLogoTile = 32.dp
    val BannerLogoIcon = 18.dp
    val BannerLogoGap = 10.dp
    val BannerDeviceIcon = 16.dp
    val BannerDeviceGap = 7.dp

    /** ריפוד אנכי של שורת קובץ (ברמת צפיפות רגילה). */
    val RowPaddingVertical = 10.dp

    /** רווח בין האריח לטקסט בשורה. */
    val RowGap = 14.dp

    /** שורה בתצוגה קומפקטית. */
    val CompactRowHeight = 48.dp
    val CompactIconTile = 32.dp

    /** אריח האייקון בתצוגת רשת. */
    val GridIconTile = 56.dp

    /** אריח של מקום אחסון במגירה. */
    val PlaceTile = 40.dp

    /** פס התפוסה מתחת למקום אחסון. */
    val UsageBar = 5.dp

    /** פס התקדמות של העברה. */
    val ProgressBar = 6.dp

    /** תיבת הסימון במצב בחירה. */
    val Checkbox = 24.dp

    /** כותרת הבחירה וסרגל הפעולות הצפים. */
    val FloatingBarHeight = 64.dp

    /** פריט ורוחב בתפריט צף (⋮ לפריט). */
    val MenuItemHeight = 50.dp
    val MenuWidth = 268.dp

    /** חלון "אפשרויות תצוגה". */
    val PopoverWidth = 300.dp

    /** כפתורי סרגל ההדבקה. */
    val BottomBarButton = 48.dp

    /** מנתח האחסון: פס הפילוח (pill), נקודת המקרא, ופס ורוחב הגודל בשורת "תיקיות גדולות". */
    val AnalyzerBar = 14.dp
    val AnalyzerBarGap = 2.dp
    val LegendDot = 10.dp
    val FolderBarWidth = 90.dp
    val FolderSizeWidth = 64.dp

    /** רשימה מקדימה בתוך חלון (שינוי שם מרובה): גלילה פנימית מעבר לגובה הזה. */
    val PreviewListMaxHeight = 180.dp

    /**
     * שני חלונות (לוח Tablet): מרוחב 840 וגובה 480 ומעלה — טאבלט. טלפון לרוחב (~914×411) נשאר בחלון אחד:
     * בגובה כזה כל חלון מציג שלוש שורות.
     */
    val DualPaneMinWidth = 840.dp
    val DualPaneMinHeight = 480.dp

    /** מסילת הניווט: רוחב, פריט עם תווית (64×56) ובלי (56), רווח בין פריטים, תג ספירה. */
    val RailWidth = 88.dp
    val RailItem = 56.dp
    val RailItemLabeledWidth = 64.dp
    val RailItemIcon = 22.dp
    val RailGap = 6.dp
    val RailBadge = 18.dp

    /** חלון: רווח בין החלונות, מסגרת (פעיל = primary), ריפוד אופקי של כותרת, שורות ותחתית. */
    val PaneGap = 12.dp
    val PaneBorder = 2.dp
    val PanePaddingHorizontal = 14.dp

    /** תחתית החלון: "N נבחרו · העתק לחלון השני · העבר", או שורת הרמז. */
    val PaneFooterHeight = 56.dp
    val PaneButtonHeight = 36.dp

    /** יעד שחרור: מסגרת מקווקוות 2 (מקף 6, רווח 4), מוזחת 8 מהצדדים. */
    val DropTargetBorder = 2.dp
    val DropTargetDash = 6.dp
    val DropTargetGap = 4.dp
    val DropTargetInset = 8.dp

    /** נגן השמע: עטיפה (עד 280; קטנה יותר במסך נמוך), סמל במקום עטיפה, כפתור נגן/השהה ושאר הבקרים. */
    val PlayerArtworkMax = 280.dp
    val PlayerArtworkIcon = 96.dp
    val PlayerPlayButton = 72.dp
    val PlayerPlayIcon = 36.dp
    val PlayerControl = 56.dp

    /**
     * "הוסף מקום" (לוח "חיבור חדש"): אריח סוג (אייקון 22, מסגרת 1 — ובנבחר 2), שדה (גובה 44, ריפוד 14),
     * שורת שרת שנמצא (52, נקודה 8, כפתור "בחר" 32), ואייקון המנעול שליד ההסבר (18).
     */
    val ConnectTypeIcon = 22.dp
    val ConnectTypeBorder = 1.dp
    val ConnectTypeBorderSelected = 2.dp
    val ConnectField = 44.dp
    val ConnectFieldPadding = 14.dp
    val ConnectFoundRow = 52.dp
    val ConnectStatusDot = 8.dp
    val ConnectChooseButton = 32.dp
    val ConnectHintIcon = 18.dp
}
