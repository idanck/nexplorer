# app — מודול האנדרואיד

Kotlin + Jetpack Compose + Material 3. ‏`minSdk 26`, ‏`targetSdk 35`, חבילה `com.idanck.nexplorer`.
הגרסאות ב-`gradle/libs.versions.toml` (זהות ל-ats-mobile-app, שנבנה בהצלחה ב-CI).

| נתיב | מה יש בו |
|---|---|
| `src/main/kotlin/.../ui/theme/` | שפת העיצוב כקוד: `Color.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt`, `Theme.kt` |
| `src/main/kotlin/.../ui/components/DrawerBanner.kt` | באנר המגירה (גלים כחולים + שם האפליקציה + שם המכשיר) |
| `src/main/kotlin/.../core/` | לוגיקה טהורה בלי אנדרואיד: מיון, מדורים, תחביר חיפוש, פורמטים — מכוסה ב-`src/test/` |
| `src/main/kotlin/.../data/` | קבצים (`java.io`), אחסונים, אוספים מ-MediaStore, העדפות (DataStore), תור העברות + שירות חזית |
| `src/main/kotlin/.../update/` | עדכון מתוך האפליקציה: מניפסט, הורדה, אימות, `PackageInstaller` |
| `src/main/kotlin/.../ui/` | המסכים לפי הלוחות: `browser/`, `drawer/`, `search/`, `settings/`, `transfer/`, `properties/`, `permission/`, `update/` |
| `src/main/res/font/` | Noto Sans Hebrew (400/500/600/700) ו-JetBrains Mono (400/500), סטטיים, OFL |

מקור האמת לעיצוב: `docs/design/` בשורש הריפו. שינוי ערך כאן מחייב עדכון `docs/design/tokens.json` באותו PR.

| build | מזהה | חתימה | עדכונים |
|---|---|---|---|
| `debug` | `com.idanck.nexplorer.debug` | מפתח debug | כבויים |
| `release` | `com.idanck.nexplorer` | מפתח release מ-CI (או debug כשאין) | פעילים |
