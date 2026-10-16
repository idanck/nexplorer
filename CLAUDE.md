# CLAUDE.md — nexplorer

> נוצר על ידי הסקיל `repo-bootstrap` v1.0.0 בתאריך 13/10/2026.
> **הקובץ הזה הוא ההגנה האמיתית של הריפו.** הריפו **ציבורי** מ-15/10/2026, ולכן אפשר להפעיל הגנת שרת על `main`
> (Settings → Branches). גם איתה, הכללים כאן הם מה שמונע בלגן. עדכן אותו כשהפרויקט משתנה.

## מה זה

מנהל קבצים לאנדרואיד בהשראת Solid Explorer — מיפוי יכולות, שפת עיצוב ואפיון כהכנה לבנייה

## מצב ותיעוד

שלב 1 ממומש (`docs/plan/01-phase1.md`): מנהל קבצים מקומי + עדכון מתוך האפליקציה. שלב 2א (`docs/plan/02-phase2.md`): מנתח, ZIP, עורך, שינוי שם מרובה. שלב 2ב: שני חלונות (טאבלט: 840×480 ומעלה), גרירה, קיצורי מקלדת, מציג תמונות. שלב 3א (`docs/plan/03-phase3.md`): 7z/TAR/GZ (`data/archive/Archives`), גיליון APK. שלב 3ב: נגני שמע ווידאו (Media3; השמע ב-`media/PlaybackService`, הממשק ב-`ui/player/`). שלב 4א (`docs/plan/04-phase4.md`): הסתר תיקייה, גודל תיקיות (`core/FolderSizes`), סידור מועדפים. שלב 4ב: הצפנת קבצים (`data/crypto/EncryptedFiles` — הפורמט מתועד בתוכנית, ושינוי בו חייב לשמור על פענוח של קבצים קיימים ועל `tools/nxdecrypt.py`). שלב 4ג: תיקייה מוצפנת (`data/crypto/Vaults` — קובץ הסימון, `VaultKeys` בזיכרון; ההצפנה בהעתקה ב-`TransferManager.sealTree`). שלב 5א (`docs/plan/05-phase5.md`, D3 הוכרע: SMB+SFTP+WebDAV): מקומות ברשת ב-SMB — נתיב `smb://<מזהה חיבור>/...` (`core/NetPaths`), `data/net/` (`SmbFs`, `RemoteFiles`, הסיסמאות ב-`ConnectionSecrets` — Keystore), העברות ברשת ב-`data/transfer/TreeCopy`; כללי R8 ל-smbj ב-`proguard-rules.pro` (נבדקים ב-`.github/qa/release-smoke.sh`). שלב 5ב: SFTP — `data/net/SftpFs` (sshj), נתיב `sftp://<מזהה>/<נתיב בשרת>`, מפתח השרת שאושר ב-`NetConnection.hostKey` (`SshHostKeys`), ו-Ed25519/X25519 לאנדרואיד שלפני 13 ב-`SshCrypto` (לא לרשום את BouncyCastle כספק). שלב 5ג: WebDAV — `data/net/WebDavFs` (OkHttp), נתיב `dav://<מזהה>/<נתיב מתחת לכתובת>`, תעודה עצמית שאושרה ב-`hostKey` ("x509-sha256 …"), ו-http מותר ב-`res/xml/network_security_config.xml` חוץ מערוץ העדכונים. שלב 6 (`docs/plan/06-phase6.md`, D4 הוכרע: Google Drive + OneDrive): ענן — `data/net/DriveFs` (Drive API v3; שמות כפולים ו-/ ב-`core/DriveNames`), `data/net/OneDriveFs` (Graph), נתיב `gdrive://<מזהה>/...` ו-`onedrive://<מזהה>/...`; ההתחברות ל-Google ב-Play services (`GoogleAuth`, לקוח Android לפי שם החבילה וטביעת ה-SHA-1 — שינוי applicationId או מפתח מחייב לרשום מחדש), ל-Microsoft ב-PKCE (`MicrosoftAuth`, ‏`OAuthRedirectActivity`; ה-client ID ב-`gradle.properties`, ה-refresh token ב-`CloudTokens`). בבדיקות: `.github/qa/cloud-server.py`. לפני כל עבודה קרא את `docs/README.md`.
- **שפת העיצוב של nexplorer (מחייבת):** `docs/design/design-language.md` + `docs/design/tokens.json`. צילומי המסכים ב-`docs/design/screens/` — בנה מסכים לפיהם.
- הקוד של העיצוב: `app/src/main/kotlin/com/idanck/nexplorer/ui/theme/`. **אל תכתוב hex או גדלים בתוך מסכים** — רק דרך `MaterialTheme`, `NexplorerTheme.colors`, `NexplorerDimens`, `NexplorerShape`.
- שינוי ערך עיצוב = עדכון `tokens.json` + הקוד + `design-language.md` באותו PR.
- שמות קבצים ונתיבים: `FileNameTextStyle` / `MonoTextStyle` (LTR מבודד). הכפתור הצף: עיגול, `FabPosition.End`.
- מפת יכולות Solid Explorer: `docs/research/02-features.md`
- מחקר שפת העיצוב של Solid (השראה בלבד): `docs/research/05-design-language.md`, `docs/research/solid-tokens.json`
- החלטות פתוחות (אין לבחור במקום עידן): `docs/research/08-open-decisions.md`
- אין להשתמש בשם, לוגו או נכסים של Solid Explorer / NeatBytes בקוד או במשאבים.
- **הקוד של אנדרואיד נבנה רק ב-CI** — בסביבת הענן אין Android SDK (dl.google.com חסום). לוגיקה טהורה שמים ב-`core/` ובודקים ב-`src/test/`.
- **minSdk 30 (D9, הוכרע 15/10/2026):** Android 11 ומעלה — מודל גישה אחד (`StorageAccess`, "גישה לכל הקבצים"). אין קוד ל-Android 8–10; בדיקות האמולטור על API 30 (המינימום) ו-35.
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
3. **אפס סודות ב-git.** שמות משתני סביבה ב-`.env.example`; הערכים חיים רק מחוץ לריפו, על ה-Pi
   (הסקיל `credentials-vault`). יש `pre-commit` hook שסורק ומסרב.
4. **קרא לפני שאתה כותב.** לפני שינוי בקובץ קיים — קרא אותו והתאם לסגנון הקיים.
5. **אמת לפני שאתה מדווח.** אחרי שינוי: build / lint / הרצה. אל תכתוב "בוצע" בלי אימות.
6. **אל תמציא.** חסר נתון (endpoint, פורט, מבנה טבלה)? בדוק בקוד, בסקיל, או שאל את עידן.
7. **הריפו ציבורי — כל מה שנכנס אליו גלוי לכולם:** קוד, היסטוריה, PRs והערות, לוגים של Actions והענפים `apk` ו-`qa`.
   - אסור לכתוב כתובות, שמות שרתים, משתמשים או פורטים מהרשת הביתית, נתיבים ב-Pi, דגמי ציוד או פרטים אישיים.
   - בבדיקות, בהערות ובלוחות משתמשים בערכי דוגמה: `192.168.1.x`, ‏`alex`, ‏`pi`, ‏`nas.local`, ‏`cloud.example.com`.
   - צילום מסך ממכשיר אמיתי נכנס רק אחרי שבדקת שאין בו מידע אישי.
   - דקות Actions על `ubuntu-latest` בחינם, ולכן אין לבטל ריצות QA כדי לחסוך.

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
