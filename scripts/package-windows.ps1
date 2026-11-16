$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$inputDir = Join-Path $root 'target/package-input'
$destination = Join-Path $root 'target/desktop'
New-Item -ItemType Directory -Force $inputDir | Out-Null
Copy-Item 'target/autowatcher-0.1.0-SNAPSHOT.jar' (Join-Path $inputDir 'autowatcher.jar') -Force
if (Test-Path $destination) { Remove-Item $destination -Recurse -Force }
& "$env:JAVA_HOME/bin/jpackage.exe" --type app-image --name AutoWatcher --app-version 0.2.0 --vendor AutoWatcher --description 'Local car listing monitor' --input $inputDir --main-jar autowatcher.jar --main-class org.springframework.boot.loader.launch.JarLauncher --dest $destination --win-console --java-options '-Djava.awt.headless=false' --arguments '--app.desktop=true'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
Copy-Item 'scripts/enable-autostart.ps1' "$destination/AutoWatcher/"
Copy-Item 'scripts/disable-autostart.ps1' "$destination/AutoWatcher/"
Copy-Item 'docs/WINDOWS.md' "$destination/AutoWatcher/README-Windows.md"
Copy-Item 'LICENSE' "$destination/AutoWatcher/"
Compress-Archive -Path "$destination/AutoWatcher" -DestinationPath 'target/AutoWatcher-Windows-x64.zip' -Force
