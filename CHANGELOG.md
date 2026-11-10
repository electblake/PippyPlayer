# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Added a Sorting dropdown below Previous/Next with Default, Length (Shortest), Length (Longest), and Name options, applied immediately and remembered across restarts.
- Added DLNA duration metadata for length sorting, with original server order restored by Default and the current video preserved when sorting.

## [0.1.0] - 2026-11-10

### Added

- Added an Android TV floating video player with LibVLC playback and a QR-accessible, TV-hosted web controller.
- Added nine-position placement, window sizing, video zoom, independent volume, and playback controls.
- Added automatic video, M3U/M3U8, and recursive DLNA playlist loading with previous and next controls.
- Added Advanced Mode with a saved UPnP server address, source URL conversion, and Send to Player controls.
- Added Gradle build configuration, unit tests, Android lint, APK packaging and installation scripts, and emulator setup documentation.
- Added Git ignore rules for build outputs, caches, local testing artifacts, and signing keys.
- Added a release signing script and a GitHub/Downloader distribution guide.
