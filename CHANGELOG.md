# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.10.0] - 2026-11-13

### Added

- Added a repeat-one icon button to loop the current video using Media3 repeat mode, with a distinct active highlight and accessible toggle state. Turning it off restored normal playlist progression without changing pause state, position or volume.

## [0.9.0] - 2026-11-13

### Added

- Added a searchable Recent URLs picker with persistent, deduplicated playback history shared across remote browsers.

### Changed

- Restored Previous and Next as full-width text buttons on their own row in the web remote.
- Made Android button focus distinct with a bright mint fill, dark text and white outline, and highlighted focused settings fields with a mint outline.

### Fixed

- Saved volume, including mute, in app preferences and restored it before playback so restarts and in-place updates retain the setting.

## [0.8.0] - 2026-11-13

### Added

- Added an eye toggle to the web remote that hides and pauses the player, then shows and resumes it without losing the current position or volume.
- Documented Media3 device performance, Android requirements, and Sony, TCL and Hisense compatibility links.

### Changed

- Replaced remote playback text buttons with compact icons, accessible labels and tooltips, retaining touch targets and wrapping on narrow screens.
- Made Play and playlist navigation show the player before resuming so playback does not continue hidden.

## [0.7.1] - 2026-11-13

### Added

- Added an App updates button to the crash screen that opens the updater directly without starting playback or deleting the crash report.

### Fixed

- Fixed the 0.7.0 startup crash caused by unimplemented Media3 load-control methods, explicitly forwarding lifecycle and buffering decisions to DefaultLoadControl.
- Exercised the actual custom load controller in player-construction tests, including startup, buffering thresholds and current-playback priority.

## [0.7.0] - 2026-11-13

### Added

- Added loading and buffering indicators on the TV overlay and remote, independent of debug mode.
- Added forward buffering and three-second next-video preloading with current-playback priority and sample-allocation limits.
- Added active hardware decoder, buffered-duration, dropped-frame and pre-Resume diagnostics.

### Changed

- Replaced LibVLC with Media3 hardware video decoding and a persistent SurfaceView across playlist transitions.
- Preferred smaller existing adaptive and DLNA video renditions for PiP without on-device transcoding.
- Reduced remote polling to playlist counts and position instead of serializing every video URL on the main thread.
- Used the saved cache setting as the rebuffer threshold, applied when the service starts.
- Preserved the current video and position when sorting the native player playlist, including duplicate URLs.

### Fixed

- Preserved the selected volume across video changes and loading a new source.

## [0.6.0] - 2026-11-13

### Added

- Added a remembered Random sorting option for playlist playback.
- Added direct performer and tag URI input on Play, including bare paths, using the configured DLNA server without matching the source hostname or query parameters.

## [0.5.0] - 2026-11-10

### Added

- Added a native App updates screen that checked the latest GitHub release and displayed the installed version and release notes.
- Added APK downloads with progress and an Android installer handoff, including the install-source permission flow.
- Added numeric release-version comparison to offer only newer releases.

### Changed

- Documented in-app updates and the one-time manual upgrade needed for versions before 0.5.0.
- Updated the distribution guide’s Downloader link to match the current README.

## [0.4.0] - 2026-11-10

### Added

- Added saved Android playback settings for the UPnP server and network buffer cache.
- Added automatic Stash page URL translation and playback through the configured UPnP server.
- Saved the last loaded source URL on Android across Stop, controller reloads, and app restarts.
- Added a persistent debug toggle showing displayed and decoded FPS, media throughput, dropped frames, video format, and buffering progress.
- Added 10-second backward and forward skip controls for seekable videos.

### Changed

- Enabled Android hardware decoding and direct rendering, switched video output to SurfaceView, and configured a network cache target.
- Stopped diagnostic sampling when debug information was disabled.

### Removed

- Removed Advanced Mode and the UPnP conversion form from the web remote.
- Removed personal server addresses and examples from the current source, documentation, and packaged remote.

### Fixed

- Fixed video surface attachment across Stop and subsequent playback.

## [0.3.1] - 2026-11-10

### Fixed

- Fixed server startup crashing when port 8080 was already in use by letting Android allocate the controller port and including that port in the URL and QR code.
- Updated the README Downloader link and code to `5239707`.

## [0.3.0] - 2026-11-10

### Added

- Added a TV crash screen showing the full uncaught exception stack trace, app version, device, Android version, and crashing thread, with remote scrolling and a Copy crash log button.
- Saved crash reports across app restarts until dismissed, and ran the report viewer in a separate process so it survives the app crash.

## [0.2.0] - 2026-11-10

### Added

- Added a Sorting dropdown below Previous/Next with Default, Length (Shortest), Length (Longest), and Name options, applied immediately and remembered across restarts.
- Added DLNA duration metadata for length sorting, with original server order restored by Default and the current video preserved when sorting.

### Changed

- Updated Android build dependencies and resolved Android lint warnings.
- Rewrote the README with an app overview, supported video sources, and installation instructions using Downloader code `6400452`.
- Moved detailed usage, playback, playlist, build, distribution, emulator, and API documentation into separate files under `docs/`.

## [0.1.0] - 2026-11-10

### Added

- Added an Android TV floating video player with LibVLC playback and a QR-accessible, TV-hosted web controller.
- Added nine-position placement, window sizing, video zoom, independent volume, and playback controls.
- Added automatic video, M3U/M3U8, and recursive DLNA playlist loading with previous and next controls.
- Added Advanced Mode with a saved UPnP server address, source URL conversion, and Send to Player controls.
- Added Gradle build configuration, unit tests, Android lint, APK packaging and installation scripts, and emulator setup documentation.
- Added Git ignore rules for build outputs, caches, local testing artifacts, and signing keys.
- Added a release signing script and a GitHub/Downloader distribution guide.
