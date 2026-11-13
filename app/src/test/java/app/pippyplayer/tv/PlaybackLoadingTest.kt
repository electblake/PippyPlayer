package app.pippyplayer.tv

import org.junit.Assert.*
import org.junit.Test

class PlaybackLoadingTest {
    @Test fun openingShowsLoadingWithoutInventingProgress() {
        val loading = PlaybackLoading()
        loading.start()
        assertTrue(loading.visible)
        assertEquals("Loading…", loading.label)
        assertNull(loading.percent)
    }

    @Test fun playingEventDoesNotHideAnIncompleteRefill() {
        val loading = PlaybackLoading()
        loading.start()
        loading.buffering(35f)
        loading.playing()
        assertTrue(loading.visible)
        assertEquals("Buffering… 35%", loading.label)
        loading.buffering(100f)
        assertFalse(loading.visible)
    }

    @Test fun rebufferingAfterPlaybackShowsUntilComplete() {
        val loading = PlaybackLoading()
        loading.start()
        loading.playing()
        assertFalse(loading.visible)
        loading.buffering(0f)
        assertTrue(loading.visible)
        loading.buffering(100f)
        assertFalse(loading.visible)
    }

    @Test fun pausedBufferingStaysHiddenUntilResume() {
        val loading = PlaybackLoading()
        loading.start()
        loading.pause()
        loading.buffering(20f)
        assertFalse(loading.visible)
        loading.playing()
        assertTrue(loading.visible)
    }

    @Test fun terminalStateClearsProgressAndNextVideoStartsFresh() {
        val loading = PlaybackLoading()
        loading.buffering(40f)
        loading.clear()
        assertFalse(loading.visible)
        assertNull(loading.percent)
        loading.start()
        assertEquals("Loading…", loading.label)
        assertTrue(loading.visible)
    }
}
