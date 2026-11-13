package app.pippyplayer.tv

import org.junit.Assert.*
import org.junit.Test

class PlaybackLoadingTest {
    @Test fun initialBufferingShowsLoading() {
        assertEquals(PlaybackLoading(true, "Loading…"), PlaybackLoading.from(true, true, false))
    }
    @Test fun playbackRefillShowsBuffering() {
        assertEquals(PlaybackLoading(true, "Buffering…"), PlaybackLoading.from(true, true, true))
    }
    @Test fun pausedPlaybackDoesNotShowSpinnerWhileDataLoads() {
        assertFalse(PlaybackLoading.from(true, false, true).visible)
    }
    @Test fun readyEndedAndIdleStatesHideSpinner() {
        assertFalse(PlaybackLoading.from(false, true, true).visible)
        assertFalse(PlaybackLoading.from(false, false, false).visible)
    }
}
