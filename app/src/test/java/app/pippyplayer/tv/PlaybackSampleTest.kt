package app.pippyplayer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSampleTest {
    @Test fun usesElapsedTimeAndDecimalMegabits() {
        val rates = PlaybackSample(3500, 60, 75, 2_500_100)
            .ratesSince(PlaybackSample(1000, 0, 0, 100))
        assertEquals(24.0, rates.displayedFps, 0.0001)
        assertEquals(30.0, rates.decodedFps, 0.0001)
        assertEquals(8.0, rates.megabitsPerSecond, 0.0001)
    }

    @Test fun stalledCountersProduceZeroRates() {
        val rates = PlaybackSample(2000, 50, 50, 1000)
            .ratesSince(PlaybackSample(1000, 50, 50, 1000))
        assertEquals(PlaybackRates(0.0, 0.0, 0.0), rates)
    }

    @Test fun byteCountersBeyondTwoGigabytesKeepAccurateRates() {
        val rates = PlaybackSample(2000, 60, 60, 3_000_002_000L)
            .ratesSince(PlaybackSample(1000, 30, 30, 3_000_000_000L))
        assertEquals(0.016, rates.megabitsPerSecond, 0.0001)
    }
}
