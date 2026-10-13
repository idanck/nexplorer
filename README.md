# nexplorer

<div dir="rtl">

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה.

## מצב

שלב מחקר. אין עדיין קוד — השלד `app/` הוא placeholder של תקן `repo-bootstrap`.
ההחלטות שצריך לקבל לפני שכותבים קוד: [docs/research/08-open-decisions.md](docs/research/08-open-decisions.md).

## תיעוד

| מסמך | תוכן |
|---|---|
| [docs/README.md](docs/README.md) | אינדקס המחקר |
| [01 — סקירה](docs/research/01-overview.md) | עובדות מפתח וציר זמן |
| [02 — יכולות](docs/research/02-features.md) | מפת יכולות מלאה |
| [03 — זרימות](docs/research/03-usage-flows.md) | 14 זרימות שימוש |
| [04 — מבנה](docs/research/04-information-architecture.md) | מסכים, תפריטים, מפת ניווט |
| [05 — עיצוב](docs/research/05-design-language.md) | צבעים, טיפוגרפיה, רכיבים, RTL |
| [06 — מונטיזציה](docs/research/06-monetization.md) | פרסומות ו-Pro |
| [07 — מיפוי טכני](docs/research/07-technical-mapping.md) | API וספריות אפשריות |
| [08 — החלטות](docs/research/08-open-decisions.md) | החלטות פתוחות |
| [tokens.json](docs/design/tokens.json) | טוקני עיצוב |
| [צילומי מסך](docs/screenshots/README.md) | 4 מסכי מקור |

## דרישות

ייקבעו אחרי החלטות D1 ו-D9 (UI, minSdk).

## הרצה מקומית

</div>

```bash
git clone https://github.com/idanck/nexplorer.git
cd nexplorer
git config core.hooksPath .githooks   # מפעיל את בדיקות ה-commit/push
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
| `docs/design/` | טוקני עיצוב |
| `docs/screenshots/` | צילומי מסך |
| `app/` | מודול האפליקציה (ריק) |

---

Stack: `android-kotlin` · הוקם ב-13/10/2026 לפי תקן `repo-bootstrap` v1.0.0

</div>
