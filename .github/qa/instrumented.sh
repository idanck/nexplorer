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
  # "some avg10=NN.NN" — אחוז הזמן שתהליכים חיכו ל-CPU. בלי /proc/pressure (קרנל ישן) — לא מחכים.
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
  # תקרת זמן, כמו ל-monkey: בדיקה שנתקעת לא שורפת את כל ה-job (‏t10 של BrowserFlowTest נתקע ב-API 30
  # ‏~37 דקות, ‏PR #34), המחלקות הבאות עוד רצות, ו-logcat נאסף בסוף. הארוכה ביותר לוקחת ~4 דקות.
  timeout 600 adb shell am instrument -w -e class "com.idanck.nexplorer.qa.$cls" "$runner" 2>&1 | tee "$out/$cls.txt"
  local rc=${PIPESTATUS[0]}
  echo "::endgroup::"
  if [ "$rc" -eq 124 ]; then
    echo "::error::$cls did not finish within 10 minutes on API $api (screens/hang-$cls.png, hang-$cls.txt, hang-$cls-stacks.txt)"
    adb exec-out screencap -p > "$out/screens/hang-$cls.png" || true
    { timeout 30 adb shell dumpsys window < /dev/null | grep -E 'mCurrentFocus|mFocusedApp|mRotation|mDisplayRotation'
      timeout 30 adb shell dumpsys activity activities < /dev/null | grep -E 'ResumedActivity|mLastPausedActivity'
    } > "$out/hang-$cls.txt" 2>&1 || true
    # איפה נתקע: המחסניות של כל החוטים (חוט הבדיקה, ה-main). ‏SIGQUIT כותב אותן ל-/data/anr, שנקרא רק ב-root
    # (תמונת google_apis). אחר כך חוזרים ל-adbd רגיל, כמו בשאר הריצה.
    local pid
    pid=$(timeout 30 adb shell pidof "$pkg" < /dev/null | tr -d '\r')
    if [ -n "$pid" ] && timeout 60 adb root < /dev/null > /dev/null 2>&1; then
      sleep 3
      timeout 60 adb wait-for-device < /dev/null || true
      timeout 30 adb shell kill -3 "$pid" < /dev/null || true
      sleep 5
      timeout 30 adb shell 'cat "$(ls -t /data/anr/trace_* | head -n 1)"' < /dev/null > "$out/hang-$cls-stacks.txt" 2>&1 || true
      timeout 60 adb unroot < /dev/null > /dev/null 2>&1 || true
      sleep 3
      timeout 60 adb wait-for-device < /dev/null || true
    fi
    # ה-instrumentation רץ בתהליך של האפליקציה, ו-timeout סוגר רק את צד ה-adb: עוצרים אותו לפני המחלקה הבאה.
    adb shell am force-stop "$pkg" < /dev/null || true
    # ה-tearDown לא רץ: מחלקה שנתקעה באמצע מצב טאבלט (Phase2bTest) או סיבוב השאירה אותו, וכל המחלקות אחריה
    # רצו בשני חלונות ונכשלו ("Failed to inject touch input", ‏QA ‏132). מחזירים את המסך לפני המחלקה הבאה.
    adb shell wm size reset < /dev/null || true
    adb shell wm density reset < /dev/null || true
    adb shell settings put system accelerometer_rotation 0 < /dev/null || true
    adb shell settings put system user_rotation 0 < /dev/null || true
    status=1
  elif ! grep -q '^OK (' "$out/$cls.txt"; then
    echo "::error::$cls failed on API $api"
    status=1
  fi
}

run_class PermissionGateTest
run_class BrowserFlowTest
run_class SearchAndSettingsTest
run_class Phase2Test
run_class Phase2bTest
# גיליון ה-APK פותח את מתקין המערכת. ב-Android 11 (API 30) שינוי של REQUEST_INSTALL_PACKAGES הורג את
# התהליך — ובאמצע הבדיקה זה "Process crashed" ("Killing …: REQUEST_INSTALL_PACKAGES changed"). כמו משתמש
# שכבר אישר, ולפני שהתהליך עולה (כמו ב-update-e2e.sh).
adb shell appops set "$pkg" REQUEST_INSTALL_PACKAGES allow || true
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
# שלב 6: Google Drive ו-OneDrive מול cloud-server.py על המארח (10.0.2.2:8090).
run_class Phase6Test
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
# הבקשות שהשרת המדומה של הענן קיבל (Phase6Test).
cat /tmp/nexplorer-cloud.log > "$out/cloud-server.log" 2>/dev/null || true
if grep -q "$pkg" "$out/anr.txt"; then
  echo "::error::the app stopped responding on API $api (anr.txt, anr-traces.txt)"
  # המחסנית של ה-main בזמן ה-ANR: ‏DropBox שומר אותה (data_app_anr), בלי root. בלעדיה רואים רק "Waited 5012ms".
  adb shell dumpsys dropbox --print data_app_anr > "$out/anr-traces.txt" 2>&1 || true
  # מצב ה-InputDispatcher ברגע ה-ANR ("Input Dispatcher State at time of last ANR"): החלון שבפוקוס, ואיזה אירוע
  # מחכה בתור של איזה חלון. בלי זה רואים רק "Waited 5012ms" ולא איפה.
  adb shell dumpsys input > "$out/anr-input.txt" 2>&1 || true
  grep -m 3 -E 'Subject:|Reason:' "$out/anr-traces.txt" || true
  sed -n '/Input Dispatcher State at time of last ANR/,/^  Configuration:/p' "$out/anr-input.txt" | head -n 80 || true
  status=1
fi

ls "$out/screens" | sed 's/^/  /'
exit $status
