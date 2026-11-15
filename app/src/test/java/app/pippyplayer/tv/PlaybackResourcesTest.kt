package app.pippyplayer.tv

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackResourcesTest {
    @Test(timeout = 5000) fun concurrentReadersDoNotWaitForAnotherPlayersCacheWriter() {
        val resources = PlaybackResources(org.robolectric.RuntimeEnvironment.getApplication())
        val bytes = ByteArray(1024) { it.toByte() }
        val factory = resources.progressiveDataSourceFactory(
            androidx.media3.datasource.DataSource.Factory { androidx.media3.datasource.ByteArrayDataSource(bytes) })
        val spec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://media.test/shared.mp4"))
        val first = factory.createDataSource()
        val second = factory.createDataSource()
        first.open(spec)
        second.open(spec)
        val actual = ByteArray(bytes.size)
        assertEquals(bytes.size, second.read(actual, 0, actual.size))
        assertArrayEquals(bytes, actual)
        second.close()
        first.close()
        resources.release()
    }

    private val timeline = SinglePeriodTimeline(60_000_000L, true, false, false, null,
        MediaItem.fromUri("https://media.test/movie.mp4"))
    private val period = MediaPeriodId(timeline.getUidOfPeriod(0))
    private fun parameters(id: PlayerId, us: Long, rebuffering: Boolean = false) =
        LoadControl.Parameters(id, timeline, period, 0L, us, 1f, true, rebuffering, C.TIME_UNSET, C.TIME_UNSET)

    @Test fun threePlayersShareBudgetAndCannotPreloadUntilOthersStop() {
        val control = PipLoadControl(3000)
        val ids = (1..3).map { PlayerId("$it") }
        for (id in ids) {
            control.register(id.name, 3000)
            control.onPrepared(id)
            control.onTracksSelected(parameters(id, 0), TrackGroupArray.EMPTY, emptyArray())
            assertTrue(control.shouldContinueLoading(parameters(id, 0)))
        }
        assertFalse(control.shouldContinuePreloading(ids[0], timeline, period, 0))
        val allocators = ids.map(control::getAllocator)
        val allocations = allocators.map { allocator ->
            List((PipLoadControl.TOTAL_SAMPLE_BYTES / 3 + allocator.individualAllocationLength - 1) /
                allocator.individualAllocationLength) { allocator.allocate() }
        }
        assertTrue(control.allocatedBytes >= PipLoadControl.TOTAL_SAMPLE_BYTES)
        for (id in ids) {
            assertFalse(control.shouldContinueLoading(parameters(id, 0)))
            assertTrue(control.shouldStartPlayback(parameters(id, 0)))
        }
        for ((index, allocator) in allocators.withIndex()) allocations[index].forEach(allocator::release)
        assertTrue(control.shouldContinueLoading(parameters(ids[2], 0)))
        control.onStopped(ids[1]); control.onStopped(ids[2])
        assertFalse(control.shouldContinueLoading(parameters(ids[0], 20_000_000)))
        assertTrue(control.shouldContinuePreloading(ids[0], timeline, period, 0))
        control.onReleased(ids[0])
    }

    @Test fun eachPlayersRebufferSettingIsHonouredIncludingLongThresholds() {
        val control = PipLoadControl(3000)
        val short = PlayerId("short"); val long = PlayerId("long")
        for ((id, ms) in listOf(short to 3000, long to 30_000)) {
            control.register(id.name, ms); control.onPrepared(id)
            control.onTracksSelected(parameters(id, 0), TrackGroupArray.EMPTY, emptyArray())
        }
        assertTrue(control.shouldStartPlayback(parameters(short, 3_000_000, true)))
        assertFalse(control.shouldStartPlayback(parameters(long, 20_000_000, true)))
        assertTrue(control.shouldContinueLoading(parameters(long, 20_000_000)))
        assertTrue(control.shouldStartPlayback(parameters(long, 30_000_000, true)))
        control.onReleased(short); control.onReleased(long)
    }
    @Test fun livePlaybackStartsWithinItsTargetLatency() {
        val control = PipLoadControl(3000)
        val id = PlayerId("live")
        control.register(id.name, 3000); control.onPrepared(id)
        val parameters = LoadControl.Parameters(id, timeline, period, 0L, 500_000L, 1f,
            true, true, 1_000_000L, C.TIME_UNSET)
        assertTrue(control.shouldStartPlayback(parameters))
        control.onReleased(id)
    }

}
