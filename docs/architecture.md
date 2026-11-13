# Architecture and API

Kotlin/Compose TV launcher, foreground media playback service, Media3 ExoPlayer, a hardware-decoded SurfaceView overlay, Ktor CIO HTTP server, ZXing QR generator, and a bundled vanilla HTML/CSS/JS controller. No application recovery, retry, fallback provider, or compatibility layer is implemented.

All routes are below `/<session-token>`:

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/` (without trailing slash) | Controller HTML |
| GET | `/state` | Settings, named playback state, loading state, buffered milliseconds, decoder, dimensions, elapsed time, native error |
| POST | `/config` | Layout/audio form fields: `position`, `size`, `zoom`, `volume` |
| POST | `/source` | Load `url`; automatically detect direct video, playlist, or DLNA |
| POST | `/previous`, `/next` | Move through the queue |
| POST | `/play` | Resume |
| POST | `/pause` | Pause |
| POST | `/stop` | Clear video; keep controller running |

Positions: `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_CENTER`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`. Settings updates preserve playback; loading a source starts a new queue. State is in memory; restarting the service clears the stream and rotates the controller URL.
