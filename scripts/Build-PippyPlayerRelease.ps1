#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $KeystorePath,

    [Parameter(Mandatory)]
    [string] $KeyAlias
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$releaseKeystore = (Resolve-Path -LiteralPath $KeystorePath).Path
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
Set-Location (Split-Path -Parent $PSScriptRoot)

./gradlew.bat :app:testReleaseUnitTest :app:lintRelease --console=plain
./gradlew.bat :app:assembleRelease --console=plain

# The default release variant is unsigned and includes all dependency ABIs and assets.
$staging = 'app/build/release-distribution'
New-Item -ItemType Directory -Path $staging -Force | Out-Null
$alignedApk = Join-Path $staging 'pippyplayer-aligned.apk'
$signedApk = Join-Path $staging 'pippyplayer.apk'
& "$buildTools/zipalign.exe" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk $alignedApk
& "$buildTools/apksigner.bat" sign --ks $releaseKeystore --ks-key-alias $KeyAlias `
    --ks-pass env:PIPPYPLAYER_KEYSTORE_PASSWORD --key-pass env:PIPPYPLAYER_KEY_PASSWORD `
    --v4-signing-enabled false --out $signedApk $alignedApk
& "$buildTools/apksigner.bat" verify --verbose --print-certs $signedApk
& "$buildTools/zipalign.exe" -c -P 16 4 $signedApk

# Only copy a verified APK to the distribution directory.
New-Item -ItemType Directory -Path dist/release -Force | Out-Null
Copy-Item -LiteralPath $signedApk -Destination dist/release/pippyplayer.apk -Force
$apkHash = (Get-FileHash dist/release/pippyplayer.apk -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content dist/release/pippyplayer.apk.sha256 "$apkHash  pippyplayer.apk" -Encoding ascii
Get-Item dist/release/pippyplayer.apk, dist/release/pippyplayer.apk.sha256
