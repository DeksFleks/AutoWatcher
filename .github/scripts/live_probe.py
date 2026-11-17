"""End-to-end public Auto.ru probe through the packaged application. Diagnostic, not a CI gate."""
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
    payload = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request("http://127.0.0.1:18083" + path, data=payload,
        method=method, headers={"Content-Type":"application/json","X-AutoWatcher-CSRF":csrf})
    with urllib.request.urlopen(req, timeout=180) as response:
        return json.load(response)
with tempfile.TemporaryDirectory(prefix="autowatcher-live-") as temporary:
    env = dict(os.environ, AUTOWATCHER_DATA=temporary, AUTOWATCHER_PORT="18083")
    log = open("target/live-probe-application.log","w+")
    app = subprocess.Popen(["java","-jar","target/autowatcher-0.1.0-SNAPSHOT.jar",
        "--app.background.enabled=false"], env=env, stdout=log, stderr=subprocess.STDOUT)
    summary = {"source":"AUTO_RU"}
    try:
        deadline = time.monotonic() + 60
        while True:
            try:
                if request("/actuator/health")["status"] == "UP": break
            except (OSError, urllib.error.URLError): pass
            if app.poll() is not None or time.monotonic() > deadline: raise RuntimeError("Startup failed")
            time.sleep(1)
        csrf = request("/api/v1/session")["csrfToken"]
        profile = request("/api/v1/profiles", {"name":"Public access probe","criteria":{},"sources":["AUTO_RU"]})
        base = "/api/v1/profiles/" + profile["id"]
        request(base+"/monitoring", {"intervalSeconds":300,"scheduled":False,
            "autoRuUrl":"https://auto.ru/cars/used/"}, "PUT")
        run = request(base+"/checks", {})[0]
        rows = request(base+"/results")
        summary.update(status=run["status"], error=run["errorMessage"], parsed_rows=len(rows),
            fields_present={key:sum(row.get(key) is not None for row in rows)
                for key in ["brand","model","year","price","mileage","region"]})
        summary["verified"] = run["status"] == "SUCCEEDED" and len(rows) > 0
    except Exception as error:
        summary.update(status="PROBE_ERROR",error=type(error).__name__,verified=False)
    finally:
        app.terminate()
        try: app.wait(timeout=10)
        except subprocess.TimeoutExpired: app.kill();app.wait(timeout=5)
        log.close()
    print(json.dumps(summary,ensure_ascii=False,indent=2))
    with open("target/live-source-probe.json","w",encoding="utf-8") as output:
        json.dump(summary,output,ensure_ascii=False,indent=2)
