"""Verify the jpackage image runs with JAVA_HOME unset and an empty PATH."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

executable = Path("target/desktop/AutoWatcher/AutoWatcher.exe").resolve()
assert executable.is_file()
assert (executable.parent / "runtime" / "bin" / "java.dll").is_file()
with tempfile.TemporaryDirectory(prefix="autowatcher-portable-") as temporary:
    env = dict(os.environ)
    env.pop("JAVA_HOME", None)
    env["PATH"] = env.get("SystemRoot", "C:/Windows") + "/System32"
    env["AUTOWATCHER_DATA"] = temporary
    env["AUTOWATCHER_PORT"] = "18082"
    process = subprocess.Popen([str(executable), "--app.desktop=false"], env=env)
    try:
        deadline = time.monotonic() + 90
        while True:
            if process.poll() is not None:
                raise RuntimeError("Portable app exited early")
            try:
                with urllib.request.urlopen("http://127.0.0.1:18082/actuator/health", timeout=5) as response:
                    if json.load(response)["status"] == "UP":
                        break
            except (OSError, urllib.error.URLError):
                pass
            if time.monotonic() >= deadline:
                raise RuntimeError("Portable app did not start")
            time.sleep(1)
        print("Windows app-image started without JAVA_HOME or system Java on PATH")
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
