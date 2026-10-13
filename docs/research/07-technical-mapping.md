<div dir="rtl">

# 07 — מיפוי טכני: יכולת ← אפשרויות מימוש באנדרואיד

רשימת אפשרויות קיימות — לא בחירה. ההכרעות ב-[08](08-open-decisions.md).
גרסאות ספריות לא צוינו בכוונה — לקבוע בזמן הבנייה מול המקור (כמו בתקן repo-bootstrap).
שורות עם [לאמת] — לבדוק לפני שמסתמכים.

## גישה לקבצים

| צורך | אפשרויות |
|---|---|
| כל האחסון הפנימי | `MANAGE_EXTERNAL_STORAGE` (All files access, Android 11+) — ב-Play מחייב הצהרה; מנהלי קבצים הם שימוש מותר · לחלופין SAF בלבד |
| SD / USB OTG | SAF — `ACTION_OPEN_DOCUMENT_TREE` + `DocumentFile` |
| `Android/data`, `Android/obb` | חסומים ל-SAF מ-Android 11 → Shizuku (shell uid) או root |
| Root | libsu · הפעלת פקודות עם remount (זרימה F12) |
| אוספים (תמונות/מוזיקה/וידאו/הורדות) | `MediaStore` |
| נפח אחסון | `StorageStatsManager`, `StatFs` |
| גודל קריא לפי שפה | `Formatter.formatShortFileSize` |

## רשת

| פרוטוקול | ספריות קיימות |
|---|---|
| SMB 2/3 | smbj · jcifs-ng |
| SFTP | sshj · JSch (fork מתוחזק) |
| FTP / FTPS (לקוח) | Apache Commons Net |
| WebDAV / Nextcloud | sardine-android · OkHttp ידני · Nextcloud Android Library |
| S3 | AWS SDK for Kotlin · MinIO Java SDK |
| NFS | [לאמת — לא נבדקה ספרייה] |
| גילוי שרתים ב-LAN | `NsdManager` (mDNS) [לאמת ש-NAS מפרסם] |

## ענן

Google Drive REST v3 · Microsoft Graph (OneDrive) · Dropbox SDK · Box SDK · pCloud API · Nextcloud (WebDAV). כניסה — OAuth דרך Custom Tabs / Credential Manager.

## ארכיונים

| פורמט | אפשרויות |
|---|---|
| ZIP כולל AES | zip4j |
| TAR / GZ / BZ2 / 7z | Apache Commons Compress |
| RAR / RAR5 | 7-Zip-JBinding (native) · junrar — [לאמת תמיכת RAR5 בכל אחת] |

## אבטחה

| צורך | אפשרויות |
|---|---|
| הצפנת קבצים AES-256 | Tink (Streaming AEAD) · `javax.crypto` AES-GCM + Android Keystore · `androidx.security-crypto` סומנה deprecated [לאמת סטטוס] |
| טביעת אצבע | `BiometricPrompt` + מפתח Keystore שדורש אימות |
| סודות חיבור (SMB/SFTP) | הצפנה ב-Keystore; לא ב-plaintext ב-DataStore |
| Checksums | `MessageDigest` (MD5, SHA-1, SHA-256) |

## מדיה

| צורך | אפשרויות |
|---|---|
| נגן וידאו/אודיו | Media3 (ExoPlayer + MediaSession) |
| Chromecast | Cast SDK (`play-services-cast-framework`) · Media3 Cast |
| תמונות ו-thumbnails | Coil · `MediaMetadataRetriever` לפריימים · `ContentResolver.loadThumbnail` |
| עורך טקסט | Compose `BasicTextField` · רכיב עורך קוד חיצוני |

## שרתים מקומיים

| צורך | אפשרויות |
|---|---|
| FTP / FTPS server | Apache FtpServer (MINA) |
| Web share | Ktor server (CIO) · NanoHTTPD |

## פעולות ורקע

| צורך | אפשרויות |
|---|---|
| העתקות ארוכות ניתנות להמשכה | WorkManager + Foreground Service (`dataSync`) + checkpoint לכל קובץ/offset. מגבלות FGS משתנות בין גרסאות אנדרואיד [לאמת מול targetSdk] |
| אינדקס חיפוש | Room + FTS4 · FileObserver/סריקה תקופתית |
| קיצורים | `ShortcutManagerCompat` |

## ממשק

| צורך | אפשרויות |
|---|---|
| UI | Jetpack Compose + Material 3 (כולל רכיבי Expressive) · Views + MDC |
| Dynamic color | `dynamicLightColorScheme` / `dynamicDarkColorScheme` (Android 12+) |
| Dual-pane אדפטיבי | Material 3 Adaptive (גודל חלון) · פריסה ידנית |
| Drag & drop | Compose `dragAndDropSource` / `dragAndDropTarget` |
| RTL / BiDi | `LayoutDirection` מ-locale · `BidiFormatter` · תווי בידוד FSI U+2068 / PDI U+2069 למספר+יחידה |
| העדפות | DataStore |

## מונטיזציה (אם רלוונטי)

Play Billing Library · Google Mobile Ads (native ads).

</div>
