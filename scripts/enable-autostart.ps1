# Run explicitly to enable startup for the current Windows user.
$ErrorActionPreference = 'Stop'
$executable = Join-Path $PSScriptRoot 'AutoWatcher.exe'
if (-not (Test-Path $executable)) { throw 'Run this script from the extracted AutoWatcher folder.' }
$startup = [Environment]::GetFolderPath('Startup')
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut((Join-Path $startup 'AutoWatcher.lnk'))
$shortcut.TargetPath = $executable
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.Save()
Write-Host 'AutoWatcher will start when you sign in to Windows.'
