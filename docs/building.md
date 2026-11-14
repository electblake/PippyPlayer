# Build

Requirements: JDK 21, Android SDK platform 37, build tools 36.0.0, platform tools, accepted SDK licences, and PowerShell 7.4+. Set `JAVA_HOME` and `ANDROID_HOME` to those installations.

```powershell
pwsh -NoProfile -File ./scripts/Build-PippyPlayer.ps1
pwsh -NoProfile -File ./scripts/Install-PippyPlayer.ps1 -Serial emulator-5554
```

The build runs unit tests and Android lint, then writes `dist/pippyplayer.apk` and its SHA-256 checksum. This is a development-signed build. Retain the same signing key for updates. For public distribution, use a dedicated release signing key kept outside source control.

## Controller regression tests

With Node.js and Microsoft Edge installed, install the browser test dependency and run the error-persistence checks:

```powershell
npm install --no-save --package-lock=false playwright@1.62.1
npx --no-install playwright test tests/controller-errors.spec.cjs --workers=1 --reporter=line --output=artifacts/controller-error-tests
```

These tests exercise the bundled controller against controlled HTTP/state responses. The Android unit suite separately covers persisted-setting upgrades and source loading against a real local HTTP server returning an error. Neither replaces testing playback on an Android TV.
