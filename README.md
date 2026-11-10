# PippyPlayer

Self-contained Android TV / Google TV floating video player. The TV hosts a phone-friendly web controller; scan the QR code, enter a direct video URL, and press Home on the TV to watch alongside another app.

## First use

1. Install `dist/pippyplayer.apk` on an Android TV device running Android 10 or newer.
2. Open **PippyPlayer** from the TV launcher.
3. Select **Allow display over other apps**, grant access, and return.
4. Select **Start PippyPlayer**. Scan its QR code on a phone on the same LAN.
5. Enter a direct stream URL and select **Play on TV**. Press Home on the TV.
6. Use the nine-position grid, window size (15â€“90%), video zoom (1â€“3x), and independent volume (0â€“100%). Pause, resume, and stop video are also available.
7. Return to the TV app and select **Stop PippyPlayer** to stop the server and remove the overlay.

The controller is bundled inside the APK: no hosted website, CDN, companion app, account, or internet connection is needed for controlling LAN videos. Internet streams require internet access. The controller URL contains a random session token which changes every time the service starts. Treat the QR/link as access to this session. HTTP is intended for a trusted home LAN; do not forward port 8080 to the internet.

## Playback scope

LibVLC handles direct progressive media (such as MP4/WebM), HLS, DASH, RTSP and accessible Android media URIs. Video decoding uses the bundled software decoder. A URI must be readable by the TV app; a phone's local file URI does not transfer the file. Webpages, authenticated providers, DRM services, and video-file uploads are outside this version.

Portrait, landscape, square and ultrawide videos retain their aspect ratio. Window size fits the video into the chosen percentage of the screen's width and height. Zoom enlarges/crops the video inside that window. Volume affects this player only. The player does not request audio focus, so it does not deliberately pause or duck other apps. Simultaneous video/audio depends on the device and the other app; apps can hide overlays and hardware decoder limits still apply.

This implementation uses a permission-based `TYPE_APPLICATION_OVERLAY`, following the supplied local Picture-in-picture reference, to support all nine positions. Native Android PiP is system-positioned and does not expose that control. Google recommends native PiP for standard PiP experiences: [Android PiP documentation](https://developer.android.com/develop/ui/views/picture-in-picture). This project targets sideloading on devices that allow overlays; no native-PiP fallback is included.

## Playlists

Paste a video, M3U/M3U8, or DLNA `upnp://` URL and press **Play on TV**. The source format is detected automatically. DLNA folders include all nested videos in server order. Videos play in order and stop after the last item. **Previous** and **Next** change items; the controller shows the current title and item number. Hosted playlists support relative URLs and optional `#EXTINF` titles. HLS manifests remain a single stream. Playlist text uses UTF-8.

## Build

Requirements: JDK 21, Android SDK platform 36, build tools 36.0.0, platform tools, accepted SDK licences, and PowerShell 7.4+. Set `JAVA_HOME` and `ANDROID_HOME` to those installations.

```powershell
pwsh -NoProfile -File ./scripts/Build-PippyPlayer.ps1
pwsh -NoProfile -File ./scripts/Install-PippyPlayer.ps1 -Serial emulator-5554
```

The build runs unit tests and Android lint, then writes `dist/pippyplayer.apk` and its SHA-256 checksum. This is a development-signed build. Retain the same signing key for updates. For public distribution, use a dedicated release signing key kept outside source control.

## Downloader by AFTVnews installation

This uses the same APK sideload flow as [StashAppAndroidTV](https://github.com/damontecres/StashAppAndroidTV#installation).

1. Host the built APK at a public HTTPS direct-download URL (for example, a GitHub Release asset).
2. Install [Downloader by AFTVnews](https://www.aftvnews.com/downloader/) on the TV and permit installation of unknown apps from Downloader.
3. Enter **PippyPlayer's APK URL** in Downloader, download, install, and open it.
4. For a short numeric code, register that URL with the [AFTVnews URL Shortener](https://go.aftvnews.com/), then enter the assigned code in Downloader.

Use [PippyPlayer's latest APK](https://github.com/electblake/PippyPlayer/releases/latest/download/pippyplayer.apk) in Downloader. The first release uses the existing development signing identity. No numeric short code has been registered. StashAppAndroidTV's code installs their application, not this one. Android TV is the tested target; Fire TV compatibility is not established by using Downloader. See [DEPLOY.md](DEPLOY.md) for release and update instructions.

## Local Android TV emulator

In Android Studio Device Manager, create an Android TV 1080p AVD with Google's Android TV API 36 x86_64 system image. This workstation already has `StashTV` configured with that image.

```powershell
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd StashTV -port 5554 -gpu host -no-snapshot
```

After boot, install the APK using the script above. For automated development only, grant the overlay permission with:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell appops set app.pippyplayer.tv SYSTEM_ALERT_WINDOW allow
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 forward tcp:18080 tcp:8080
```

Start PippyPlayer using the TV remote. Open the displayed URL on this PC, replacing `10.0.2.15:8080` with `127.0.0.1:18080` and preserving the token path. The emulator's QR address is behind emulator NAT and is not reachable directly from a physical phone. Physical TVs advertise their LAN address for scanning. A media server on this PC is reachable from the emulator as `10.0.2.2`.

For phone testing against the emulator, run `python scripts/emulator-controller-proxy.py 192.168.1.66 18082` on the PC with the ADB forward above active. Replace the IP with the PC's current LAN address. Launch the app with `adb -s emulator-5554 shell am start -n app.pippyplayer.tv/.MainActivity --es controller_origin http://192.168.1.66:18082`, then select **Start PippyPlayer**. The QR will point through the PC proxy to the TV-hosted controller. Keep the proxy running while testing. A normal launch without the extra advertises the TV's own address.

## Architecture and API

Kotlin/Compose TV launcher, foreground media playback service, LibVLC, TextureView overlay, Ktor CIO HTTP server, ZXing QR generator, and a bundled vanilla HTML/CSS/JS controller. No application recovery, retry, fallback provider, or compatibility layer is implemented.

All routes are below `/<session-token>`:

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/` (without trailing slash) | Controller HTML |
| GET | `/state` | Settings, playback state, dimensions, elapsed time, native error |
| POST | `/config` | Layout/audio form fields: `position`, `size`, `zoom`, `volume` |
| POST | `/source` | Load `url`; automatically detect direct video, playlist, or DLNA |
| POST | `/previous`, `/next` | Move through the queue |
| POST | `/play` | Resume |
| POST | `/pause` | Pause |
| POST | `/stop` | Clear video; keep controller running |

Positions: `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_CENTER`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`. Settings updates preserve playback; loading a source starts a new queue. State is in memory; restarting the service clears the stream and rotates the controller URL.
