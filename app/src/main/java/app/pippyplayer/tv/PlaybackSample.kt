package app.pippyplayer.tv

internal data class PlaybackSample(
    val timeMs: Long,
    val displayed: Int,
    val decoded: Int,
    val bytes: Long
) {
    fun ratesSince(previous: PlaybackSample): PlaybackRates {
        val seconds = (timeMs - previous.timeMs) / 1000.0
        val transferred = bytes - previous.bytes
        return PlaybackRates(
            (displayed - previous.displayed) / seconds,
            (decoded - previous.decoded) / seconds,
            transferred * 8 / seconds / 1_000_000
        )
    }
}

internal data class PlaybackRates(val displayedFps: Double, val decodedFps: Double, val megabitsPerSecond: Double)
