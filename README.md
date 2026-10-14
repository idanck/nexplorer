# nexplorer

<div dir="rtl">

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה.

## מצב

שלב 1 ממומש ([התוכנית](docs/plan/01-phase1.md)): מנהל קבצים מקומי לפי שפת העיצוב "סלע ואות" — דפדוף, בחירה, העתקה/העברה/מחיקה בתור העברות, חיפוש עמוק, אפשרויות תצוגה, הגדרות — ועדכון מתוך האפליקציה.
שלב 2א ([התוכנית](docs/plan/02-phase2.md)): מנתח אחסון, ארכיוני ZIP (כולל AES-256), עורך טקסט, שינוי שם מרובה. שלב 2ב: שני חלונות בטאבלט, גרירה ביניהם, קיצורי מקלדת, מציג תמונות. שלב 3א ([התוכנית](docs/plan/03-phase3.md)): ארכיוני 7z (כולל AES-256), TAR ו-GZ/BZ2/XZ, וגיליון APK עם התקנה. שלב 3ב: נגן שמע (ברקע, עם התראה) ונגן וידאו. שלב 4א ([התוכנית](docs/plan/04-phase4.md)): הסתר תיקייה, גודל תיקיות, סידור מועדפים.
עוד אין רשת, ענן, הצפנת קבצים או טאבלט. החלטות פתוחות: [docs/research/08-open-decisions.md](docs/research/08-open-decisions.md).
QA על אמולטור (API 29 + 35) רץ בכל PR; הצילומים והתוצאות בענף [`qa`](https://github.com/idanck/nexplorer/tree/qa).

## התקנה ועדכונים

- **APK אחרון:** הענף [`apk`](https://github.com/idanck/nexplorer/tree/apk) — מתעדכן בכל build חתום (גם של PR).
- **עדכונים:** האפליקציה בודקת פעם ביום את `https://nexplorer-updates.idanck.com/latest.json` (וגם בהגדרות ← עדכונים ← "בדוק עכשיו"). כל מיזוג ל-`main` מפרסם גרסה.
- בהתקנה הראשונה צריך לאשר "התקנה ממקורות לא ידועים" לאפליקציה שממנה פותחים את ה-APK, ובעדכון הראשון — ל-nexplorer עצמה.
- פרטי הערוץ, המפתח והסודות: [worker/README.md](worker/README.md).

## תיעוד

| מסמך | תוכן |
|---|---|
| [docs/README.md](docs/README.md) | אינדקס התיעוד |
| [תוכנית שלב 1](docs/plan/01-phase1.md) | מה נכנס, מה לא, ערכים זמניים, ארכיטקטורה |
| [תוכנית שלב 2](docs/plan/02-phase2.md) | 2א: מנתח אחסון, ZIP, עורך, שינוי שם מרובה · 2ב: שני חלונות, גרירה, מקלדת, מציג תמונות |
| [תוכנית שלב 3](docs/plan/03-phase3.md) | 3א: 7z, TAR, GZ/BZ2/XZ, APK · 3ב: נגני מדיה |
| [שפת העיצוב](docs/design/design-language.md) | "סלע ואות" — עקרונות, צבע, טיפוגרפיה, מסכים, החלטות |
| [tokens.json](docs/design/tokens.json) | טוקני העיצוב של nexplorer (מקור האמת) |
| [01 — סקירה](docs/research/01-overview.md) | עובדות מפתח וציר זמן |
| [02 — יכולות](docs/research/02-features.md) | מפת יכולות מלאה |
| [03 — זרימות](docs/research/03-usage-flows.md) | 14 זרימות שימוש |
| [04 — מבנה](docs/research/04-information-architecture.md) | מסכים, תפריטים, מפת ניווט |
| [05 — עיצוב](docs/research/05-design-language.md) | צבעים, טיפוגרפיה, רכיבים, RTL |
| [06 — מונטיזציה](docs/research/06-monetization.md) | פרסומות ו-Pro |
| [07 — מיפוי טכני](docs/research/07-technical-mapping.md) | API וספריות אפשריות |
| [08 — החלטות](docs/research/08-open-decisions.md) | החלטות פתוחות |
| [solid-tokens.json](docs/research/solid-tokens.json) | ערכים שנדגמו מ-Solid (השראה) |
| [צילומי מסך](docs/screenshots/README.md) | 4 מסכי מקור |

## דרישות

JDK 17 ו-Android SDK 35. ‏`minSdk 26` זמני עד החלטה D9.

## הרצה מקומית

</div>

```bash
git clone https://github.com/idanck/nexplorer.git
cd nexplorer
git config core.hooksPath .githooks   # מפעיל את בדיקות ה-commit/push
./gradlew assembleDebug
```

<div dir="rtl">

## משתני סביבה

העתק את `.env.example` ל-`.env` ומלא ערכים. `.env` לא נכנס ל-git לעולם.

## Deploy

אין deploy ידני: מיזוג ל-`main` ← CI בונה APK חתום ← מפרסם ל-`nexplorer-updates.idanck.com` ← האפליקציות המותקנות מציעות עדכון.
ה-Worker עצמו נפרס ידנית מה-Pi (ראה [worker/README.md](worker/README.md)).

## מבנה

| נתיב | תוכן |
|---|---|
| `docs/research/` | מסמכי המחקר 01–08 + מקורות |
| `docs/design/` | שפת העיצוב: מסמך, טוקנים, צילומי מסכים, מקורות הלוחות, רישיונות פונטים |
| `docs/screenshots/` | צילומי מסך |
| `docs/plan/` | תוכניות מימוש |
| `app/` | מודול האפליקציה ([app/README.md](app/README.md)) |
| `worker/` | ה-Worker של ערוץ העדכונים |

---

Stack: `android-kotlin` · הוקם ב-13/10/2026 לפי תקן `repo-bootstrap` v1.0.0

</div>
