<div dir="rtl">

# תיעוד

## שפת העיצוב של nexplorer

| מסמך | מה יש בו |
|---|---|
| [שפת העיצוב](design/design-language.md) | "סלע ואות": עקרונות מבנה, צבע, טיפוגרפיה, צורות, 19 מסכים, יומן החלטות, פתוח |
| [tokens.json](design/tokens.json) | הטוקנים — מקור האמת לקוד ב-`app/.../ui/theme/` |
| [screens/](design/screens/) | צילום של כל לוח מהקנבס |
| [boards/](design/boards/) | מקורות ה-HTML של הלוחות (ערכים מדויקים) |
| [fonts/](design/fonts/) | רישיונות OFL של הפונטים |

## תוכניות מימוש

| מסמך | מה יש בו |
|---|---|
| [שלב 1](plan/01-phase1.md) | מנהל קבצים מקומי + עדכונים מתוך האפליקציה: היקף, ערכים זמניים, ארכיטקטורה |
| [שלב 2](plan/02-phase2.md) | 2א: מנתח אחסון, ZIP, עורך טקסט, שינוי שם מרובה · 2ב: שני חלונות, גרירה, מקלדת, מציג תמונות |
| [שלב 3](plan/03-phase3.md) | 3א: 7z, TAR, GZ/BZ2/XZ, גיליון APK והתקנה · 3ב: נגן שמע ווידאו |
| [שלב 4](plan/04-phase4.md) | 4א: הסתר תיקייה, גודל תיקיות, סידור מועדפים · 4ב: הצפנת קבצים (והפורמט) · 4ג: תיקייה מוצפנת (`data/crypto/Vaults`) |
| [שלב 5](plan/05-phase5.md) | מקומות ברשת (D3): 5א SMB (`data/net/`, `data/transfer/TreeCopy`) · 5ב SFTP (`SftpFs`, `SshCrypto`) · 5ג WebDAV (`WebDavFs`, `core/DavAddress`) · 5ד פתיחה ישירה מהשרת (`media/RemoteDataSource`, `data/net/RemoteFilesProvider`) |
| [שלב 6](plan/06-phase6.md) | ענן (D4): 6א Google Drive (`DriveFs`, `GoogleAuth`, `core/DriveNames`) · 6ב OneDrive (`OneDriveFs`, `MicrosoftAuth`) · מה עידן מגדיר אצל Google ו-Microsoft |
| [העברה בין מכשירים](plan/08-peer.md) | טלפון ↔ טלוויזיה ברשת הביתית, כשהאפליקציה פתוחה: חיבור בקוד, הפרוטוקול וההצפנה (`data/peer/`, `ui/peer/`) |

## מחקר — Solid Explorer

מיפוי של Solid Explorer File Manager (NeatBytes) כהכנה לבניית nexplorer.
נאסף ב-13/10/2026 מתוך 4 צילומי מסך של עידן (Samsung, ממשק בעברית, גרסה 3.6.x) ומקורות פומביים.

| # | מסמך | מה יש בו |
|---|---|---|
| 1 | [סקירה כללית](research/01-overview.md) | עובדות מפתח, ציר זמן גרסאות, מתודולוגיה, מה לא אומת |
| 2 | [מפת יכולות](research/02-features.md) | כל היכולות לפי קטגוריה, Free/Pro, מקור לכל שורה |
| 3 | [זרימות שימוש](research/03-usage-flows.md) | 14 זרימות צעד-אחר-צעד, כולל מצבי ממשק |
| 4 | [ארכיטקטורת מידע](research/04-information-architecture.md) | מלאי מסכים, תפריטים, מגירה, מפת ניווט |
| 5 | [שפת עיצוב](research/05-design-language.md) | צבעים (hex שנדגמו מהפיקסלים), טיפוגרפיה, צורות, רכיבים, RTL |
| 6 | [מונטיזציה](research/06-monetization.md) | פרסומות, Pro, נקודות upsell |
| 7 | [מיפוי טכני](research/07-technical-mapping.md) | יכולת ← API / ספריות אנדרואיד אפשריות |
| 8 | [החלטות פתוחות](research/08-open-decisions.md) | החלטות שעידן צריך לקבל — אפשרויות, יתרונות וחסרונות |
| — | [מקורות](research/sources.md) | כל הקישורים |
| — | [טוקני Solid](research/solid-tokens.json) | הצבעים והמידות שנדגמו מ-Solid במבנה JSON |

## מוסכמות במסמכים

- **[אומת]** — מופיע במקור רשמי (Play / changelog / אתר NeatBytes) או בצילום מסך.
- **[מקור יחיד]** — מופיע רק במקור צד-שלישי אחד.
- **[לא אומת]** — השערה או מידע חסר. לא להסתמך בלי בדיקה.
- מידות בפיקסלים נמדדו על צילומי 1080×2340. ה-density של המכשיר לא ידועה, לכן אין המרה ל-dp.

</div>
