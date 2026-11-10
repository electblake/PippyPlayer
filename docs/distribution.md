# Downloader by AFTVnews installation

This uses the same APK sideload flow as [StashAppAndroidTV](https://github.com/damontecres/StashAppAndroidTV#installation).

1. Host the built APK at a public HTTPS direct-download URL (for example, a GitHub Release asset).
2. Install [Downloader by AFTVnews](https://www.aftvnews.com/downloader/) on the TV and permit installation of unknown apps from Downloader.
3. Enter **PippyPlayer's APK URL** in Downloader, download, install, and open it.
4. For a short numeric code, register that URL with the [AFTVnews URL Shortener](https://go.aftvnews.com/), then enter the assigned code in Downloader.

Use [PippyPlayer's latest APK](https://github.com/electblake/PippyPlayer/releases/latest/download/pippyplayer.apk) in Downloader. The first release uses the existing development signing identity. PippyPlayer is available through Downloader code **6400452** or [aftv.news/6400452](http://aftv.news/6400452). StashAppAndroidTV's code installs their application, not this one. Android TV is the tested target; Fire TV compatibility is not established by using Downloader. See [DEPLOY.md](../DEPLOY.md) for release and update instructions.
