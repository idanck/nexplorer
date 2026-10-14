#!/usr/bin/env bash
# QA על אמולטור — רץ בתוך reactivecircus/android-emulator-runner, אחרי שהאמולטור עלה.
#   1. בדיקת מסך הגישה, על התקנה טרייה (בלי הרשאה).
#   2. מסלולי מסך הקבצים, חיפוש והגדרות (הבדיקות נותנות לעצמן הרשאה).
#   3. monkey: אירועים אקראיים בתוך האפליקציה בלבד — קריסה או ANR = כשל.
# הכל נאסף ל-$1: צילומי מסך, פלט הבדיקות, logcat. היציאה לא 0 אם משהו נכשל.
set -uo pipefail

out="${1:?usage: instrumented.sh <out-dir>}"
mkdir -p "$out/screens"

pkg=com.idanck.nexplorer.debug
runner="$pkg.test/androidx.test.runner.AndroidJUnitRunner"
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
status=0

# אמולטור עמוס: אפליקציות מערכת (Pixel Launcher) נתקעות, וחלון "isn't responding" שלהן מכסה את
# האפליקציה — ב-API 35 כל הבדיקות נכשלו בגללו. בלי חלונות שגיאה המערכת הורגת את התוקעת; ANR או
# קריסה של האפליקציה עצמה עדיין נתפסים בסוף מה-logcat (am_anr, crash). ראשון, לפני ההתקנות וההמתנה
# לעומס: ה-launcher נתקע גם בזמן הזה, וחלון שכבר נפתח לא נסגר כשההגדרה נכנסת לתוקף.
adb shell settings put global hide_error_dialogs 1 || true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# אנדרואיד 15 הורג תהליך שלא סיים לעלות תוך ~30 שניות ("failed to complete startup"). מיד אחרי
# האתחול האמולטור עמוס (gms, dexopt), ואימות ה-dex של שני ה-APK-ים (debug, בלי AOT) בהפעלה
# הראשונה גלש מעבר לזה — לפני שקוד האפליקציה בכלל רץ. מאמתים מראש, ומחכים שהעומס ירד.
adb shell cmd package compile -m verify -f "$pkg" > /dev/null || true
adb shell cmd package compile -m verify -f "$pkg.test" > /dev/null || true
for _ in $(seq 1 60); do
  # "some avg10=NN.NN" — אחוז הזמן שתהליכים חיכו ל-CPU. אין /proc/pressure (API 29) — לא מחכים.
  load=$(adb shell cat /proc/pressure/cpu 2>/dev/null | tr -d '\r' | awk '/^some/ { split($2, a, "="); print int(a[2]) }')
  { [ -z "$load" ] || [ "$load" -lt 40 ]; } && break
  sleep 2
done
echo "cpu pressure before tests: ${load:-n/a}"
adb shell rm -rf /sdcard/Download/nexplorer-qa
adb logcat -G 16M || true
adb logcat -c

# חלון "isn't responding" של אפליקציה אחרת שכבר פתוח (נפתח לפני hide_error_dialogs, או למרות
# ההגדרה): נשאר מעל האפליקציה עד שהתהליך שלו נהרג — וכל הבדיקות אחריו נכשלות. של האפליקציה שלנו
# לא נוגעים: הבדיקה צריכה להיכשל, ו-anr.txt מדווח עליו.
clear_foreign_anr_dialogs() {
  adb shell dumpsys window windows < /dev/null | tr -d '\r' \
    | sed -n 's/.*Application Not Responding: \([A-Za-z0-9._]*\).*/\1/p' | sort -u \
    | while read -r stuck; do
        [ "$stuck" = "$pkg" ] && continue
        echo "::warning::closing the ANR dialog of $stuck (API $api)"
        adb shell am force-stop "$stuck" < /dev/null || true
      done
}

run_class() {
  local cls="$1"
  clear_foreign_anr_dialogs
  echo "::group::$cls (API $api)"
  adb shell am instrument -w -e class "com.idanck.nexplorer.qa.$cls" "$runner" 2>&1 | tee "$out/$cls.txt"
  echo "::endgroup::"
  if ! grep -q '^OK (' "$out/$cls.txt"; then
    echo "::error::$cls failed on API $api"
    status=1
  fi
}

run_class PermissionGateTest
run_class BrowserFlowTest
run_class SearchAndSettingsTest
run_class Phase2Test
run_class Phase2bTest
run_class Phase3Test
run_class Phase3bTest
run_class Phase4Test
run_class Phase4bTest
run_class Phase4cTest
run_class Phase5aTest
# המפתחות של sftp-server.sh (וטביעת האצבע של השרת) — Phase5bTest קורא אותם מהמכשיר.
if [ -d /tmp/nexplorer-sftp/keys ]; then
  adb shell mkdir -p /sdcard/nexplorer-qa-keys
  adb push /tmp/nexplorer-sftp/keys/. /sdcard/nexplorer-qa-keys/ > /dev/null
fi
run_class Phase5bTest
# טביעת האצבע של התעודה העצמית של webdav-server.sh — Phase5cTest משווה אליה.
if [ -f /tmp/nexplorer-dav/certs/fingerprint.txt ]; then
  adb shell mkdir -p /sdcard/nexplorer-qa-keys
  adb push /tmp/nexplorer-dav/certs/fingerprint.txt /sdcard/nexplorer-qa-keys/dav-fingerprint.txt > /dev/null
fi
run_class Phase5cTest
# Phase2bTest מדמה טאבלט ו-Phase3bTest מסובב; אם התהליך קרס לפני tearDown — שהמונקי ירוץ על המסך הרגיל.
adb shell wm size reset || true
adb shell wm density reset || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 0 || true

echo "::group::monkey (API $api)"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell am start -W -n "$pkg/com.idanck.nexplorer.MainActivity" \
  -a com.idanck.nexplorer.action.OPEN_FOLDER --es path /sdcard/nexplorer-qa > /dev/null
# איזה חלון מחזיק את הפוקוס, כל 15 שניות: "Injection Failed" רצוף = אף חלון לא מקבל קלט, וזה מראה מי.
( while true; do
    echo "$(date +%T) $(adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | tr -s ' ' | tr '\n' ' ')"
    sleep 15
  done ) > "$out/monkey-focus.txt" 2>&1 &
watch=$!
# תקרת זמן: monkey שנתקע לא שורף את כל ה-job, ו-logcat וצילומי המסך עדיין נאספים.
# -v -v: גם המקשים נרשמים — כך נמצא שמקש MENU תקע את הקלט ב-API 29.
timeout 600 adb shell monkey -p "$pkg" -s 4242 --throttle 100 --pct-syskeys 0 -v -v 2000 > "$out/monkey.txt" 2>&1
monkey_exit=$?
kill $watch 2>/dev/null
tail -n 5 "$out/monkey.txt"
echo "::endgroup::"
if [ $monkey_exit -eq 124 ]; then
  echo "::error::monkey did not finish within 10 minutes on API $api (monkey-focus.txt)"
  adb shell pkill -f com.android.commands.monkey || true
  status=1
elif grep -qE 'CRASH|NOT RESPONDING' "$out/monkey.txt" || ! grep -q 'Monkey finished' "$out/monkey.txt"; then
  echo "::error::monkey found a crash or ANR on API $api"
  status=1
fi
adb exec-out screencap -p > "$out/screens/90-after-monkey.png"

# R8: ה-release המכווץ מתחבר ל-SMB (שלב 5א). רק אם נבנה — ב-workflow ‏QA.
if [ -f app/build/outputs/apk/release/app-release.apk ]; then
  echo "::group::release smoke (API $api)"
  bash .github/qa/release-smoke.sh app/build/outputs/apk/release/app-release.apk "$out" || status=1
  echo "::endgroup::"
fi

adb pull /sdcard/Download/nexplorer-qa/. "$out/screens/" > /dev/null || true
adb logcat -d > "$out/logcat.txt"
adb logcat -d -b crash > "$out/crash.txt" || true
if grep -q "$pkg" "$out/crash.txt"; then
  echo "::error::the app crashed on API $api (crash.txt)"
  status=1
fi
adb logcat -d -b events -s am_anr > "$out/anr.txt" || true
# הצד של השרת: חיבור שנתקע ב"מתחבר…" — האם הגיע ל-sshd, ואיפה נעצר (sftp-server.sh, ‏LogLevel VERBOSE).
sudo cat /tmp/nexplorer-sftp/sshd.log > "$out/sshd.log" 2>/dev/null || true
if grep -q "$pkg" "$out/anr.txt"; then
  echo "::error::the app stopped responding on API $api (anr.txt)"
  status=1
fi

ls "$out/screens" | sed 's/^/  /'
exit $status
