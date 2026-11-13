# Playback scope

LibVLC handles direct progressive media (such as MP4/WebM), HLS, DASH, RTSP and accessible Android media URIs. Playback enables Android hardware decoding and MediaCodec direct rendering instead of forcing the bundled software video decoder. Video renders through a SurfaceView so Android can compose its surface separately from the app UI, avoiding TextureView composition overhead. Network media uses the cache target saved in the Android app (initially 3,000 ms) to absorb delivery jitter; this can increase startup latency and is not a guaranteed amount of buffered content. A URI must be readable by the TV app; a phone's local file URI does not transfer the file. Webpages, authenticated providers, DRM services, and video-file uploads are outside this version.

Portrait, landscape, square and ultrawide videos retain their aspect ratio. Window size fits the video into the chosen percentage of the screen's width and height. Zoom enlarges/crops the video inside that window. Volume affects this player only. The player does not request audio focus, so it does not deliberately pause or duck other apps. Simultaneous video/audio depends on the device and the other app; apps can hide overlays and hardware decoder limits still apply.

This implementation uses a permission-based `TYPE_APPLICATION_OVERLAY`, following the supplied local Picture-in-picture reference, to support all nine positions. Native Android PiP is system-positioned and does not expose that control. Google recommends native PiP for standard PiP experiences: [Android PiP documentation](https://developer.android.com/develop/ui/views/picture-in-picture). This project targets sideloading on devices that allow overlays; no native-PiP fallback is included.

## Android playback settings

Open **Playback settings** in the Android app to set the **UPnP server URL** and **Network buffer cache (milliseconds)**. Both are saved on the Android device; neither is configured through the phone remote. The server URL starts empty. Cache changes apply to the next loaded video or playlist item, without restarting current playback.

The last successfully loaded source URL is also saved on Android. Opening or reloading the remote restores it, including after an app restart. Stop clears playback but keeps the URL. Playback does not start automatically.

## Playback diagnostics

The TV overlay and remote show a spinner while opening media and a buffering percentage while VLC refills its buffer. These indicators work with diagnostics disabled. Buffering takes precedence over VLC's playing flag and clears when the refill reaches 100%; paused playback hides the indicator. The percentage is progress through the current refill, not measured buffer occupancy. Decoder or rendering stalls that do not emit buffering events are not labelled as network buffering.

Toggle **Debug info** in the TV app or **Debug info on TV and remote** under the remote's Settings. The setting persists and controls both the overlay and the remote panel. Statistics are sampled once per second only while enabled; the remote refreshes every 1.5 seconds. Disabling diagnostics removes the sampling callback and overlay.

- Displayed and decoded FPS are counter deltas over monotonic elapsed time, not the video's advertised frame rate. Source FPS is shown separately. A dash means a measurement is not available yet.
- Media input Mbps measures bytes read by VLC, not Wi-Fi link speed or a speed test. Bursty input and zero-throughput intervals can be normal while buffered data is playing.
- Dropped frames are VLC's cumulative lost-picture count for the current media. The resolution and codec describe the selected video track.
- Last buffering progress comes from VLC's buffering events. It does not report free bytes, buffer capacity, or seconds of playable content. The cache target is a configuration value, not measured occupancy.
- “Hardware decoding requested” describes configuration, not proof that the device selected a hardware codec. Verify the selected MediaCodec implementation in device logs when profiling.

DLNA browsing currently selects the first HTTP media resource advertised by the server. It does not negotiate adaptive quality or request transcoding. A smaller overlay still decodes the original video resolution. To reduce that workload and network demand, the server must supply a lower-resolution/lower-bitrate resource. Additional caching can absorb short network stalls but cannot fix sustained insufficient bandwidth or decoder overload. Hardware decoder capacity and performance alongside another video app need verification on the target TV.
