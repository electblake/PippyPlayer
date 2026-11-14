package app.pippyplayer.tv

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class VideoPlaylistTest {
    @Test fun missingMetadataSortsAfterKnownValuesWithoutInventingValues() {
        val queue = PlaybackQueue()
        queue.load(listOf(PlaylistEntry("unknown", null), PlaylistEntry("long", "Bravo", 20.0),
            PlaylistEntry("short", "Alpha", 10.0)))
        queue.sort(PlaylistSort.LENGTH_SHORTEST)
        assertEquals(listOf("short", "long", "unknown"), queue.entries.map { it.uri })
        queue.sort(PlaylistSort.LENGTH_LONGEST)
        assertEquals(listOf("long", "short", "unknown"), queue.entries.map { it.uri })
        queue.sort(PlaylistSort.NAME)
        assertEquals(listOf("short", "long", "unknown"), queue.entries.map { it.uri })
        assertNull(queue.entries.last().title)
        assertNull(queue.entries.last().durationSeconds)
    }
    @Test fun randomSortPreservesEntriesCurrentOccurrenceAndDefaultOrder() {
        val duplicate = PlaylistEntry("same", "Same")
        val items = listOf(duplicate, PlaylistEntry("other", "Other"), duplicate)
        val queue = PlaybackQueue()
        queue.load(items)
        queue.next()
        queue.next()
        queue.sort(PlaylistSort.RANDOM)
        assertEquals(items.groupingBy { it }.eachCount(), queue.entries.groupingBy { it }.eachCount())
        assertEquals(duplicate, queue.current)
        queue.sort(PlaylistSort.DEFAULT)
        assertEquals(items, queue.entries)
        assertEquals(2, queue.index)
        queue.sort(PlaylistSort.RANDOM)
        queue.clear()
        queue.load(items)
        assertEquals(PlaylistSort.RANDOM, queue.sorting)
        assertEquals(items.groupingBy { it }.eachCount(), queue.entries.groupingBy { it }.eachCount())
        assertEquals(0, queue.index)
    }

    @Test fun sortingPreservesCurrentOccurrenceAndRestoresOriginalOrder() {
        val duplicate = PlaylistEntry("same", "Bravo", 60.0)
        val items = listOf(duplicate, PlaylistEntry("long", "alpha", 3600.0), duplicate,
            PlaylistEntry("short", "Charlie", 2.5))
        val queue = PlaybackQueue()
        queue.load(items)
        queue.next()
        queue.next()
        queue.sort(PlaylistSort.LENGTH_SHORTEST)
        assertEquals(listOf(2.5, 60.0, 60.0, 3600.0), queue.entries.map { it.durationSeconds })
        assertEquals(2, queue.index)
        queue.sort(PlaylistSort.LENGTH_LONGEST)
        assertEquals(listOf(3600.0, 60.0, 60.0, 2.5), queue.entries.map { it.durationSeconds })
        assertEquals(2, queue.index)
        queue.sort(PlaylistSort.NAME)
        assertEquals(listOf("alpha", "Bravo", "Bravo", "Charlie"), queue.entries.map { it.title })
        assertEquals(2, queue.index)
        queue.sort(PlaylistSort.DEFAULT)
        assertEquals(items, queue.entries)
        assertEquals(2, queue.index)
    }

    @Test fun selectedSortAppliesToNewQueuesAndSurvivesClear() {
        val queue = PlaybackQueue()
        queue.sort(PlaylistSort.LENGTH_SHORTEST)
        val items = listOf(PlaylistEntry("long", "Long", 100.0), PlaylistEntry("short", "Short", 10.0))
        queue.load(items)
        assertEquals("short", queue.current.uri)
        queue.clear()
        queue.load(items)
        assertEquals("short", queue.current.uri)
        assertEquals(0, queue.index)
    }

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
        assertEquals(listOf(PlaylistEntry(source.toString(), null, mimeType = "application/x-mpegURL")),
            VideoPlaylist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\nsegment.ts", source))
    }

    @Test fun hlsMasterManifestRemainsOneStream() {
        assertEquals(listOf(PlaylistEntry(source.toString(), null, mimeType = "application/x-mpegURL")),
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
