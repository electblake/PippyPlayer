# Playback scope

Media3 ExoPlayer handles progressive media (such as MP4/WebM), HLS, DASH, RTSP and accessible Android media URIs. Video uses Android hardware decoders and renders directly to a SurfaceView. The same player and surface remain attached across playlist transitions. Decoder prewarming is disabled: next-item preloading buffers compressed samples without starting a second video decoder. PiP does not request audio focus or display refresh-rate changes.

A source must use a video codec/profile supported by the device's hardware decoder. There is no software video decoder path or on-device transcoding. Audio uses Android's available decoders. A URI must be readable by the TV app; a phone's local file URI does not transfer the file. Webpages, authenticated providers, DRM services, and video-file uploads are outside this version.

Portrait, landscape, square and ultrawide videos retain their aspect ratio. Window size fits the video into the chosen percentage of the screen's width and height. Zoom enlarges/crops the video inside that window. Volume affects this player only and survives video transitions. Simultaneous playback with another app depends on the TV's decoder and memory-bandwidth capacity.

## Buffering and rendition selection

- Forward buffering targets 10–20 seconds, with a 16 MiB compressed-sample allocation target and no retained back buffer. Startup waits for 1.5 seconds; rebuffering defaults to 3 seconds. Media3 also considers byte limits and end of source when deciding readiness. These are targets, not guarantees or total process-memory limits.
- Up to 3 seconds of the next playlist item is preloaded only when current playback is not loading. Preloading additionally stops when combined sample allocations reach 20 MiB; a read already in progress can exceed that threshold. Current playback always retains loading priority.
- HLS/DASH track selection prefers video within 1280×720 and 30 fps. This selects existing tracks; it does not resize encoded video. A single higher-resolution source still requires decoding its original resolution.
- DLNA ranks advertised HTTP video resources: largest known resolution within 1280×720 (either orientation), then smallest known resolution above that budget, then resources with unknown resolution in server order. It does not request transcoding. Frame rate is constrained for adaptive tracks, not inferred from DLNA resolution metadata.
- Next/Previous seeks within the existing playlist. Sorting moves existing items while retaining the current occurrence, position and volume, including duplicate URLs. Automatic advancement is managed by Media3.

A smaller PiP window alone does not reduce decode workload. Prebuffering absorbs delivery jitter; it cannot fix sustained insufficient bandwidth, unsupported codecs, decoder contention or corrupt frames. Test simultaneous playback on the target TV.

The overlay uses permission-based `TYPE_APPLICATION_OVERLAY` to support all nine positions. Native Android PiP is system-positioned and does not expose that control. See [Android PiP documentation](https://developer.android.com/develop/ui/views/picture-in-picture).

## Android playback settings

Open **Playback settings** to set the **UPnP server URL** and **Resume buffer after a stall (milliseconds)**. Both are saved on Android. The existing saved cache value is now the rebuffer threshold. Stop and start the PippyPlayer service from the TV app to apply a changed threshold. Thresholds greater than 10/20 seconds also raise the respective forward-buffer time targets; the byte target remains unchanged.

The last loaded source URL remains saved across Stop, remote reloads and app restarts. Stop clears playback but keeps the URL. Playback does not start automatically.

## Playback diagnostics

The TV and remote show **Loading…** during startup and **Buffering…** during refills. These indicators work with diagnostics disabled and hide during pause. They use Media3's buffering state; they do not label every decoder/rendering stall as buffering or invent a percentage.

Toggle **Debug info** on the TV or remote to see one-second samples (remote refreshes every 1.5 seconds):

- Active hardware video decoder name and selected video dimensions, MIME type and source frame rate.
- Rendered FPS and frames queued to the decoder, plus cumulative dropped buffers for the active renderer. Queued frames are not proof of successful decoding.
- Network input Mbps, including next-item preloading. Bursty reads and zero-input intervals while buffered video plays are normal.
- Actual buffered media duration, playWhenReady and playback suppression reason. Duration is Media3's total buffered media time, not allocated memory or link speed.
- A snapshot immediately before the last Resume request, also written to logcat under `PippyPlayer`. Resume does not automatically reload or retry failed media.

Disabling diagnostics stops periodic sampling. Buffering indicators and lightweight network-byte/decoder-event counters continue to operate. To investigate a stall, compare buffered duration, rendered FPS, dropped buffers and the pre-Resume state while both apps are playing.
