package app.pippyplayer.tv

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import java.util.concurrent.ConcurrentHashMap

/** All decisions run on one playback looper. Targets exclude codec/GPU memory. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PipLoadControl(private val rebufferMs: Int) : LoadControl {
    companion object { const val TOTAL_SAMPLE_BYTES = 32 * 1024 * 1024 }
    private val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
    private val delegate = DefaultLoadControl.Builder().setAllocator(allocator)
        .setBufferDurationsMs(maxOf(10_000, rebufferMs), maxOf(20_000, rebufferMs), 1500, rebufferMs)
        .setTargetBufferBytes(16 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(false)
        .setBackBuffer(0, false).build()
    private val active = linkedSetOf<PlayerId>()
    private val loading = mutableMapOf<PlayerId, Boolean>()
    private val thresholds = ConcurrentHashMap<String, Int>()
    val allocatedBytes get() = allocator.totalBytesAllocated
    fun register(name: String, thresholdMs: Int) { thresholds[name] = thresholdMs }
    fun unregister(name: String) { thresholds.remove(name) }
    private fun targetBytes() = minOf(16 * 1024 * 1024, TOTAL_SAMPLE_BYTES / maxOf(1, active.size))
    override fun onPrepared(playerId: PlayerId) { active.add(playerId); loading[playerId] = false; delegate.onPrepared(playerId) }
    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) {
        active.remove(playerId); loading.remove(playerId); delegate.onStopped(playerId)
    }
    override fun onReleased(playerId: PlayerId) {
        active.remove(playerId); loading.remove(playerId); delegate.onReleased(playerId)
    }
    override fun getAllocator(playerId: PlayerId) = delegate.getAllocator(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId) = 0L
    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = false
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val threshold = thresholds.getValue(parameters.playerId.name)
        val bufferedMs = parameters.bufferedDurationUs / (1000 * parameters.playbackSpeed)
        val wantsData = bufferedMs < maxOf(10_000, threshold) ||
            (bufferedMs < maxOf(20_000, threshold) && loading.getValue(parameters.playerId))
        loading[parameters.playerId] = wantsData
        return wantsData && allocatedBytes < TOTAL_SAMPLE_BYTES &&
            getAllocator(parameters.playerId).totalBytesAllocated < targetBytes()
    }
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val threshold = if (parameters.rebuffering) thresholds.getValue(parameters.playerId.name) else 1500
        val thresholdUs = if (parameters.targetLiveOffsetUs == C.TIME_UNSET) threshold * 1000L
            else minOf(threshold * 1000L, parameters.targetLiveOffsetUs / 2)
        return parameters.bufferedDurationUs / parameters.playbackSpeed >= thresholdUs ||
            getAllocator(parameters.playerId).totalBytesAllocated >= targetBytes()
    }
    override fun shouldContinuePreloading(playerId: PlayerId, timeline: Timeline,
        mediaPeriodId: MediaPeriodId, bufferedDurationUs: Long): Boolean =
        active.size <= 1 && loading.values.none { it } && allocatedBytes < targetBytes() &&
            bufferedDurationUs < 3_000_000L
}
