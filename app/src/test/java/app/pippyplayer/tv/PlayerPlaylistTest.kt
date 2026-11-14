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

    @Test fun directPlaybackRequestsRevealHiddenPlayerAndHidingStillPauses() {
        val window = android.view.View(RuntimeEnvironment.getApplication())
        player.addListener(PlayerVisibilityListener(window))
        player.setWindowVisible(window, false)
        player.play()
        assertEquals(android.view.View.VISIBLE, window.visibility)
        assertTrue(player.playWhenReady)
        player.setWindowVisible(window, false)
        assertEquals(android.view.View.GONE, window.visibility)
        assertFalse(player.playWhenReady)
        player.playWhenReady = true
        assertEquals(android.view.View.VISIBLE, window.visibility)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(0.23f, player.volume, 0f)
    }

    @Test fun playbackStartingRevealsWindowWithoutChangingPauseState() {
        val window = android.view.View(RuntimeEnvironment.getApplication())
        window.visibility = android.view.View.GONE
        val listener = PlayerVisibilityListener(window)
        listener.onIsPlayingChanged(false)
        assertEquals(android.view.View.GONE, window.visibility)
        listener.onIsPlayingChanged(true)
        assertEquals(android.view.View.VISIBLE, window.visibility)
    }

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
        assertEquals(queue.itemIds, traversalOrder(player))
        assertEquals("2", player.currentMediaItem!!.mediaId)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(1, queue.index)
        assertEquals(12_000L, player.currentPosition)
        assertEquals(0.23f, player.volume, 0f)
        queue.sort(PlaylistSort.DEFAULT)
        player.applyQueueOrder(queue.itemIds)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(12_000L, player.currentPosition)
    }

    private fun traversalOrder(target: ExoPlayer): List<String> = buildList {
        var index = target.currentTimeline.getFirstWindowIndex(true)
        while (index != androidx.media3.common.C.INDEX_UNSET) {
            add(target.getMediaItemAt(index).mediaId)
            index = target.currentTimeline.getNextWindowIndex(index, androidx.media3.common.Player.REPEAT_MODE_OFF, true)
        }
    }

    @Test fun twoLargeQueuesSortWithOneTimelineUpdateAndKeepPlaybackAndNavigation() {
        val second = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            for (target in listOf(player, second)) {
                val large = PlaybackQueue()
                large.load(List(1778) { index -> PlaylistEntry("https://media.test/$index.mp4", "Video $index") })
                target.setMediaItems(large.mediaItems(), 900, 42_000L)
                large.select(900)
                target.applyQueueOrder(large.itemIds)
                target.volume = 0.37f
                target.play()
                var timelineChanges = 0
                var transitions = 0
                target.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { timelineChanges++ }
                    override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { transitions++ }
                })
                for (sort in listOf(PlaylistSort.RANDOM, PlaylistSort.NAME, PlaylistSort.DEFAULT)) {
                    timelineChanges = 0
                    transitions = 0
                    large.sort(sort)
                    target.applyQueueOrder(large.itemIds)
                    assertEquals(large.itemIds, traversalOrder(target))
                    assertEquals("900", target.currentMediaItem!!.mediaId)
                    assertEquals(42_000L, target.currentPosition)
                    assertEquals(0.37f, target.volume, 0f)
                    assertTrue(target.playWhenReady)
                    val nextId = if (target.nextMediaItemIndex == androidx.media3.common.C.INDEX_UNSET) null
                        else target.getMediaItemAt(target.nextMediaItemIndex).mediaId
                    val previousId = if (target.previousMediaItemIndex == androidx.media3.common.C.INDEX_UNSET) null
                        else target.getMediaItemAt(target.previousMediaItemIndex).mediaId
                    assertEquals(large.itemIds.getOrNull(large.index + 1), nextId)
                    assertEquals(large.itemIds.getOrNull(large.index - 1), previousId)
                    assertEquals(1, timelineChanges)
                    assertEquals(0, transitions)
                }
                target.seekToNextMediaItem()
                assertEquals("901", target.currentMediaItem!!.mediaId)
                target.seekToPreviousMediaItem()
                assertEquals("900", target.currentMediaItem!!.mediaId)
                large.load(List(5) { PlaylistEntry("https://media.test/new$it.mp4", "New $it") })
                target.setMediaItems(large.mediaItems(), 0, 0)
                target.applyQueueOrder(large.itemIds)
                assertEquals(large.itemIds, traversalOrder(target))
            }
        } finally { second.release() }
    }

    @Test fun loadingOverlayHidesOnReadyOrPlayingWithoutWaitingForBatchedEvents() {
        val context = RuntimeEnvironment.getApplication()
        val firstView = android.view.View(context)
        val secondView = android.view.View(context)
        val firstLoading = PlayerLoadingListener(player, firstView, android.widget.TextView(context))
        val secondLoading = PlayerLoadingListener(player, secondView, android.widget.TextView(context))
        firstView.visibility = android.view.View.VISIBLE
        secondView.visibility = android.view.View.VISIBLE
        secondLoading.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY)
        assertEquals(android.view.View.GONE, secondView.visibility)
        assertEquals(android.view.View.VISIBLE, firstView.visibility)
        secondView.visibility = android.view.View.VISIBLE
        secondLoading.onIsPlayingChanged(true)
        assertEquals(android.view.View.GONE, secondView.visibility)
        firstLoading.onPlaybackStateChanged(androidx.media3.common.Player.STATE_IDLE)
        assertEquals(android.view.View.GONE, firstView.visibility)
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
