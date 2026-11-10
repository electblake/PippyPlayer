#requires -Version 7.4
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
Set-Location (Split-Path -Parent $PSScriptRoot)
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
New-Item -ItemType Directory -Path dist -Force | Out-Null
Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination dist/pippyplayer.apk -Force
$apkHash = (Get-FileHash dist/pippyplayer.apk -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content dist/pippyplayer.apk.sha256 "$apkHash  pippyplayer.apk" -Encoding ascii
Get-Item dist/pippyplayer.apk, dist/pippyplayer.apk.sha256
