<div dir="rtl">

# דף הבית ומדיניות הפרטיות — `nexplorer.idanck.com`

Cloudflare Worker ‏`nexplorer-site`. שני דפים סטטיים שגוגל דורשת ב-Branding כדי לפרסם את אפליקציית ה-OAuth ל-Production (פרויקט Google Cloud ‏`nexplorer-idanck`):

| נתיב | מה |
|---|---|
| `GET /` | דף הבית |
| `GET /privacy` | מדיניות הפרטיות (Drive: ‏`auth/drive`) |

שינוי בהרשאות שהאפליקציה מבקשת מחייב עדכון של מדיניות הפרטיות.

## פריסה (ידנית, מה-Pi)

</div>

```bash
cd site
# CLOUDFLARE_API_TOKEN ו-CLOUDFLARE_ACCOUNT_ID — מקובץ הסודות שמחוץ לריפו (הסקיל credentials-vault)
npx -y wrangler@4 deploy
```
