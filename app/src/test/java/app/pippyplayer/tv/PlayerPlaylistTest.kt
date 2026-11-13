package app.pippyplayer.tv

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerPlaylistTest {
    private lateinit var player: ExoPlayer
    private val queue = PlaybackQueue()

    @Before fun createPlayer() {
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        queue.load(listOf(
            PlaylistEntry("https://media.test/duplicate.mp4", "Bravo"),
            PlaylistEntry("https://media.test/zulu.mp4", "Zulu"),
            PlaylistEntry("https://media.test/duplicate.mp4", "Bravo")))
        player.setMediaItems(queue.mediaItems(), 2, 12_000L)
        queue.select(2)
        player.volume = 0.23f
    }

    @After fun releasePlayer() { player.release() }

    @Test fun sortingPreservesDuplicateOccurrencePositionAndVolume() {
        queue.sort(PlaylistSort.NAME)
        player.applyQueueOrder(queue.itemIds)
        assertEquals(queue.itemIds, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        assertEquals("2", player.currentMediaItem!!.mediaId)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(0.23f, player.volume, 0f)
        queue.sort(PlaylistSort.DEFAULT)
        player.applyQueueOrder(queue.itemIds)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(12_000L, player.currentPosition)
    }

    @Test fun navigationAndNewSourcePreserveVolume() {
        player.seekToPreviousMediaItem()
        assertEquals("1", player.currentMediaItem!!.mediaId)
        player.seekToNextMediaItem()
        assertEquals("2", player.currentMediaItem!!.mediaId)
        player.stop()
        player.clearMediaItems()
        player.setMediaItems(queue.mediaItems())
        assertEquals(0.23f, player.volume, 0f)
    }

    @Test fun extensionlessHlsRetainsItsSourceType() {
        queue.load(VideoPlaylist.parse("#EXTM3U\n#EXT-X-TARGETDURATION:3\nsegment.ts", java.net.URI("https://media.test/stream?id=1")))
        assertEquals("application/x-mpegURL", queue.mediaItems().single().localConfiguration!!.mimeType)
    }

    @Test fun preloadingStopsAtCombinedSampleAllocationThreshold() {
        val loadControl = PipLoadControl(3000)
        val playerId = androidx.media3.exoplayer.analytics.PlayerId.UNSET
        val allocator = loadControl.getAllocator(playerId)
        val timeline = androidx.media3.common.Timeline.EMPTY
        val period = androidx.media3.exoplayer.source.MediaSource.MediaPeriodId(Any())
        assertTrue(loadControl.shouldContinuePreloading(playerId, timeline, period, 0L))
        val allocations = Array(20 * 1024 * 1024 / allocator.individualAllocationLength) { allocator.allocate() }
        assertFalse(loadControl.shouldContinuePreloading(playerId, timeline, period, 0L))
        allocations.forEach { allocator.release(it) }
        assertTrue(loadControl.shouldContinuePreloading(playerId, timeline, period, 0L))
    }
}
