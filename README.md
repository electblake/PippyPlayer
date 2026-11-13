# PippyPlayer

Watch video in a floating window on your Android TV or Google TV while using other apps. Scan the on-screen QR code to control playback from your phoneâ€”move and resize the video, adjust zoom and volume, or switch between playlist items. No companion phone app is needed.

## Supported video

- Direct video URLs, including MP4 and WebM.
- HLS, DASH, and RTSP streams.
- M3U/M3U8 playlists and DLNA/UPnP video folders.
- Android media URIs accessible to the TV.

Video requires a hardware decoder for the source codec/profile. PiP prefers existing smaller renditions and preloads the next video without transcoding on the TV.

Webpages, DRM-protected services, authenticated providers, and video-file uploads are not supported. See [playback details](docs/playback.md) and [playlist usage](docs/playlists.md).

## Install

Requires **Android 10 / API 29 or newer** on an Android TV or Google TV device that supports displaying apps over other apps. Fire TV compatibility is unverified.

1. Install **Downloader by AFTVnews** on your TV, or open it if you already have it.
2. In TV settings, allow **Install unknown apps** for Downloader.
3. Enter code **8535690** in Downloader, or open **[aftv.news/8535690](http://aftv.news/8535690)**.
4. Download the APK, choose **Install**, then open **PippyPlayer**.
5. Select **Allow display over other apps**, grant the overlay permission, and return to PippyPlayer.
6. Select **Start PippyPlayer**, then scan the QR code with a phone on the same network. Enter a video URL and select **Play on TV**. Press Home on the TV to keep watching alongside another app.

See [usage and controls](docs/usage.md) for more details.

## Updates

Open **App updates** in PippyPlayer to check the latest GitHub release. Allow installs from PippyPlayer when prompted, then choose **Download and install update** and confirm in Android’s installer. Your saved settings remain in place. Versions before 0.5.0 need one final manual APK installation to gain the updater.
