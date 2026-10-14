# CLAUDE.md — nexplorer

> נוצר על ידי הסקיל `repo-bootstrap` v1.0.0 בתאריך 13/10/2026.
> **הקובץ הזה הוא ההגנה האמיתית של הריפו.** אין הגנת שרת על `main` (GitHub Free + ריפו פרטי),
> ולכן הכללים כאן הם מה שמונע בלגן. עדכן אותו כשהפרויקט משתנה.

## מה זה

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה

## מצב ותיעוד

שלב 1 ממומש (`docs/plan/01-phase1.md`): מנהל קבצים מקומי + עדכון מתוך האפליקציה. שלב 2א (`docs/plan/02-phase2.md`): מנתח, ZIP, עורך, שינוי שם מרובה. שלב 2ב: שני חלונות (טאבלט: 840×480 ומעלה), גרירה, קיצורי מקלדת, מציג תמונות. שלב 3א (`docs/plan/03-phase3.md`): 7z/TAR/GZ (`data/archive/Archives`), גיליון APK. שלב 3ב: נגני שמע ווידאו (Media3; השמע ב-`media/PlaybackService`, הממשק ב-`ui/player/`). שלב 4א (`docs/plan/04-phase4.md`): הסתר תיקייה, גודל תיקיות (`core/FolderSizes`), סידור מועדפים. לפני כל עבודה קרא את `docs/README.md`.
- **שפת העיצוב של nexplorer (מחייבת):** `docs/design/design-language.md` + `docs/design/tokens.json`. צילומי המסכים ב-`docs/design/screens/` — בנה מסכים לפיהם.
- הקוד של העיצוב: `app/src/main/kotlin/com/idanck/nexplorer/ui/theme/`. **אל תכתוב hex או גדלים בתוך מסכים** — רק דרך `MaterialTheme`, `NexplorerTheme.colors`, `NexplorerDimens`, `NexplorerShape`.
- שינוי ערך עיצוב = עדכון `tokens.json` + הקוד + `design-language.md` באותו PR.
- שמות קבצים ונתיבים: `FileNameTextStyle` / `MonoTextStyle` (LTR מבודד). הכפתור הצף: עיגול, `FabPosition.End`.
- מפת יכולות Solid Explorer: `docs/research/02-features.md`
- מחקר שפת העיצוב של Solid (השראה בלבד): `docs/research/05-design-language.md`, `docs/research/solid-tokens.json`
- החלטות פתוחות (אין לבחור במקום עידן): `docs/research/08-open-decisions.md`
- אין להשתמש בשם, לוגו או נכסים של Solid Explorer / NeatBytes בקוד או במשאבים.
- **הקוד של אנדרואיד נבנה רק ב-CI** — בסביבת הענן אין Android SDK (dl.google.com חסום). לוגיקה טהורה שמים ב-`core/` ובודקים ב-`src/test/`.
- **עדכונים (D5):** `versionCode` = מספר הריצה של ה-workflow `CI` — לא לשנות את שם הקובץ `ci.yml` או את שם ה-workflow, אחרת המספור מתאפס והמכשירים לא יראו עדכון.
- **מפתח החתימה הוא זהות האפליקציה.** אסור ליצור מפתח חדש או להחליף את `RELEASE_KEYSTORE_B64`. טביעת האצבע נעוצה ב-`ci.yml` (`RELEASE_CERT_SHA256`). פרטים: `worker/README.md`.

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

מיזוג ל-`main` = גרסה: CI בונה APK חתום, מפרסם ל-`nexplorer-updates.idanck.com`, והאפליקציות המותקנות מציעות עדכון.
כל build חתום (גם PR) נדחף גם לענף `apk`. ה-Worker נפרס ידנית מה-Pi — `worker/README.md`.

<!-- TODO: איך מדפלים, לאן, ומה בדיקת העשן שמאשרת הצלחה -->

## אסור לגעת

<!-- TODO: נתיבים או קבצים שדורשים אישור מפורש מעידן לפני שינוי -->
