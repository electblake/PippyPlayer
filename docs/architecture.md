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

Positions: `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_CENTER`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`. Settings updates preserve playback; loading a source starts a new queue. Queues and playback are in memory; restarting the service clears streams and rotates the controller URL. Player IDs and settings persist. The `playback` preferences store the `players` list, monotonic `nextPlayerId`, shared recent URLs/names, and debug flag. Each `player_<id>` preferences file stores its own last URL, volume, position, size, zoom, sorting, repeat, UPnP server, and buffer threshold. Existing single-player settings in `playback` are not migrated; shared URL history remains available.

All playback operations are scoped to `/players/{playerId}`, including `/sorting`, `/skip-backward`, `/skip-forward`, `/toggle-repeat`, and `/toggle-visibility`. The service creates and releases every player independently on the main thread. Each remote panel retains its own inputs and captures its player ID for asynchronous requests. Switching tabs only changes the controls shown, without changing playback. Debug is shared and displays labelled statistics for every player.
