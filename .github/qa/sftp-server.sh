#!/usr/bin/env bash
# שרת SFTP לבדיקות שלב 5ב (Phase5bTest): OpenSSH על המארח בפורט 2222, והאמולטור מגיע אליו ב-10.0.2.2.
#   משתמש qa / qa-pass (רק SFTP — ForceCommand internal-sftp) · הבית: /tmp/nexplorer-sftp/home
#   מפתחות משתמש (ssh-keygen, Ed25519): keys/qa_ed25519 בלי סיסמה, keys/qa_ed25519_pass עם "key-pass".
# הבדיקות יוצרות ומוחקות את מה שבתוך home/nexplorer-qa — כאן רק השרת, הבית והמפתחות.
set -euo pipefail

base=/tmp/nexplorer-sftp
sudo rm -rf "$base"
mkdir -p "$base/home" "$base/keys" "$base/authorized"
# קבוע — לבדיקת העשן של release (release-smoke.sh).
echo "nexplorer release smoke" > "$base/home/release-smoke-sftp.txt"

ssh-keygen -q -t ed25519 -N '' -C nexplorer-qa-host -f "$base/host_ed25519"
ssh-keygen -q -t ed25519 -N '' -C qa -f "$base/keys/qa_ed25519"
ssh-keygen -q -t ed25519 -N 'key-pass' -C qa-pass -f "$base/keys/qa_ed25519_pass"
cat "$base/keys/qa_ed25519.pub" "$base/keys/qa_ed25519_pass.pub" > "$base/authorized/qa"
# טביעת האצבע שהאפליקציה צריכה להציג לאישור — הבדיקה משווה אליה.
ssh-keygen -l -E sha256 -f "$base/host_ed25519.pub" | awk '{print $2}' > "$base/keys/host-fingerprint.txt"

# smb-server.sh כבר יצר את qa (בלי בית); ל-SSH הוא צריך בית וסיסמה של המערכת.
id qa > /dev/null 2>&1 || sudo useradd -M -s /usr/sbin/nologin qa
sudo usermod -d "$base/home" qa
echo 'qa:qa-pass' | sudo chpasswd
sudo chown -R qa "$base/home"

cat > "$base/sshd_config" <<EOF
Port 2222
ListenAddress 0.0.0.0
HostKey $base/host_ed25519
PidFile $base/sshd.pid
AuthorizedKeysFile $base/authorized/%u
PasswordAuthentication yes
KbdInteractiveAuthentication no
PubkeyAuthentication yes
UsePAM no
PermitRootLogin no
# המפתחות ב-/tmp, שבעלים שלו אינו המשתמש.
StrictModes no
AllowUsers qa
Subsystem sftp internal-sftp
ForceCommand internal-sftp
LogLevel VERBOSE
EOF

sudo mkdir -p /run/sshd
sudo /usr/sbin/sshd -t -f "$base/sshd_config"
sudo /usr/sbin/sshd -f "$base/sshd_config" -E "$base/sshd.log"

for _ in $(seq 1 20); do
  if (exec 3<>/dev/tcp/127.0.0.1/2222) 2>/dev/null; then
    echo "sshd is listening on 2222 (host key $(cat "$base/keys/host-fingerprint.txt"))"
    exit 0
  fi
  sleep 0.5
done
echo "::error::sshd did not start"
sudo cat "$base/sshd.log" || true
exit 1
