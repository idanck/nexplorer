#!/usr/bin/env bash
# שרת SMB לבדיקות שלב 5א (Phase5aTest): Samba על המארח, והאמולטור מגיע אליו ב-10.0.2.2.
#   משתמש qa / qa-pass · שיתופים qa ו-second · פורטים 445 (ברירת המחדל) ו-4450.
# הבדיקות עצמן יוצרות ומוחקות את מה שבתוך qa/nexplorer-qa — כאן רק השרת והשיתופים.
set -euo pipefail

# נתיב קצר: ה-sockets של samba-dcerpcd (רשימת השיתופים) מוגבלים ל-108 תווים.
base=/tmp/nexplorer-smb
sudo rm -rf "$base"
mkdir -p "$base/qa" "$base/second" "$base/state/ncalrpc"
# קבוע — לבדיקת העשן של release (release-smoke.sh). Phase5aTest נוגעת רק ב-qa/nexplorer-qa.
echo "nexplorer release smoke" > "$base/qa/release-smoke.txt"
# ההתקנה מפעילה smbd של המערכת על 445 — עוצרים, כדי שהשרת שלנו יקבל את הפורט.
sudo systemctl stop smbd nmbd 2>/dev/null || true
sudo systemctl disable smbd nmbd 2>/dev/null || true

cat > "$base/smb.conf" <<EOF
[global]
  server role = standalone server
  smb ports = 445 4450
  server min protocol = SMB2_02
  map to guest = never
  disable netbios = yes
  load printers = no
  printcap name = /dev/null
  state directory = $base/state
  cache directory = $base/state
  lock directory = $base/state
  private dir = $base/state
  pid directory = $base/state
  ncalrpc dir = $base/state/ncalrpc
  log file = $base/state/log.%m
[qa]
  path = $base/qa
  read only = no
  valid users = qa
  force user = root
[second]
  path = $base/second
  read only = no
  valid users = qa
  force user = root
EOF

id qa > /dev/null 2>&1 || sudo useradd -M -s /usr/sbin/nologin qa
printf 'qa-pass\nqa-pass\n' | sudo smbpasswd -c "$base/smb.conf" -s -a qa
sudo smbd -s "$base/smb.conf" -D

for _ in $(seq 1 20); do
  if (exec 3<>/dev/tcp/127.0.0.1/445) 2>/dev/null; then
    echo "smbd is listening on 445 and 4450"
    exit 0
  fi
  sleep 0.5
done
echo "::error::smbd did not start"
sudo cat "$base"/state/log.* || true
exit 1
