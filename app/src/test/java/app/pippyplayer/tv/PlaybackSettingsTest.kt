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

    private val shared get() = PlaybackSettings(context)

    @Before fun clearSettings() {
        listOf("playback", "player_1", "player_2", "player_3").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun playersPersistIndependentSettingsAndShareServerAndHistory() {
        val first = shared.player("1")
        assertEquals(listOf("1"), shared.playerIds)
        val id = shared.addPlayer()
        assertEquals(listOf("1", "2"), PlaybackSettings(context).playerIds)
        val second = shared.player(id)
        first.volume = 12
        first.saveLayout(Position.TOP_LEFT, 45, 1.5f)
        first.sorting = PlaylistSort.NAME
        first.repeatOne = true
        shared.upnpServer = "http://first.test"
        first.saveNetworkCacheMs(5000)
        first.lastPlayedUrl = "https://first.test/video"
        shared.recordPlayedUrl("https://first.test/video")
        second.volume = 78
        second.saveLayout(Position.BOTTOM_LEFT, 25, 2f)
        shared.upnpServer = "http://second.test"
        second.saveNetworkCacheMs(2000)
        second.lastPlayedUrl = "https://second.test/video"
        shared.recordPlayedUrl("https://second.test/video")
        shared.nameRecentUrl("https://first.test/video", "Shared name")
        val reopened = shared.player("1")
        assertEquals(12, reopened.volume)
        assertEquals(45, reopened.size)
        assertEquals(1.5f, reopened.zoom)
        assertEquals(Position.TOP_LEFT, reopened.position)
        assertEquals(PlaylistSort.NAME, reopened.sorting)
        assertTrue(reopened.repeatOne)
        assertEquals("http://second.test", shared.upnpServer)
        assertEquals(2000, second.networkCacheMs)
        assertEquals(5000, reopened.networkCacheMs)
        assertEquals("https://first.test/video", reopened.lastPlayedUrl)
        assertEquals("https://second.test/video", second.lastPlayedUrl)
        assertEquals(78, shared.player("2").volume)
        assertEquals(PlaylistSort.DEFAULT, second.sorting)
        assertFalse(second.repeatOne)
        assertEquals("Shared name", shared.recentUrlNames.getString("https://first.test/video"))
    }

    @Test fun upgradeMigratesExistingSinglePlayerSettingsWithoutReentry() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://saved-server.test:9999")
            .putInt("networkCacheMs", 7000).putInt("volume", 23)
            .putString("sorting", "NAME").putString("lastPlayedUrl", "https://stash.test/performers/1")
            .putString("recentUrls", "[\"https://stash.test/performers/1\"]").commit()
        val settings = shared.player("1")
        assertEquals("http://saved-server.test:9999", shared.upnpServer)
        assertEquals(7000, settings.networkCacheMs)
        assertEquals(23, settings.volume)
        assertEquals(PlaylistSort.NAME, settings.sorting)
        assertEquals("https://stash.test/performers/1", settings.lastPlayedUrl)
        assertEquals("upnp://saved-server.test:9999/ctl?ObjectID=performers/1",
            MediaSource.resolveUrl(settings.lastPlayedUrl, shared.upnpServer))
        assertEquals(listOf(settings.lastPlayedUrl), shared.recentUrls)
        shared.addPlayer()
        assertEquals("http://saved-server.test:9999", shared.upnpServer)
    }

    @Test fun upgradeThrough012RestoresMissingServerWithoutOverwritingNewPlayerSettings() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("players", "[\"1\",\"2\"]").putInt("nextPlayerId", 3)
            .putString("upnpServer", "http://saved-server.test:9999")
            .putInt("volume", 23).putInt("networkCacheMs", 7000).commit()
        context.getSharedPreferences("player_1", Context.MODE_PRIVATE).edit()
            .putInt("volume", 81).putInt("size", 45).commit()
        context.getSharedPreferences("player_2", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://second.test").commit()
        val settings = shared.player("1")
        assertEquals("http://saved-server.test:9999", shared.upnpServer)
        assertEquals(7000, settings.networkCacheMs)
        assertEquals(81, settings.volume)
        assertEquals(45, settings.size)
        assertEquals("http://saved-server.test:9999", shared.upnpServer)
        shared.upnpServer = "http://changed.test"
        settings.saveNetworkCacheMs(2000)
        assertEquals("http://changed.test", PlaybackSettings(context).upnpServer)
        shared.removePlayer("1")
        assertEquals("http://changed.test", PlaybackSettings(context).upnpServer)
        assertEquals(listOf("2"), PlaybackSettings(context).playerIds)
    }

    @Test fun upgradeKeepsAnAlreadyReenteredServer() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://old.test").commit()
        context.getSharedPreferences("player_1", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://new.test").commit()
        assertEquals("http://new.test", shared.upnpServer)
        assertEquals("http://new.test", PlaybackSettings(context).upnpServer)
        PlaybackSettings(context).upnpServer = "http://latest.test"
        assertEquals("http://latest.test", shared.upnpServer)
    }

    @Test fun deletionKeepsOtherPlayersAndHistoryAndAllowsAnEmptyCollection() {
        val first = shared.player("1")
        val secondId = shared.addPlayer()
        val second = shared.player(secondId)
        first.volume = 12
        second.volume = 78
        second.lastPlayedUrl = "https://shared.test/video"
        shared.recordPlayedUrl("https://shared.test/video")
        shared.removePlayer(secondId)
        assertEquals(listOf("1"), shared.playerIds)
        assertEquals(12, first.volume)
        assertThrows(NoSuchElementException::class.java) { shared.player(secondId) }
        assertEquals(listOf("https://shared.test/video"), shared.recentUrls)
        shared.removePlayer("1")
        assertEquals(emptyList<String>(), PlaybackSettings(context).playerIds)
        assertEquals("3", shared.addPlayer())
        assertEquals(listOf("3"), PlaybackSettings(context).playerIds)
    }

    @Test fun newInstallationHasNoHistory() {
        assertEquals(emptyList<String>(), PlaybackSettings(context).recentUrls)
    }

    @Test fun displayNamesPersistWithoutChangingUrlsOrHistoryOrder() {
        val first = "https://media.test/video?q=one&name=\"A\""
        val second = "performer:Example"
        val settings = shared.player("1")
        settings.lastPlayedUrl = first
        shared.recordPlayedUrl(first)
        settings.lastPlayedUrl = second
        shared.recordPlayedUrl(second)
        shared.nameRecentUrl(first, "My favourites 片")
        val reopened = shared.player("1")
        assertEquals("My favourites 片", shared.recentUrlNames.getString(first))
        assertEquals(listOf(second, first), shared.recentUrls)
        assertEquals(second, reopened.lastPlayedUrl)
        reopened.lastPlayedUrl = first
        shared.recordPlayedUrl(first)
        assertEquals("My favourites 片", PlaybackSettings(context).recentUrlNames.getString(first))
        assertEquals(first, reopened.lastPlayedUrl)
        shared.nameRecentUrl(first, "Renamed")
        assertEquals("Renamed", PlaybackSettings(context).recentUrlNames.getString(first))
        shared.nameRecentUrl(first, "")
        assertFalse(PlaybackSettings(context).recentUrlNames.has(first))
        assertEquals(listOf(first, second), shared.recentUrls)
    }

    @Test fun volumePersistsAcrossSettingsInstancesIncludingMute() {
        assertEquals(50, shared.player("1").volume)
        shared.player("1").volume = 23
        val reopened = shared.player("1")
        assertEquals(23, reopened.volume)
        shared.upnpServer = "http://server.test"
        reopened.saveNetworkCacheMs(5000)
        reopened.lastPlayedUrl = "https://media.test/video.mp4"
        shared.recordPlayedUrl("https://media.test/video.mp4")
        assertEquals(23, shared.player("1").volume)
        reopened.volume = 0
        assertEquals(0, shared.player("1").volume)
    }

    @Test fun historyPersistsAcrossSettingsInstancesAndReplayMovesUrlToFront() {
        val settings = shared.player("1")
        val first = "https://media.test/video?q=one&name=\"A\"#片"
        val second = "performer:Example"
        shared.upnpServer = "http://server.test"
        settings.saveNetworkCacheMs(5000)
        settings.lastPlayedUrl = first
        shared.recordPlayedUrl(first)
        settings.lastPlayedUrl = second
        shared.recordPlayedUrl(second)
        assertEquals(listOf(second, first), PlaybackSettings(context).recentUrls)
        shared.player("1").lastPlayedUrl = first
        shared.recordPlayedUrl(first)

        val reopened = shared.player("1")
        assertEquals(listOf(first, second), shared.recentUrls)
        assertEquals(first, reopened.lastPlayedUrl)
        assertEquals("http://server.test", shared.upnpServer)
        assertEquals(5000, reopened.networkCacheMs)
        shared.upnpServer = "http://new-server.test"
        reopened.saveNetworkCacheMs(3000)
        assertEquals(listOf(first, second), PlaybackSettings(context).recentUrls)
    }
}
