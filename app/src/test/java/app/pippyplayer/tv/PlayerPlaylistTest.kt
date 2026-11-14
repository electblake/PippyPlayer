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
        player = ExoPlayer.Builder(RuntimeEnvironment.getApplication())
            .setLoadControl(PipLoadControl(3000))
            .build()
        queue.load(listOf(
            PlaylistEntry("https://media.test/duplicate.mp4", "Bravo"),
            PlaylistEntry("https://media.test/zulu.mp4", "Zulu"),
            PlaylistEntry("https://media.test/duplicate.mp4", "Bravo")))
        player.setMediaItems(queue.mediaItems(), 2, 12_000L)
        queue.select(2)
        player.volume = 0.23f
    }

    @After fun releasePlayer() { player.release() }

    @Test fun repeatOneLoopsCurrentItemAndCanBeDisabledWithoutChangingPlayback() {
        player.pause()
        player.toggleRepeatOne()
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, player.repeatMode)
        assertEquals(2, player.currentTimeline.getNextWindowIndex(2, player.repeatMode, false))
        assertFalse(player.playWhenReady)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(0.23f, player.volume, 0f)
        player.seekToPreviousMediaItem()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(1, player.currentTimeline.getNextWindowIndex(1, player.repeatMode, false))
        player.toggleRepeatOne()
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, player.repeatMode)
        assertEquals(2, player.currentTimeline.getNextWindowIndex(1, player.repeatMode, false))
        assertFalse(player.playWhenReady)
    }

    @Test fun hidingPausesAndShowingResumesWithoutLosingPositionOrVolume() {
        val window = android.view.View(RuntimeEnvironment.getApplication())
        player.play()
        player.setWindowVisible(window, false)
        assertFalse(player.playWhenReady)
        assertEquals(android.view.View.GONE, window.visibility)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(2, player.currentMediaItemIndex)
        player.setWindowVisible(window, true)
        assertTrue(player.playWhenReady)
        assertEquals(android.view.View.VISIBLE, window.visibility)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(0.23f, player.volume, 0f)
    }

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

    @Test fun customLoadControlForwardsLifecycleAndBufferDecisions() {
        val control = PipLoadControl(3000)
        val id = androidx.media3.exoplayer.analytics.PlayerId.UNSET
        val timeline = androidx.media3.exoplayer.source.SinglePeriodTimeline(
            60_000_000L, true, false, false, null,
            androidx.media3.common.MediaItem.fromUri("https://media.test/video.mp4"))
        val period = androidx.media3.exoplayer.source.MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))
        fun parameters(bufferedUs: Long, rebuffering: Boolean = false) = androidx.media3.exoplayer.LoadControl.Parameters(
            id, timeline, period, 0L, bufferedUs, 1f, true, rebuffering,
            androidx.media3.common.C.TIME_UNSET, androidx.media3.common.C.TIME_UNSET)
        assertEquals(0L, control.getBackBufferDurationUs(id))
        assertFalse(control.retainBackBufferFromKeyframe(id))
        control.onPrepared(id)
        control.onTracksSelected(parameters(0L), androidx.media3.exoplayer.source.TrackGroupArray.EMPTY, emptyArray())
        assertTrue(control.shouldContinueLoading(parameters(0L)))
        assertFalse(control.shouldContinuePreloading(id, timeline, period, 0L))
        assertFalse(control.shouldStartPlayback(parameters(0L)))
        assertTrue(control.shouldStartPlayback(parameters(1_500_000L)))
        assertFalse(control.shouldStartPlayback(parameters(1_500_000L, true)))
        assertTrue(control.shouldStartPlayback(parameters(3_000_000L, true)))
        assertFalse(control.shouldContinueLoading(parameters(20_000_000L)))
        assertTrue(control.shouldContinuePreloading(id, timeline, period, 0L))
        control.onStopped(id)
        control.onPrepared(id)
        control.onReleased(id)
    }
}
