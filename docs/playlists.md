# Playlists

Paste a video, M3U/M3U8, or DLNA `upnp://` URL and press **Play on TV**. The source format is detected automatically. DLNA folders include all nested videos in server order. Videos play in order and stop after the last item. **Previous** and **Next** change items; the controller shows the current title and item number. Hosted playlists support relative URLs and optional `#EXTINF` titles. HLS manifests remain a single stream. Playlist text uses UTF-8.

## Stash page URLs

Set the **UPnP server URL** once under **Playback settings** in the Android app. In the phone remote, paste a Stash page URL into **Video URL** and press **Play**. Android translates the URL, browses the configured server, loads the returned playlist, and starts playback. There is no separate parsing step or UPnP form on the remote.

Supported page paths are `/scenes/{id}` and `/{collection}/{id}` with an optional `/scenes` suffix, where collection is `performers`, `studios`, `tags`, `movies`, or `groups`, and IDs are numeric. Trailing slashes are accepted. The hostname is not hardcoded. Page query parameters and fragments are not forwarded: the mapped object ID is the path without its trailing `/scenes`, and the configured server must support that object ID under `/ctl`. Direct stream, playlist, and explicit `upnp://` URLs continue to work unchanged.

The last loaded source is retained on Android and restored in the remote after reloads, Stop, and app restarts. Press Play to load it again.

Bare paths such as `tags/17279/scenes` and `/performers/5575/scenes` can also be entered directly in Video URL. Only the path identifies the DLNA collection.

## Sorting

Sorting applies immediately and is remembered. Choose Default, Length (Shortest), Length (Longest), Name, or Random. Random shuffles the playlist each time it is applied or a new playlist is loaded. Changing sorting keeps the current video playing; Default restores the original server order.
