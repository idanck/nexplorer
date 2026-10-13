# CLAUDE.md — nexplorer

> נוצר על ידי הסקיל `repo-bootstrap` v1.0.0 בתאריך 13/10/2026.
> **הקובץ הזה הוא ההגנה האמיתית של הריפו.** אין הגנת שרת על `main` (GitHub Free + ריפו פרטי),
> ולכן הכללים כאן הם מה שמונע בלגן. עדכן אותו כשהפרויקט משתנה.

## מה זה

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה

## מצב ותיעוד

שלב מחקר — אין קוד עדיין. לפני כל עבודה קרא את `docs/README.md`.
- מפת יכולות Solid Explorer: `docs/research/02-features.md`
- שפת עיצוב + טוקנים: `docs/research/05-design-language.md`, `docs/design/tokens.json`
- החלטות פתוחות (אין לבחור במקום עידן): `docs/research/08-open-decisions.md`
- אין להשתמש בשם, לוגו או נכסים של Solid Explorer / NeatBytes בקוד או במשאבים.

## Stack

`android-kotlin`

## פקודות

<!-- מולא אוטומטית לפי ה-stack; עדכן כשמשתנה -->

| מטרה | פקודה |
|---|---|
| build | `./gradlew assembleDebug` |
| test | `./gradlew test` |
| lint | `./gradlew lint` |
| התקנה למכשיר | `./gradlew installDebug` |

## כללי עבודה — מחייבים

1. **אסור push ישיר ל-`main`.** תמיד `feature/<name>` ואז PR. יש `pre-push` hook מקומי
   שחוסם, אבל הוא חל רק על clone שהוגדר בו `core.hooksPath` — הכלל הזה גובר עליו תמיד.
2. **Conventional Commits בלבד:** `<type>(<scope>): <subject>` —
   `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`.
   subject בציווי, עד ~72 תווים.
3. **אפס סודות ב-git.** שמות משתני סביבה ב-`.env.example`; הערכים חיים רק ב-
   `/home/idan/.secrets/credentials.env` על ה-Pi. יש `pre-commit` hook שסורק ומסרב.
4. **קרא לפני שאתה כותב.** לפני שינוי בקובץ קיים — קרא אותו והתאם לסגנון הקיים.
5. **אמת לפני שאתה מדווח.** אחרי שינוי: build / lint / הרצה. אל תכתוב "בוצע" בלי אימות.
6. **אל תמציא.** חסר נתון (endpoint, פורט, מבנה טבלה)? בדוק בקוד, בסקיל, או שאל את עידן.

## אחרי clone חדש

```bash
git config core.hooksPath .githooks
```
בלי זה ה-hooks לא פעילים ב-clone הזה. בסשן ענן זה השלב הראשון.

## סקילים רלוונטיים

| נושא | סקיל |
|---|---|
| מוסכמות קוד (מקור אמת) | `coding-conventions` |
| branch / commit / PR / merge | `git-workflow` |
| סקירת קוד, tags, releases | `code-review` |
| סודות והרשאות | `credentials-vault` |

## משתני סביבה

ראה `.env.example` — שמות בלבד. ערכים לעולם לא נכנסים לריפו.

## Deploy

<!-- TODO: איך מדפלים, לאן, ומה בדיקת העשן שמאשרת הצלחה -->

## אסור לגעת

<!-- TODO: נתיבים או קבצים שדורשים אישור מפורש מעידן לפני שינוי -->
