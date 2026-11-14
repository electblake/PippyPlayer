# PippyPlayer

Watch video in a floating window on your Android TV or Google TV while using other apps. Scan the on-screen QR code to control playback from your phoneâ€”move and resize the video, adjust zoom and volume, or switch between playlist items. No companion phone app is needed.

## Supported video

- Direct video URLs, including MP4 and WebM.
- HLS, DASH, and RTSP streams.
- M3U/M3U8 playlists and DLNA/UPnP video folders.
- Android media URIs accessible to the TV.

Video requires a hardware decoder for the source codec/profile. PiP prefers existing smaller renditions and preloads the next video without transcoding on the TV.

Webpages, DRM-protected services, authenticated providers, and video-file uploads are not supported. See [playback details](docs/playback.md) and [playlist usage](docs/playlists.md).

## Device performance and compatibility

PippyPlayer uses [Android Media3 / ExoPlayer](https://developer.android.com/media/media3/exoplayer/supported-devices) and the TV's hardware video decoders. Compatibility depends on the operating system and hardware, not just the manufacturer. Android TV and Google TV models need **Android 10 / API 29 or newer** and permission to display over other apps.

Playback performance varies by codec, resolution, and available decoder resources. Playing another video app alongside PippyPlayer can cause contention even when either video plays smoothly on its own. PippyPlayer prefers existing smaller renditions and preloads the next video without transcoding on the TV.

Manufacturers with compatible Android TV / Google TV product lines:

- [Sony BRAVIA](https://www.sony.com/electronics/support/audio-video/articles/00200248): Android TV and Google TV models meeting the requirements above; older non-Android Sony TVs are not supported.
- [TCL](https://support.tcl.com/en_CA/62992-televisions): Android TV and Google TV models meeting the requirements above; Roku models are not supported.
- [Hisense](https://www.hisense-usa.com/televisions/smart-tv-platforms): Android TV and Google TV models meeting the requirements above; VIDAA and Roku models are not supported.

These are eligible product lines, not a guarantee that every model has been tested. Playback is confirmed by a user on a **Sony BRAVIA 4K AE1 running Android TV 14**. Other manufacturers' Android TV and Google TV models may also meet the requirements. Samsung Tizen and LG webOS TVs cannot run this Android APK natively; Fire TV compatibility remains unverified.

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
