# Architecture and API

Kotlin/Compose TV launcher, foreground media playback service, Media3 ExoPlayer, one hardware-decoded SurfaceView overlay and ExoPlayer per player, Ktor CIO HTTP server, ZXing QR generator, and a bundled vanilla HTML/CSS/JS controller. No application recovery, retry, fallback provider, or compatibility layer is implemented.

All routes are below `/<session-token>`:

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/` (without trailing slash) | Controller HTML |
| GET | `/state` | `players` array with IDs, settings and playback/debug state; shared recent URLs, names and debug flag |
| POST | `/players` | Create a player and return its state |
| DELETE | `/players/{playerId}` | Release its playback engine and window, delete its settings and remove it from the player list |
| POST | `/debug` | Set shared debug visibility (`enabled`) |
| POST | `/recent-url-name` | Name a shared recent URL (`url`, `name`) |
| POST | `/players/{playerId}/config` | Layout/audio form fields: `position`, `size`, `zoom`, `volume` |
| POST | `/players/{playerId}/source` | Load `url`; automatically detect direct video, playlist, or DLNA |
| POST | `/players/{playerId}/previous`, `/players/{playerId}/next` | Move through the queue |
| POST | `/players/{playerId}/play` | Resume |
| POST | `/players/{playerId}/pause` | Pause |
| POST | `/players/{playerId}/stop` | Clear video; keep controller running |

Positions: `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_CENTER`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`. Settings updates preserve playback; loading a source starts a new queue. Queues and playback are in memory; restarting the service clears streams and rotates the controller URL. Player IDs and settings persist. The `playback` preferences store the `players` list, monotonic `nextPlayerId`, shared UPnP server URL, shared recent URLs/names, and debug flag. Each `player_<id>` preferences file stores its own last URL, volume, position, size, zoom, sorting, repeat, and buffer threshold. A one-time migration copies the original single-player settings from `playback` into missing Player 1 settings. Existing per-player values are preserved, and a deleted Player 1 is not recreated. Shared URL history remains available.

All playback operations are scoped to `/players/{playerId}`, including `/sorting`, `/skip-backward`, `/skip-forward`, `/toggle-repeat`, and `/toggle-visibility`. The service creates and releases every player independently on the main thread. Each remote panel retains its own inputs and captures its player ID for asynchronous requests. Switching tabs only changes the controls shown, without changing playback. Debug is shared and displays labelled statistics for every player.

Each player retains its source-loading exception until its next source attempt. The original exception is rethrown to Ktor and its full text is exposed in that player's state; no retry or substitute source is used. The remote displays errors separately from polling status.

The UPnP server is read from the shared `playback.upnpServer` preference for every source request. The 0.12.2 migration promotes a configured Player 1 server from 0.12.0/0.12.1; otherwise it preserves the original shared server, or moves the first configured remaining player server when no shared address exists. Subsequent writes use only shared storage. Deleting players leaves the shared server intact.
