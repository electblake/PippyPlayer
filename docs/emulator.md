# Local Android TV emulator

In Android Studio Device Manager, create an Android TV 1080p AVD with Google's Android TV API 36 x86_64 system image. This workstation already has `StashTV` configured with that image.

```powershell
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd StashTV -port 5554 -gpu host -no-snapshot
```

After boot, install the APK using the script in [Building](building.md). For automated development only, grant the overlay permission with:

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 shell appops set app.pippyplayer.tv SYSTEM_ALERT_WINDOW allow
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s emulator-5554 forward tcp:18080 tcp:8080
```

Start PippyPlayer using the TV remote. Open the displayed URL on this PC, replacing `10.0.2.15:8080` with `127.0.0.1:18080` and preserving the token path. The emulator's QR address is behind emulator NAT and is not reachable directly from a physical phone. Physical TVs advertise their LAN address for scanning. A media server on this PC is reachable from the emulator as `10.0.2.2`.

For phone testing against the emulator, run `python scripts/emulator-controller-proxy.py 192.168.1.66 18082` on the PC with the ADB forward above active. Replace the IP with the PC's current LAN address. Launch the app with `adb -s emulator-5554 shell am start -n app.pippyplayer.tv/.MainActivity --es controller_origin http://192.168.1.66:18082`, then select **Start PippyPlayer**. The QR will point through the PC proxy to the TV-hosted controller. Keep the proxy running while testing. A normal launch without the extra advertises the TV's own address.
