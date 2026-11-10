# Release APK distribution

The first GitHub release, `0.1.0`, distributes the working development-signed APK at the owner's request. It is built with `scripts/Build-PippyPlayer.ps1` and packaged in `dist/`. Retain that existing signing identity to update these installations in place. The dedicated release-key workflow below remains available for future distribution; switching to a different key requires uninstalling the development-signed app and loses its data.

PippyPlayer is distributed as a self-signed APK through a public GitHub Release and Downloader by AFTVnews. No app store or in-app updater is involved. This follows the [StashAppAndroidTV installation method](https://github.com/damontecres/StashAppAndroidTV#installation), using PippyPlayer's own download URL and optional numeric code.

Compatibility: Android TV / Google TV running Android 10 (API 29) or newer, with permission to display overlays. The universal APK includes the bundled `assets/controller.html` web remote and LibVLC native libraries for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. Fire TV compatibility remains unverified; Downloader availability does not establish compatibility.

## Prerequisites

Use Windows with PowerShell **7.4+** (`pwsh`, not Windows PowerShell), JDK **21** (including `keytool`), Android SDK platform **37**, SDK Build Tools **36.0.0**, SDK command-line tools, and platform-tools (`adb`). Use the Gradle version supplied by the repository wrapper; initial dependency downloads require network access. Install Git and GitHub CLI (`gh`) for publishing, with write access to the public repository.

Start `pwsh -NoProfile`. Set the following paths to your actual installations; these are the paths used on the current workstation:

```powershell
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$env:JAVA_HOME = 'C:\Users\shoji\AppData\Local\mise\installs\java\21.0.2'
$env:ANDROID_HOME = 'C:\Users\shoji\AppData\Local\Android\PiP-SDK'
Set-Location 'S:\Spaces\Media-Players\PippyPlayer'
& "$env:JAVA_HOME/bin/java.exe" -version
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --licenses
```

Accept the SDK licences interactively. `ANDROID_HOME` is required by the release script; if `ANDROID_SDK_ROOT` or `local.properties` is also set, point it at the same SDK. The script invokes `zipalign.exe` and `apksigner.bat` specifically from `build-tools/36.0.0`.

## Create and retain the release key once

Choose a permanent key location **outside the repository**. The example below creates a JKS keystore under your Windows user profile, with a self-signed RSA certificate. Run it only when establishing the first release identity, never for routine updates. No certificate authority or app-store signing service is needed.

Enter passwords interactively into process environment variables. `Read-Host -MaskInput` hides typing; it returns a string because the signing tools require environment strings. Do not put passwords in command-line literals, scripts, Gradle properties, committed files, or shell history.

```powershell
$releaseKeyDirectory = Join-Path $env:USERPROFILE 'PippyPlayer-Signing'
New-Item -ItemType Directory -Path $releaseKeyDirectory -Force | Out-Null
$releaseKeystore = Join-Path $releaseKeyDirectory 'pippyplayer-release.jks'
$releaseAlias = 'pippyplayer-release'
$env:PIPPYPLAYER_KEYSTORE_PASSWORD = Read-Host 'New keystore password' -MaskInput
$env:PIPPYPLAYER_KEY_PASSWORD = Read-Host 'New private-key password' -MaskInput
& "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -storetype JKS `
    -keystore $releaseKeystore -alias $releaseAlias `
    -keyalg RSA -keysize 3072 -validity 10000 `
    -storepass:env PIPPYPLAYER_KEYSTORE_PASSWORD -keypass:env PIPPYPLAYER_KEY_PASSWORD
```

Answer keytool's certificate identity prompts. Keep secure backups of the keystore, alias, and passwords separately from source control. Retain this exact signing key for **all updates**. Losing it prevents updating existing installations with this release identity. Never supply the Android debug keystore. The release script requires an explicit keystore path and alias and never generates or substitutes a key. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Version and build each release

In `app/build.gradle.kts`, set `android.defaultConfig.versionName` to the user-facing release version and increment `versionCode` to an integer greater than every previously distributed build. The starting values are `0.1.0` and `1`. Keep `applicationId = "app.pippyplayer.tv"` unchanged.

In a new shell, repeat the prerequisite environment setup and enter the existing passwords:

```powershell
$releaseKeystore = Join-Path $env:USERPROFILE 'PippyPlayer-Signing/pippyplayer-release.jks'
$releaseAlias = 'pippyplayer-release'
$env:PIPPYPLAYER_KEYSTORE_PASSWORD = Read-Host 'Keystore password' -MaskInput
$env:PIPPYPLAYER_KEY_PASSWORD = Read-Host 'Private-key password' -MaskInput
pwsh -NoProfile -File ./scripts/Build-PippyPlayerRelease.ps1 `
    -KeystorePath $releaseKeystore -KeyAlias $releaseAlias
```

The script runs `:app:testReleaseUnitTest` and `:app:lintRelease`, then `:app:assembleRelease`. AGP 9's `android.onlyEnableUnitTestForTheTestedBuildType=false` enables release tests while retaining debug tests. No ABI splits or filters are configured, so the default release APK includes all supported native architectures and the existing web asset. Release signing stays outside Gradle.

The unsigned APK is aligned with `zipalign -P 16` **before** signing. Passwords are passed to apksigner by environment-variable name. The script verifies the signature (printing certificate fingerprints) and alignment before copying the APK to:

```text
dist/release/pippyplayer.apk
dist/release/pippyplayer.apk.sha256
```

The checksum is SHA-256 in `<hash>  pippyplayer.apk` format. Intermediate APKs remain under `app/build/release-distribution`. The existing development script and `dist/pippyplayer.apk` remain separate. Only use outputs from a successful run: a failed run stops immediately and may leave previous outputs on disk. Signature/alignment checks follow the official [apksigner](https://developer.android.com/tools/apksigner) and [zipalign](https://developer.android.com/tools/zipalign) documentation; do not modify the signed APK.

To inspect the final files independently:

```powershell
& "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify --verbose --print-certs ./dist/release/pippyplayer.apk
& "$env:ANDROID_HOME/build-tools/36.0.0/zipalign.exe" -c -P 16 4 ./dist/release/pippyplayer.apk
Get-FileHash ./dist/release/pippyplayer.apk -Algorithm SHA256
Get-Content ./dist/release/pippyplayer.apk.sha256
& "$env:ANDROID_HOME/build-tools/36.0.0/aapt.exe" dump badging ./dist/release/pippyplayer.apk
Remove-Item Env:PIPPYPLAYER_KEYSTORE_PASSWORD, Env:PIPPYPLAYER_KEY_PASSWORD
```

Compare the computed hash with the checksum file, and retain the signing certificate SHA-256 fingerprint with your release records. Environment passwords exist as plaintext in the process environment while in use; clear them after building (also after a failed run) or close the shell.

## Test installation and upgrades

Use a designated Android TV test device or a separate emulator. Enable developer options/ADB debugging and authorize the PC on the device. Select the serial reported by `adb devices -l`:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
$tvSerial = Read-Host 'Serial of the designated release test device'
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s $tvSerial install ./dist/release/pippyplayer.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s $tvSerial shell am start -n app.pippyplayer.tv/.MainActivity
```

An existing **debug-signed installation cannot be updated in place with a different release key**, even with a higher version code. Use a clean test device, or explicitly decide to uninstall the debug app first. Uninstalling removes its app data. The following is a destructive, optional command; do not run it on the running development emulator without authorization:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s $tvSerial uninstall app.pippyplayer.tv
```

After a clean install, open PippyPlayer, choose **Allow display over other apps**, grant overlay permission, return, and select **Start PippyPlayer**. Scan the QR code from a phone on the same LAN. Verify the bundled controller loads, play a video, press Home, and exercise positioning, sizing, zoom, volume, playlists, pause/resume, and stop. Test physical ARM TVs as well as the emulator; APK alignment alone does not prove native-library/device compatibility.

For an upgrade test, install a previous release signed with this key, then build the next version with the same application ID/key and a higher version code. Do not uninstall between these installs:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s $tvSerial install -r ./dist/release/pippyplayer.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s $tvSerial shell dumpsys package app.pippyplayer.tv
```

Confirm the new version is installed, overlay access remains usable, and playback/controller behavior still works. Player/session state is in memory and can reset on process restart; the controller session URL changes when the service restarts. Also test the real Downloader install/update flow below before broad distribution.

## Publish a public GitHub Release

The configured origin and GitHub repository metadata identify **[electblake/PippyPlayer](https://github.com/electblake/PippyPlayer)** as public. The commands below publish; run them only when the APK has passed release testing and publication is intended. Authenticate `gh`, commit the version/build changes, and ensure the working tree matches the tested release commit. Choose a new tag matching `versionName`:

```powershell
gh auth login
gh repo view electblake/PippyPlayer --json nameWithOwner,url,visibility
git status --short
$releaseTag = Read-Host 'Release tag matching versionName (for example v0.1.0)'
git tag -a $releaseTag -m "PippyPlayer $releaseTag"
git push origin $releaseTag
gh release create $releaseTag `
    ./dist/release/pippyplayer.apk ./dist/release/pippyplayer.apk.sha256 `
    --repo electblake/PippyPlayer --verify-tag --latest `
    --title "PippyPlayer $releaseTag" --generate-notes
```

Publish a normal release marked **Latest**, not a draft or prerelease. Keep the asset names **`pippyplayer.apk`** and **`pippyplayer.apk.sha256`** on every release. GitHub's generated source archives are not installable APKs. See [GitHub release management](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository) and [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create).

GitHub's [stable asset-link format](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases) is `https://github.com/OWNER/REPOSITORY/releases/latest/download/pippyplayer.apk`. For this verified repository, use:

- APK: <https://github.com/electblake/PippyPlayer/releases/latest/download/pippyplayer.apk>
- Checksum: <https://github.com/electblake/PippyPlayer/releases/latest/download/pippyplayer.apk.sha256>

These URLs require a published latest release containing those assets. Before sharing, test the APK URL without GitHub authentication and verify that it downloads the APK directly. The APK and checksum must come from the same successful build.

## Downloader and an optional numeric code

1. Once the public APK URL works, visit [AFTVnews URL Shortener](https://go.aftvnews.com/), enter the stable APK URL above, complete its verification, and register it. Record and share the numeric code assigned to **PippyPlayer**. No code has been created by this workflow; do not use StashAppAndroidTV's code. Submitted URLs are public.
2. Install **Downloader by AFTVnews** from the TV's app store, or use your existing installation.
3. In the TV's settings, allow **Install unknown apps** for **Downloader** (menu wording varies by TV). This sideload permission is separate from PippyPlayer's overlay permission.
4. Open Downloader, enter the full stable APK URL or PippyPlayer's assigned numeric code, download the APK, and choose **Install**.
5. Open PippyPlayer, select **Allow display over other apps**, grant PippyPlayer overlay access, return, and select **Start PippyPlayer**.

For subsequent releases, keep the same application ID and release key, increment `versionCode`, and publish the new APK/checksum under the same asset names in the new latest release. Users manually open Downloader and download/install again using the same URL or code. The existing release installation is upgraded without uninstalling. PippyPlayer does not check for, download, or install updates itself.
