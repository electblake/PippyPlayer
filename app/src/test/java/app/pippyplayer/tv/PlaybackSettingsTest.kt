package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSettings() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun newInstallationHasNoHistory() {
        assertEquals(emptyList<String>(), PlaybackSettings(context).recentUrls)
    }

    @Test fun volumePersistsAcrossSettingsInstancesIncludingMute() {
        assertEquals(50, PlaybackSettings(context).volume)
        PlaybackSettings(context).volume = 23
        val reopened = PlaybackSettings(context)
        assertEquals(23, reopened.volume)
        reopened.save("http://server.test", 5000)
        reopened.recordPlayedUrl("https://media.test/video.mp4")
        assertEquals(23, PlaybackSettings(context).volume)
        reopened.volume = 0
        assertEquals(0, PlaybackSettings(context).volume)
    }

    @Test fun historyPersistsAcrossSettingsInstancesAndReplayMovesUrlToFront() {
        val settings = PlaybackSettings(context)
        val first = "https://media.test/video?q=one&name=\"A\"#片"
        val second = "performer:Example"
        settings.save("http://server.test", 5000)
        settings.recordPlayedUrl(first)
        settings.recordPlayedUrl(second)
        assertEquals(listOf(second, first), PlaybackSettings(context).recentUrls)
        PlaybackSettings(context).recordPlayedUrl(first)

        val reopened = PlaybackSettings(context)
        assertEquals(listOf(first, second), reopened.recentUrls)
        assertEquals(first, reopened.lastPlayedUrl)
        assertEquals("http://server.test", reopened.upnpServer)
        assertEquals(5000, reopened.networkCacheMs)
        reopened.save("http://new-server.test", 3000)
        assertEquals(listOf(first, second), PlaybackSettings(context).recentUrls)
    }
}
