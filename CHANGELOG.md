# Changelog

כל שינוי מהותי מתועד כאן. הפורמט לפי [Keep a Changelog](https://keepachangelog.com/),
המספור לפי [SemVer](https://semver.org/lang/he/).

## [Unreleased]

### Added
- שפת העיצוב "סלע ואות" (`docs/design/`): מסמך, `tokens.json`, צילומי 15 לוחות, מקורות הלוחות, רישיונות פונטים
- פרויקט Gradle (Compose + Material 3, AGP 8.7.3, Kotlin 2.0.21) עם ערכת הנושא `ui/theme/`, באנר המגירה ומסך קטלוג זמני
- פונטים: Noto Sans Hebrew (400–700) ו-JetBrains Mono (400–500), סטטיים, OFL
- CI: בנייה, בדיקות ו-lint אמיתיים + APK debug כ-artifact

### Changed
- טוקני Solid הועברו מ-`docs/design/tokens.json` ל-`docs/research/solid-tokens.json`; `docs/design/tokens.json` הוא עכשיו של nexplorer
- החלטות D1, D7, D8 סומנו כסגורות ב-`08-open-decisions.md`; D9, D10 עדיין פתוחות עם ערכים זמניים בקוד

### Added (מחקר)
- מחקר Solid Explorer: מפת יכולות, זרימות שימוש, ארכיטקטורת מידע, שפת עיצוב, מונטיזציה, מיפוי טכני, החלטות פתוחות (`docs/research/`)
- טוקני עיצוב (`docs/design/tokens.json`) ו-4 צילומי מסך (`docs/screenshots/`)
- הקמת הריפו לפי תקן `repo-bootstrap` v1.0.0 (13/10/2026)
