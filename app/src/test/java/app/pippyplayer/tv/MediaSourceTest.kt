package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class MediaSourceTest {
    @Test fun detectsPlaylistExtensionDespiteQuery() {
        assertTrue(MediaSource.isPlaylist(URI("http://server/LIST.M3U8?key=1"), "application/octet-stream", ""))
    }
    @Test fun detectsPlaylistMimeWithoutExtension() {
        assertTrue(MediaSource.isPlaylist(URI("http://server/export"), "Audio/X-MPEGURL; charset=utf-8", ""))
    }
    @Test fun detectsPlaylistContentWithoutMimeOrExtension() {
        assertTrue(MediaSource.isPlaylist(URI("http://server/export"), null, "\uFEFF#EXTM3U\n"))
        assertTrue(MediaSource.isPlaylist(URI("http://server/export"), "text/plain", "https://server/video.mp4\n"))
    }
    @Test fun leavesVideoAndDashToPlayer() {
        assertFalse(MediaSource.isPlaylist(URI("http://server/scene/42/stream"), "video/mp4", "\u0000\u0000ftyp"))
        assertFalse(MediaSource.isPlaylist(URI("http://server/video.mpd"), "application/dash+xml", "<?xml version=\"1.0\"?><MPD>"))
    }
}
