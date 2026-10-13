# nexplorer

<div dir="rtl">

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה.

## מצב

שפת העיצוב אושרה ונכנסה לקוד: פרויקט Gradle עם Compose + Material 3, ערכת נושא מלאה ומסך קטלוג זמני.
עדיין אין לוגיקת קבצים. החלטות פתוחות: [docs/research/08-open-decisions.md](docs/research/08-open-decisions.md).

## תיעוד

| מסמך | תוכן |
|---|---|
| [docs/README.md](docs/README.md) | אינדקס התיעוד |
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

ייקבע בהחלטה D5 (הפצה).

## מבנה

| נתיב | תוכן |
|---|---|
| `docs/research/` | מסמכי המחקר 01–08 + מקורות |
| `docs/design/` | שפת העיצוב: מסמך, טוקנים, צילומי מסכים, מקורות הלוחות, רישיונות פונטים |
| `docs/screenshots/` | צילומי מסך |
| `app/` | מודול האפליקציה — ערכת נושא ומסך קטלוג ([app/README.md](app/README.md)) |

---

Stack: `android-kotlin` · הוקם ב-13/10/2026 לפי תקן `repo-bootstrap` v1.0.0

</div>
