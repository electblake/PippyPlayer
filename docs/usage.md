# First use

1. Install `dist/pippyplayer.apk` on an Android TV device running Android 10 or newer.
2. Open **PippyPlayer** from the TV launcher.
3. Select **Allow display over other apps**, grant access, and return.
4. Select **Start PippyPlayer**. Scan its QR code on a phone on the same LAN.
5. Enter a direct stream URL and select **Play on TV**. Press Home on the TV.
6. Use the nine-position grid, window size (15–90%), video zoom (1–3x), and independent volume (0–100%). Pause, resume, and stop video are also available.
7. Return to the TV app and select **Stop PippyPlayer** to stop the server and remove the overlay.

The controller is bundled inside the APK: no hosted website, CDN, companion app, account, or internet connection is needed for controlling LAN videos. Internet streams require internet access. The controller URL contains a random session token which changes every time the service starts. Treat the QR/link as access to this session. HTTP is intended for a trusted home LAN; do not forward port 8080 to the internet.
