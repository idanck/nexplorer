#!/usr/bin/env bash
# עדכון מתוך האפליקציה, מקצה לקצה, מול הערוץ האמיתי (D5):
# מתקין build ‏release חתום ב-versionCode 1, פותח, לוחץ "עדכן" בבאנר, ומחכה שהמערכת
# תחליף אותו בגרסה ש-latest.json מפרסם — בלי חלון אישור (UPDATE_PACKAGES_WITHOUT_USER_ACTION).
set -uo pipefail

apk="${1:?usage: update-e2e.sh <old-apk> <out-dir>}"
out="${2:?usage: update-e2e.sh <old-apk> <out-dir>}"
mkdir -p "$out"

pkg=com.idanck.nexplorer
host="${UPDATES_HOST:-https://nexplorer-updates.idanck.com}"
want=$(curl -fsS "$host/latest.json" | jq -r .versionCode) || { echo "::error::no published manifest at $host"; exit 1; }

code() { adb shell dumpsys package "$pkg" | grep -m1 -o 'versionCode=[0-9]*' | cut -d= -f2; }
shot() { adb exec-out screencap -p > "$out/$1.png"; }
ui() { python3 .github/qa/ui.py "$@"; }
fail() {
  echo "::error::$1"
  adb logcat -d > "$out/logcat.txt"
  exit 1
}

adb install -r "$apk" || fail "could not install the old build"
echo "installed $(code), published $want"
[ "$(code)" -lt "$want" ] || fail "the old build is not older than the published one"
# כמו משתמש שכבר אישר: גישה לקבצים והתקנה מהאפליקציה. (שינוי REQUEST_INSTALL_PACKAGES הורג את התהליך — לכן לפני הפתיחה.)
adb shell appops set --uid "$pkg" MANAGE_EXTERNAL_STORAGE allow
adb shell appops set "$pkg" REQUEST_INSTALL_PACKAGES allow
adb logcat -c
# הבדיקה היומית רצה בפתיחה, ונכשלת בשקט כשאין רשת (מנסה שוב בחזרה הבאה לאפליקציה). מיד אחרי
# האתחול הרשת של האמולטור עוד לא מאומתת — מחכים לה, כמו טלפון שכבר מחובר.
for _ in $(seq 1 60); do
  adb shell dumpsys connectivity 2>/dev/null | grep -q '&VALIDATED' && break
  sleep 2
done
adb shell am start -W -n "$pkg/.MainActivity" > /dev/null

found=0
for i in $(seq 1 30); do
  if ui has "עדכן"; then found=1; break; fi
  # עדיין כלום אחרי חצי דקה: יציאה וחזרה (ON_RESUME) — מה שמשתמש היה עושה.
  if [ "$i" -eq 15 ]; then
    adb shell input keyevent KEYCODE_HOME
    sleep 1
    adb shell am start -W -n "$pkg/.MainActivity" > /dev/null
  fi
  sleep 2
done
shot 01-banner
[ $found -eq 1 ] || fail "the update banner did not appear"

ui tap "עדכן" || fail "could not tap the update button"
ok=0
for i in $(seq 1 90); do
  if [ "$(code)" = "$want" ]; then ok=1; break; fi
  [ "$i" -eq 4 ] && shot 02-installing
  sleep 2
done
shot 03-after-install
[ $ok -eq 1 ] || fail "still on versionCode $(code), expected $want (see 02-installing.png)"

adb shell am start -W -n "$pkg/.MainActivity" > /dev/null
sleep 3
shot 04-relaunched
adb logcat -d > "$out/logcat.txt"
echo "updated 1 -> $want without a confirmation dialog"
