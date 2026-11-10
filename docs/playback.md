# Playback scope

LibVLC handles direct progressive media (such as MP4/WebM), HLS, DASH, RTSP and accessible Android media URIs. Video decoding uses the bundled software decoder. A URI must be readable by the TV app; a phone's local file URI does not transfer the file. Webpages, authenticated providers, DRM services, and video-file uploads are outside this version.

Portrait, landscape, square and ultrawide videos retain their aspect ratio. Window size fits the video into the chosen percentage of the screen's width and height. Zoom enlarges/crops the video inside that window. Volume affects this player only. The player does not request audio focus, so it does not deliberately pause or duck other apps. Simultaneous video/audio depends on the device and the other app; apps can hide overlays and hardware decoder limits still apply.

This implementation uses a permission-based `TYPE_APPLICATION_OVERLAY`, following the supplied local Picture-in-picture reference, to support all nine positions. Native Android PiP is system-positioned and does not expose that control. Google recommends native PiP for standard PiP experiences: [Android PiP documentation](https://developer.android.com/develop/ui/views/picture-in-picture). This project targets sideloading on devices that allow overlays; no native-PiP fallback is included.
