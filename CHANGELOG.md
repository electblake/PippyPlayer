# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
