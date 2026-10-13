# app — מודול האנדרואיד

Kotlin + Jetpack Compose + Material 3. ‏`minSdk 26`, ‏`targetSdk 35`, חבילה `com.idanck.nexplorer`.
הגרסאות ב-`gradle/libs.versions.toml` (זהות ל-ats-mobile-app, שנבנה בהצלחה ב-CI).

| נתיב | מה יש בו |
|---|---|
| `src/main/kotlin/.../ui/theme/` | שפת העיצוב כקוד: `Color.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt`, `Theme.kt` |
| `src/main/kotlin/.../ui/components/DrawerBanner.kt` | באנר המגירה (גלים כחולים + שם האפליקציה + שם המכשיר) |
| `src/main/kotlin/.../ui/catalog/` | מסך קטלוג זמני שמציג את הטוקנים על המכשיר |
| `src/main/res/font/` | Noto Sans Hebrew (400/500/600/700) ו-JetBrains Mono (400/500), סטטיים, OFL |

מקור האמת לעיצוב: `docs/design/` בשורש הריפו. שינוי ערך כאן מחייב עדכון `docs/design/tokens.json` באותו PR.
