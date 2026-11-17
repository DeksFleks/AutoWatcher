"""Run bundled Windows runtime twice without system Java; verify persisted profile/settings."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

csrf = ""
def request(path, body=None, method=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request("http://127.0.0.1:18082" + path, data=data, method=method,
        headers={"Content-Type":"application/json","X-AutoWatcher-CSRF":csrf})
    with urllib.request.urlopen(req, timeout=5) as response:
        text = response.read()
        return json.loads(text) if text else None

executable = Path("target/desktop/AutoWatcher/AutoWatcher.exe").resolve()
assert executable.is_file()
assert (executable.parent / "runtime" / "bin" / "java.dll").is_file()
with tempfile.TemporaryDirectory(prefix="autowatcher-portable-") as temporary:
    env = dict(os.environ)
    env.pop("JAVA_HOME", None)
    env["PATH"] = env.get("SystemRoot", "C:/Windows") + "/System32"
    env["AUTOWATCHER_DATA"] = temporary
    env["AUTOWATCHER_PORT"] = "18082"
    profile_id = None
    for attempt in range(2):
        process = subprocess.Popen([str(executable), "--app.desktop=false"], env=env)
        try:
            deadline = time.monotonic() + 90
            while True:
                if process.poll() is not None: raise RuntimeError("Portable app exited early")
                try:
                    if request("/actuator/health")["status"] == "UP": break
                except (OSError, urllib.error.URLError): pass
                if time.monotonic() >= deadline: raise RuntimeError("Portable app did not start")
                time.sleep(1)
            csrf = request("/api/v1/session")["csrfToken"]
            if attempt == 0:
                profile = request("/api/v1/profiles", {"name":"Persisted Windows profile","criteria":{},"sources":["AUTO_RU"]})
                profile_id = profile["id"]
                request("/api/v1/profiles/"+profile_id+"/monitoring", {
                    "intervalSeconds":120,"scheduled":False,"autoRuUrl":"https://auto.ru/cars/used/"}, "PUT")
                request("/api/v1/settings/retention", {"days":45}, "PUT")
            else:
                assert request("/api/v1/profiles/"+profile_id)["name"] == "Persisted Windows profile"
                assert request("/api/v1/profiles/"+profile_id+"/monitoring")["intervalSeconds"] == 120
                assert request("/api/v1/settings")["retentionDays"] == 45
                print("Portable app: bundled Java, restart, persisted profiles/settings passed")
        finally:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill();process.wait(timeout=5)
