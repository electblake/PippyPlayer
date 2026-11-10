package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class VideoPlaylistTest {
    private val source = URI("http://tv.test/lists/watch.m3u8?key=123")

    @Test fun extendedPlaylistResolvesRelativeUrlsAndTitles() {
        val entries = VideoPlaylist.parse("\uFEFF#EXTM3U\r\n#EXTINF:-1,First, with comma\r\n../one.mp4\r\n\r\n# comment\r\nhttps://other.test/two?key=abc\r\n", source)
        assertEquals(listOf(
            PlaylistEntry("http://tv.test/one.mp4", "First, with comma"),
            PlaylistEntry("https://other.test/two?key=abc", null)
        ), entries)
    }

    @Test fun plainPlaylistPreservesOrderAndDuplicates() {
        assertEquals(listOf("http://tv.test/a.mp4", "http://tv.test/a.mp4", "http://tv.test/lists/b.mp4"),
            VideoPlaylist.parse("/a.mp4\n/a.mp4\nb.mp4", source).map { it.uri })
    }

    @Test fun hlsMediaManifestRemainsOneStream() {
        assertEquals(listOf(PlaylistEntry(source.toString(), null)),
            VideoPlaylist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\nsegment.ts", source))
    }

    @Test fun hlsMasterManifestRemainsOneStream() {
        assertEquals(listOf(PlaylistEntry(source.toString(), null)),
            VideoPlaylist.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nlow.m3u8", source))
    }

    @Test fun queueFinishesAtLastItemAndCanGoBack() {
        val queue = PlaybackQueue()
        queue.load(VideoPlaylist.parse("one.mp4\ntwo.mp4", source))
        assertFalse(queue.hasPrevious)
        assertTrue(queue.hasNext)
        queue.next()
        assertEquals("http://tv.test/lists/two.mp4", queue.current.uri)
        assertFalse(queue.hasNext)
        assertTrue(queue.hasPrevious)
        queue.previous()
        assertEquals(0, queue.index)
        queue.clear()
        assertTrue(queue.entries.isEmpty())
        assertFalse(queue.hasNext)
        assertFalse(queue.hasPrevious)
    }
}
