#!/usr/bin/env bash
# בדיקת עשן על build ‏release (R8 מכווץ ומשנה שמות): "הוסף מקום" ← SMB מול השרת של smb-server.sh.
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

ui tap-desc "שרת" && adb shell input text 10.0.2.2
ui tap-desc "משתמש" && adb shell input text qa
ui tap-desc "סיסמה" && adb shell input text qa-pass
shot "release-01-add-place"
ui tap "בדוק והתחבר" || fail "no connect button"
# רשימת השיתופים: smbj + dcerpc עברו את R8.
wait_for "second" 45 || fail "the share list did not appear"
shot "release-02-shares"
ui tap "qa"
wait_for "release-smoke.txt" 30 || fail "the qa share did not open"
shot "release-03-share"

adb logcat -d -b crash > "$out/release-crash.txt" || true
# "Process: com.idanck.nexplorer," — ה-release בלבד, לא ה-debug (‎.debug) שרץ לפניו.
if grep -q "Process: $pkg," "$out/release-crash.txt"; then
  fail "the release build crashed (release-crash.txt)"
fi
adb shell am force-stop "$pkg"
echo "release smoke passed on API $api"
