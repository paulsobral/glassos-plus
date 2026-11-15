#!/usr/bin/env bash
# CI only: installs the debug APK on a running Android TV emulator, points the lamp button at a fake local
# Home Assistant (10.0.2.2 is the host from inside the emulator) and saves screenshots to screenshots/.
set -euo pipefail
cd "$(dirname "$0")/.."
P=io.github.abhishekkaisar.glassos.personal
OUT=screenshots
mkdir -p "$OUT"

# A fake Home Assistant: one switch whose state flips on every toggle call.
cat > /tmp/fake_ha.py <<'PY'
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
state = {"on": True}
ENTITY = "switch.candeeiro_sala"
NAME = "Candeeiro sala"
class H(BaseHTTPRequestHandler):
    def _send(self, obj):
        b = json.dumps(obj).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def _entity(self):
        return {"entity_id": ENTITY, "state": "on" if state["on"] else "off", "attributes": {"friendly_name": NAME}}
    def do_GET(self):
        if self.path == "/api/states": self._send([self._entity(), {"entity_id": "light.cozinha", "state": "off", "attributes": {"friendly_name": "Cozinha"}}])
        elif self.path == "/api/states/" + ENTITY: self._send(self._entity())
        else: self.send_response(404); self.end_headers()
    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0); self.rfile.read(n)
        if self.path.endswith("/toggle"): state["on"] = not state["on"]
        self._send([self._entity()])
    def log_message(self, *a): print("fake HA:", self.command, self.path, flush=True)
HTTPServer(("0.0.0.0", 8123), H).serve_forever()
PY
python3 /tmp/fake_ha.py &
FAKE=$!
trap 'kill $FAKE 2>/dev/null || true' EXIT

adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk

cat > /tmp/glass.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="setupDone" value="true" />
    <string name="name">Paulo</string>
    <string name="haUrl">http://10.0.2.2:8123</string>
    <string name="haToken">ci-test-token</string>
    <string name="haEntity">switch.candeeiro_sala</string>
    <string name="haEntityName">Candeeiro sala</string>
</map>
XML
adb push /tmp/glass.xml /data/local/tmp/glass.xml
adb shell chmod 644 /data/local/tmp/glass.xml
adb shell run-as $P mkdir -p shared_prefs
adb shell run-as $P cp /data/local/tmp/glass.xml shared_prefs/glass.xml

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }
key() { adb shell input keyevent "$@"; sleep 1; }

adb shell am start -n $P/.MainActivity
shot 1-home-lamp-on 25
# From the dock: Up to the Top Shelf, Up to the pill, Left to the lamp.
key 19; key 19; key 21
shot 2-lamp-focused-on 2
key 23
shot 3-lamp-focused-off 4
key 23
shot 4-lamp-focused-on-again 4
# Long press OK on the lamp opens Settings; go down to the Home Assistant rows.
adb shell input keyevent --longpress 23
sleep 2
for i in $(seq 1 13); do adb shell input keyevent 20; done
shot 5-settings-home-assistant 2
adb shell dumpsys window | grep -m1 mCurrentFocus || true
