package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class MediaSourceTest {
    @Test fun mapsStashCollectionPagesThroughConfiguredServer() {
        for (collection in listOf("performers", "studios", "tags", "movies", "groups")) {
            val resolved = MediaSource.resolveUrl("https://stash.example.test/$collection/42/scenes/?sortby=date", "http://media.example.test:1338")
            assertEquals(DlnaAddress(URI("http://media.example.test:1338/ctl"), "$collection/42"), DlnaAddress.parse(resolved))
        }
    }

    @Test fun mapsSceneAndCollectionDetailPagesWithoutDependingOnStashHostname() {
        for (path in listOf("scenes/7", "performers/42", "studios/3/")) {
            val resolved = MediaSource.resolveUrl("http://another.example.test/$path", "upnp://media.example.test:1338")
            assertEquals(path.trimEnd('/'), DlnaAddress.parse(resolved).objectId)
        }
    }

    @Test fun leavesStreamsPlaylistsAndExplicitDlnaUrlsUnchanged() {
        for (url in listOf("https://server/video.mp4", "https://server/scene/7/stream", "https://server/scenes/7/stream", "http://server/list.m3u8", "upnp://server/ctl?ObjectID=root", "rtsp://server/live")) {
            assertEquals(url, MediaSource.resolveUrl(url, ""))
        }
    }

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
