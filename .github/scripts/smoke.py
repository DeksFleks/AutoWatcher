"""Start the built JAR against a temporary SQLite database; check health and demo cycle."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        "http://127.0.0.1:18080" + path,
        data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)

with tempfile.TemporaryDirectory(prefix="autowatcher-smoke-") as temporary:
    env = dict(os.environ)
    env["AUTOWATCHER_DB"] = str(Path(temporary) / "smoke.db")
    env["AUTOWATCHER_PORT"] = "18080"
    with open(Path(temporary) / "application.log", "w+") as output:
        app = subprocess.Popen(
            ["java", "-jar", "target/autowatcher-0.1.0-SNAPSHOT.jar", "--spring.profiles.active=demo"],
            env=env, stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 90
            while True:
                if app.poll() is not None:
                    raise RuntimeError("Application exited during startup")
                try:
                    if request("/actuator/health")["status"] == "UP":
                        break
                except (OSError, urllib.error.URLError):
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Application did not become healthy")
                time.sleep(1)
            profile = request("/api/v1/profiles", {
                "name": "Smoke BMW",
                "criteria": {"brand": "BMW", "priceTo": 3000000},
                "sources": ["FAKE"]})
            base = "/api/v1/profiles/" + profile["id"]
            for _ in range(2):
                assert request(base + "/checks", {})[0]["status"] == "SUCCEEDED"
            assert len(request(base + "/results")) == 4
            assert len(request(base + "/outbox")) == 1
            assert request(base + "/outbox/dispatch-test", {})["deliveredToTestTransport"] == 1
            assert request(base + "/outbox/dispatch-test", {})["deliveredToTestTransport"] == 0
            print("Packaged application: health and fake monitoring cycle passed")
        except BaseException:
            output.flush()
            output.seek(0)
            print(output.read())
            raise
        finally:
            app.terminate()
            try:
                app.wait(timeout=15)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait(timeout=5)
