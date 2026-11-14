# First use

The remote's **Recent URLs** picker remembers sources played on this TV. Open it, search or select a previous URL, then select **Play**. Each source you play moves to the top without duplicates. History is saved with the Android app's settings, shared across remote browsers, and retained after restarting the app. You can still type or paste a new URL at any time.

1. Install `dist/pippyplayer.apk` on an Android TV device running Android 10 or newer.
2. Open **PippyPlayer** from the TV launcher.
3. Select **Allow display over other apps**, grant access, and return.
4. Select **Start PippyPlayer**. Scan its QR code on a phone on the same LAN.
5. Enter a direct stream URL and select **Play on TV**. Press Home on the TV.
6. Use the nine-position grid, window size (15–90%), video zoom (1–3x), and independent volume (0–100%). Pause, resume, and stop video are also available. The buttons on either side skip backward or forward 10 seconds within the current video, stopping at its start or end. Skip controls are enabled when the video supports seeking.
7. Return to the TV app and select **Stop PippyPlayer** to stop the server and remove the overlay.

The controller is bundled inside the APK: no hosted website, CDN, companion app, account, or internet connection is needed for controlling LAN videos. Internet streams require internet access. The controller URL contains a random session token which changes every time the service starts. Treat the QR/link as access to this session. HTTP is intended for a trusted home LAN; do not forward the controller port to the internet. Android assigns a free port on each server start; the displayed URL and QR code include that port.

If the crash report screen appears, select **App updates** to open the updater directly without starting playback. The crash report remains available until you select **Dismiss and reopen**. For installations stuck on 0.7.0, install a newer APK through Downloader over the existing app; the crash-screen update button is available from 0.7.1.

Use the **Repeat current video** button (repeat arrows with a **1**) to toggle looping the current video. Mint highlighting means repeat is on. Turn it off to resume normal playlist progression. Previous and Next still let you choose another video. Repeat starts off when the playback service starts.
