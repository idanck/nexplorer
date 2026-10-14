#!/usr/bin/env bash
# בדיקת עשן על build ‏release (R8 מכווץ ומשנה שמות): "הוסף מקום" ← SMB מול השרת של smb-server.sh,
# ואז SFTP מול sftp-server.sh.
# הבדיקות האחרות רצות על debug, בלי R8 — וספריית ה-SMB (smbj) מוצאת חלק מהקוד שלה ברפלקציה.
# כלל חסר ב-proguard-rules.pro נראה רק כאן: קריסה, או חיבור שלא עונה.
#   $1 — ה-APK. $2 — תיקיית התוצאות (צילומים, logcat).
set -uo pipefail

apk="${1:?usage: release-smoke.sh <release-apk> <out-dir>}"
out="${2:?usage: release-smoke.sh <release-apk> <out-dir>}"
mkdir -p "$out/screens"

pkg=com.idanck.nexplorer
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
ui() { python3 .github/qa/ui.py "$@"; }
shot() { adb exec-out screencap -p > "$out/screens/$1.png"; }
fail() {
  echo "::error::release smoke (API $api): $1"
  shot "release-failed"
  adb logcat -d > "$out/release-logcat.txt"
  exit 1
}
wait_for() {
  for _ in $(seq 1 "${2:-30}"); do
    ui has "$1" && return 0
    sleep 1
  done
  return 1
}

adb install -r "$apk" || fail "could not install $apk"
if [ "$api" -ge 30 ]; then
  adb shell appops set --uid "$pkg" MANAGE_EXTERNAL_STORAGE allow
else
  adb shell pm grant "$pkg" android.permission.READ_EXTERNAL_STORAGE
  adb shell pm grant "$pkg" android.permission.WRITE_EXTERNAL_STORAGE
fi
# בלי logcat -c: הסקריפט הראשי אוסף את ה-logcat של כל הבדיקות אחרי זה.
adb shell am start -W -n "$pkg/.MainActivity" > /dev/null

wait_for "פתח מגירת מקומות" || fail "the main screen did not open"
ui tap "פתח מגירת מקומות"
wait_for "הוסף מקום" 10 || fail "the drawer has no add place row"
ui tap "הוסף מקום"
wait_for "פרטי חיבור" 10 || fail "the add place screen did not open"

# המקלדת מכסה את השדות שמתחת לשדה הפעיל — מגוללים עד שהשדה על המסך (uiautomator לא רואה מה שמחוץ לו).
field() {
  for _ in 1 2 3 4; do
    if ui tap-desc "$1"; then
      adb shell input text "$2"
      return 0
    fi
    adb shell input swipe 540 1300 540 600 300
    sleep 1
  done
  return 1
}
field "שרת" 10.0.2.2 || fail "no server field"
field "משתמש" qa || fail "no user field"
field "סיסמה" qa-pass || fail "no password field"
shot "release-01-add-place"
ui tap "בדוק והתחבר" || fail "no connect button"
# רשימת השיתופים: smbj + dcerpc עברו את R8.
wait_for "second" 45 || fail "the share list did not appear"
shot "release-02-shares"
ui tap "qa"
wait_for "release-smoke.txt" 30 || fail "the qa share did not open"
shot "release-03-share"

# SFTP (שלב 5ב): sshj וההצפנה שלו (Ed25519 ו-X25519 מ-BouncyCastle ב-API 29) אחרי R8, מול sftp-server.sh.
ui tap "פתח מגירת מקומות" || fail "no drawer button (SFTP)"
wait_for "הוסף מקום" 10 || fail "the drawer has no add place row (SFTP)"
ui tap "הוסף מקום"
wait_for "פרטי חיבור" 10 || fail "the add place screen did not open (SFTP)"
ui tap "SFTP" || fail "no SFTP tile"
wait_for "מפתח פרטי" 10 || fail "the SFTP fields did not appear"
field "שרת" 10.0.2.2 || fail "no server field (SFTP)"
field "משתמש" qa || fail "no user field (SFTP)"
field "סיסמה" qa-pass || fail "no password field (SFTP)"
ui tap "מתקדם" || fail "no advanced button"
field "פורט" 2222 || fail "no port field"
shot "release-04-add-sftp"
ui tap "בדוק והתחבר" || fail "no connect button (SFTP)"
wait_for "לסמוך על השרת?" 45 || fail "the host key question did not appear"
shot "release-05-trust-host"
ui tap "סמוך והתחבר" || fail "no trust button"
wait_for "release-smoke-sftp.txt" 45 || fail "the SFTP home did not open"
shot "release-06-sftp-home"

adb logcat -d -b crash > "$out/release-crash.txt" || true
# "Process: com.idanck.nexplorer," — ה-release בלבד, לא ה-debug (‎.debug) שרץ לפניו.
if grep -q "Process: $pkg," "$out/release-crash.txt"; then
  fail "the release build crashed (release-crash.txt)"
fi
adb shell am force-stop "$pkg"
echo "release smoke passed on API $api"
