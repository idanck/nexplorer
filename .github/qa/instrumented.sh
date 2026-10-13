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

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell rm -rf /sdcard/Download/nexplorer-qa
adb logcat -G 16M || true
adb logcat -c

run_class() {
  local cls="$1"
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
# --pct-anyevent 0: מקשים שרירותיים כוללים מקשי מערכת (ALL_APPS, SEARCH, ASSIST) שפותחים אפליקציות אחרות;
# ב-API 29 ALL_APPS השאיר את WindowManager ואת InputDispatcher חלוקים על הפוקוס, וכל קלט אחריו נכשל.
# זה לא באג של האפליקציה, ו-monkey כאן בודק את האפליקציה בלבד — כמו --pct-syskeys 0.
timeout 600 adb shell monkey -p "$pkg" -s 4242 --throttle 100 --pct-syskeys 0 --pct-anyevent 0 -v -v 2000 > "$out/monkey.txt" 2>&1
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

adb pull /sdcard/Download/nexplorer-qa/. "$out/screens/" > /dev/null || true
adb logcat -d > "$out/logcat.txt"
adb logcat -d -b crash > "$out/crash.txt" || true
if grep -q "$pkg" "$out/crash.txt"; then
  echo "::error::the app crashed on API $api (crash.txt)"
  status=1
fi

ls "$out/screens" | sed 's/^/  /'
exit $status
