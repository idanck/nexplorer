<div dir="rtl">

# ערוץ העדכונים — `nexplorer-updates.idanck.com`

Cloudflare Worker ‏`nexplorer-updates` + bucket R2 ‏`nexplorer-updates`. נפרד מ-`updates.idanck.com` של TiviCopy (הפרדת blast radius, כמו ב-smartscan).
ערוץ משלו: האפליקציה מורידה ממנו build בלי לשאת סוד בתוך ה-APK ובלי תלות ב-GitHub.

| נתיב | מה |
|---|---|
| `GET /latest.json` | המניפסט: `versionCode`, `versionName`, `apkUrl`, `sha256`, `sizeBytes`, `minSdk`, `notes` |
| `GET /apk/<name>.apk` | ה-APK. ‏206 רק כשהבקשה ביקשה Range |
| `PUT /publish/<key>` | העלאה מה-CI. ‏`Authorization: Bearer <UPLOAD_TOKEN>`; רק `latest.json` ו-`apk/*` |

## מי מפרסם

ה-workflow `.github/workflows/ci.yml`, בכל push ל-`main` עם build חתום: מעלה את ה-APK, ואז את `latest.json`, ומאמת שהשרת מגיש את שניהם.
`versionCode` = מספר הריצה של ה-workflow (מונוטוני). אין bump ידני.

## פריסה (ידנית, מה-Pi)

</div>

```bash
cd <עותק של worker/ מהריפו>
# CLOUDFLARE_API_TOKEN ו-CLOUDFLARE_ACCOUNT_ID — מקובץ הסודות שמחוץ לריפו (הסקיל credentials-vault)
npx -y wrangler@4 deploy
# הטוקן כבר מוגדר; רק אם מחליפים אותו:
# printf '%s' "$NEXPLORER_UPDATES_UPLOAD_TOKEN" | npx -y wrangler@4 secret put UPLOAD_TOKEN --name nexplorer-updates
```

<div dir="rtl">

## סודות (שמות בלבד)

| איפה | שם | הערה |
|---|---|---|
| Worker | `UPLOAD_TOKEN` | זהה ל-`NEXPLORER_UPDATES_UPLOAD_TOKEN` בקובץ הסודות ול-GitHub Secret ‏`UPDATES_UPLOAD_TOKEN` |
| GitHub Secrets | `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `UPDATES_UPLOAD_TOKEN` | הוזנו 13/10/2026 |
| Pi | קובץ ה-keystore + `NEXPLORER_KEYSTORE_PASSWORD`, `NEXPLORER_KEYSTORE_B64` | ה-B64 זהה לקובץ, כך שהגיבוי של קובץ הסודות מכסה את המפתח |

**מפתח החתימה הוא זהות האפליקציה.** אובדנו = אי אפשר לעדכן התקנה קיימת, רק להסיר ולהתקין מחדש.
טביעת האצבע (SHA-256) נעוצה ב-CI: `30:1E:BD:26:5A:5B:5D:22:64:A6:97:54:9B:B6:D1:42:67:EA:66:F4:F2:1D:5F:BF:B6:6C:DC:92:98:88:0F:6F`.

</div>
