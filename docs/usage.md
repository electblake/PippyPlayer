# First use

The remote's **Recent URLs** picker remembers sources played on this TV. Open it and select a previous URL to start playback immediately; use search to find a saved source. Each source you play moves to the top without duplicates. History is saved with the Android app's settings, shared across remote browsers, and retained after restarting the app. You can still type or paste a new URL at any time.

1. Install `dist/pippyplayer.apk` on an Android TV device running Android 10 or newer.
2. Open **PippyPlayer** from the TV launcher.
3. Select **Allow display over other apps**, grant access, and return.
4. Select **Start PippyPlayer**. Scan its QR code on a phone on the same LAN.
5. Enter a direct stream URL and select **Play**. Press Home on the TV.
6. Use the nine-position grid, window size (15–90%), video zoom (1–3x), and independent volume (0–100%). Pause, resume, and stop video are also available. The buttons on either side skip backward or forward 10 seconds within the current video, stopping at its start or end. Skip controls are enabled when the video supports seeking.
7. Return to the TV app and select **Stop PippyPlayer** to stop the server and remove all player overlays.

The controller is bundled inside the APK: no hosted website, CDN, companion app, account, or internet connection is needed for controlling LAN videos. Internet streams require internet access. The controller URL contains a random session token which changes every time the service starts. Treat the QR/link as access to this session. HTTP is intended for a trusted home LAN; do not forward the controller port to the internet. Android assigns a free port on each server start; the displayed URL and QR code include that port.

If the crash report screen appears, select **App updates** to open the updater directly without starting playback. The crash report remains available until you select **Dismiss and reopen**. For installations stuck on 0.7.0, install a newer APK through Downloader over the existing app; the crash-screen update button is available from 0.7.1.

Use the **Repeat current video** button (repeat arrows with a **1**) to toggle looping the current video. Mint highlighting means repeat is on. Turn it off to resume normal playlist progression. Previous and Next still let you choose another video. Repeat is saved independently for each player.

## Multiple players

The remote opens on **Player 1**. Select **+** beside the player tabs to add another player, then choose its URL and screen position. Each player has its own playback, playlist sorting, repeat, visibility, volume, position, size, and zoom controls. Switching tabs keeps every other player running and preserves unfinished URL input. New players start with default settings; position their windows separately to see them together.

The UPnP server URL, Recent URLs, and their display names are shared. The debug checkbox below the player controls applies to all players. Player settings and the player list survive a service restart, but videos must be started again. On the TV’s **Playback settings** screen, use **Save shared server** to configure the UPnP server for every player. Choose a player only for its buffer threshold and use **Save player buffer**. Restart the service to apply buffer changes.

Version 0.12.2 restores one shared UPnP server, preserving the address entered in Player 1 during 0.12.0/0.12.1 or the original saved shared address. Other saved single-player settings are restored into Player 1 automatically. Settings already changed in Player 1 are preserved. Install the update over the existing app without uninstalling or clearing app data; recent URL history is retained. The number of simultaneous videos supported depends on the TV’s hardware decoders.

Select **Delete player** inside a player’s controls to stop and remove that player and its saved settings. Shared recent URLs are retained. You can delete every player and use **+** to start again.

Source-loading errors remain visible in the player controls until the next source attempt, including through remote reloads. The error text is selectable so you can copy the original failure details.
