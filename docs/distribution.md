# Downloader by AFTVnews installation

This uses the same APK sideload flow as [StashAppAndroidTV](https://github.com/damontecres/StashAppAndroidTV#installation).

1. Host the built APK at a public HTTPS direct-download URL (for example, a GitHub Release asset).
2. Install [Downloader by AFTVnews](https://www.aftvnews.com/downloader/) on the TV and permit installation of unknown apps from Downloader.
3. Enter **PippyPlayer's APK URL** in Downloader, download, install, and open it.
4. For a short numeric code, register that URL with the [AFTVnews URL Shortener](https://go.aftvnews.com/), then enter the assigned code in Downloader.

Use [PippyPlayer's latest APK](https://github.com/electblake/PippyPlayer/releases/latest/download/pippyplayer.apk) in Downloader. The first release uses the existing development signing identity. PippyPlayer is available through Downloader code **8604117** or [aftv.news/8604117](http://aftv.news/8604117). StashAppAndroidTV's code installs their application, not this one. Android TV is the tested target; Fire TV compatibility is not established by using Downloader. See [DEPLOY.md](../DEPLOY.md) for release and update instructions.

## In-app updates (0.5.0 and later)

Choose **App updates** on the Android app’s home screen. The app checks the latest public GitHub release and compares its numeric version with the installed version. If newer, allow installation from PippyPlayer in Android settings, return to the app, and select **Download and install update**. Keep the update screen open while downloading. Playback stops before the installer opens; confirm installation there. Android preserves app data and checks the APK signing identity. Update checks run only when you open this screen or select Check for updates.

The updater downloads the release asset named `pippyplayer.apk`. No GitHub login or web-remote configuration is needed. The download is stored in private app files and shared only with the installer using a temporary content-URI read grant. The next download replaces the previous APK.
