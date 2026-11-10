package app.pippyplayer.tv

internal data class PlaybackSample(
    val timeMs: Long,
    val displayed: Int,
    val decoded: Int,
    val bytes: Int
) {
    fun ratesSince(previous: PlaybackSample): PlaybackRates {
        val seconds = (timeMs - previous.timeMs) / 1000.0
        // VLC 3 exposes cumulative byte counts as 32-bit integers; subtraction wraps too.
        val transferred = (bytes - previous.bytes).toUInt().toLong()
        return PlaybackRates(
            (displayed - previous.displayed) / seconds,
            (decoded - previous.decoded) / seconds,
            transferred * 8 / seconds / 1_000_000
        )
    }
}

internal data class PlaybackRates(val displayedFps: Double, val decodedFps: Double, val megabitsPerSecond: Double)
