#!/usr/bin/env bash
# שרת WebDAV לבדיקות שלב 5ג (Phase5cTest): Apache עם mod_dav על המארח, והאמולטור מגיע אליו ב-10.0.2.2.
#   http://10.0.2.2:8080/dav/ ו-https://10.0.2.2:8443/dav/ (תעודה עצמית — "לסמוך על השרת?")
#   משתמש qa / qa-pass (Basic). התעודה וטביעת האצבע שלה: certs/ (instrumented.sh דוחף אותן למכשיר).
# הבדיקות יוצרות ומוחקות את מה שבתוך dav/nexplorer-qa — כאן רק השרת והתיקייה.
set -euo pipefail

base=/tmp/nexplorer-dav
sudo rm -rf "$base"
mkdir -p "$base/dav" "$base/certs" "$base/run" "$base/logs"
# קבוע — לבדיקת העשן של release (release-smoke.sh).
echo "nexplorer release smoke" > "$base/dav/release-smoke-dav.txt"

openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=nexplorer-qa" \
  -addext "subjectAltName=DNS:nexplorer-qa" \
  -keyout "$base/certs/server.key" -out "$base/certs/server.crt" 2> /dev/null
# טביעת האצבע שהאפליקציה צריכה להציג לאישור (SHA-256, AB:CD:…) — הבדיקה משווה אליה.
openssl x509 -in "$base/certs/server.crt" -noout -fingerprint -sha256 | sed 's/^.*=//' > "$base/certs/fingerprint.txt"
htpasswd -bcB "$base/htpasswd" qa qa-pass 2> /dev/null

modules=/usr/lib/apache2/modules
# Apache לא רץ כ-root: ב-runner — המשתמש הנוכחי; בסביבה שבה הסקריפט רץ כ-root — www-data.
if [ "$(id -u)" -eq 0 ]; then
  owner=www-data
  chown -R "$owner" "$base/dav" "$base/run" "$base/logs"
else
  owner=$(id -un)
fi
cat > "$base/httpd.conf" <<EOF
ServerRoot "$base"
PidFile "$base/run/httpd.pid"
Mutex file:$base/run default
DefaultRuntimeDir "$base/run"
ErrorLog "$base/logs/error.log"
LogLevel warn
Listen 8080
Listen 8443
User $owner
Group $(id -gn "$owner")
ServerName nexplorer-qa
LoadModule mpm_event_module $modules/mod_mpm_event.so
LoadModule authn_core_module $modules/mod_authn_core.so
LoadModule authn_file_module $modules/mod_authn_file.so
LoadModule authz_core_module $modules/mod_authz_core.so
LoadModule authz_user_module $modules/mod_authz_user.so
LoadModule auth_basic_module $modules/mod_auth_basic.so
LoadModule alias_module $modules/mod_alias.so
LoadModule dav_module $modules/mod_dav.so
LoadModule dav_fs_module $modules/mod_dav_fs.so
LoadModule ssl_module $modules/mod_ssl.so
LoadModule socache_shmcb_module $modules/mod_socache_shmcb.so
DavLockDB "$base/run/davlock"
Alias /dav "$base/dav"
<Directory "$base/dav">
  Dav On
  AuthType Basic
  AuthName "nexplorer-qa"
  AuthUserFile "$base/htpasswd"
  Require valid-user
</Directory>
<VirtualHost *:8443>
  SSLEngine on
  SSLCertificateFile "$base/certs/server.crt"
  SSLCertificateKeyFile "$base/certs/server.key"
</VirtualHost>
EOF

apache2 -t -f "$base/httpd.conf" > /dev/null 2>&1 || { apache2 -t -f "$base/httpd.conf"; exit 1; }
apache2 -f "$base/httpd.conf" -k start

for _ in $(seq 1 20); do
  if (exec 3<>/dev/tcp/127.0.0.1/8443) 2>/dev/null && (exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
    echo "apache WebDAV is listening on 8080 and 8443 (certificate $(cat "$base/certs/fingerprint.txt"))"
    exit 0
  fi
  sleep 0.5
done
echo "::error::apache did not start"
cat "$base/logs/error.log" || true
exit 1
