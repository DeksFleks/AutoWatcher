$ErrorActionPreference = 'Stop'
$startup = [Environment]::GetFolderPath('Startup')
$shortcut = Join-Path $startup 'AutoWatcher.lnk'
if (Test-Path $shortcut) { Remove-Item $shortcut }
Write-Host 'AutoWatcher startup disabled.'
