# Playback comparison

Compare the last known-good APK and the exact signed candidate on the same designated Android TV. Use the same local media, window sizes, and settings. An emulator run also provides evidence for API behavior, saved settings, and frame output. For a change to video composition, use a physical Android TV before publication when a designated TV is available. If a fix depends on reproducing physical-TV behavior, physical verification is needed to establish that fix.

## Prepare the comparison

- Obtain published APKs with `gh release download` and check the published SHA-256. Compare the signing certificate digests with `apksigner` before installing over an existing build. Use supported in-place upgrades on the designated device to test settings retention.
- Use a moving 720p H.264 clip with audio and a known visible pattern. Serve the same file locally for all runs. Follow [the emulator setup](../../../../docs/emulator.md) for controller access when applicable.
- Seed players with the settings supported by both builds, including URL, volume, sort and repeat settings, and layout. Use saved custom names, nonzero offsets, and an asymmetric crop when the baseline supports them. Capture the saved values before upgrading and check the same player IDs and values after restarting the app. Exercise newly introduced controls separately on the candidate.

## Check actual playback

1. Start one player with zero offsets and crop. Verify a visible first video frame. Add two players and sustain playback at the same settings for a fixed sample interval on each build. Exercise playback over the TV home screen as well as the app.
2. Capture the actual display with `adb exec-out screencap -p` or observe the TV. With debug info enabled, compare rendered and dropped frame counters over the sample interval. Investigate audio-only playback, black or frozen video, frame drops, stalls, and decoder errors. A controller status of **Playing** alone does not establish visible video.
3. While all three players are running, change volume, move and resize one player, change zoom, apply an asymmetric crop, then clear it. Verify the expected picture and that other players keep rendering. Check that the regular uncropped surface fills its overlay. Compare overlay flags in `adb shell dumpsys window windows` before and after layout changes. Android's existing `HARDWARE_ACCELERATED` flag must survive while `LAYOUT_NO_LIMITS` follows offsets.
4. Test pause and resume, switching sources, and restoring the saved layout after restarting. If a failure appears only with a particular saved setting, repeat the same setting on the baseline and record the difference.

Keep the APK versions, device/API, sample interval, frame counts, and screenshots in ignored `artifacts/` when they are needed as evidence. Use dedicated test players and keep controller session URLs local.
