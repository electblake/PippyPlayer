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
        listOf("playback", "player_1", "player_2", "player_3").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun playersPersistIndependentSettingsAndShareOnlyHistory() {
        val first = PlaybackSettings(context)
        assertEquals(listOf("1"), first.playerIds)
        val id = first.addPlayer()
        assertEquals(listOf("1", "2"), PlaybackSettings(context).playerIds)
        val second = PlaybackSettings(context, id)
        first.volume = 12
        first.saveLayout(Position.TOP_LEFT, 45, 1.5f)
        first.sorting = PlaylistSort.NAME
        first.repeatOne = true
        first.save("http://first.test", 5000)
        first.recordPlayedUrl("https://first.test/video")
        second.volume = 78
        second.saveLayout(Position.BOTTOM_LEFT, 25, 2f)
        second.save("http://second.test", 2000)
        second.recordPlayedUrl("https://second.test/video")
        second.nameRecentUrl("https://first.test/video", "Shared name")
        val reopened = PlaybackSettings(context, "1")
        assertEquals(12, reopened.volume)
        assertEquals(45, reopened.size)
        assertEquals(1.5f, reopened.zoom)
        assertEquals(Position.TOP_LEFT, reopened.position)
        assertEquals(PlaylistSort.NAME, reopened.sorting)
        assertTrue(reopened.repeatOne)
        assertEquals("http://first.test", reopened.upnpServer)
        assertEquals(5000, reopened.networkCacheMs)
        assertEquals("https://first.test/video", reopened.lastPlayedUrl)
        assertEquals("https://second.test/video", second.lastPlayedUrl)
        assertEquals(78, PlaybackSettings(context, "2").volume)
        assertEquals(PlaylistSort.DEFAULT, second.sorting)
        assertFalse(second.repeatOne)
        assertEquals(second.recentUrls, reopened.recentUrls)
        assertEquals("Shared name", reopened.recentUrlNames.getString("https://first.test/video"))
    }

    @Test fun upgradeMigratesExistingSinglePlayerSettingsWithoutReentry() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://saved-server.test:9999")
            .putInt("networkCacheMs", 7000).putInt("volume", 23)
            .putString("sorting", "NAME").putString("lastPlayedUrl", "https://stash.test/performers/1")
            .putString("recentUrls", "[\"https://stash.test/performers/1\"]").commit()
        val settings = PlaybackSettings(context)
        assertEquals("http://saved-server.test:9999", settings.upnpServer)
        assertEquals(7000, settings.networkCacheMs)
        assertEquals(23, settings.volume)
        assertEquals(PlaylistSort.NAME, settings.sorting)
        assertEquals("https://stash.test/performers/1", settings.lastPlayedUrl)
        assertEquals("upnp://saved-server.test:9999/ctl?ObjectID=performers/1",
            MediaSource.resolveUrl(settings.lastPlayedUrl, settings.upnpServer))
        assertEquals(listOf(settings.lastPlayedUrl), settings.recentUrls)
        assertEquals("", PlaybackSettings(context, settings.addPlayer()).upnpServer)
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
        val settings = PlaybackSettings(context)
        assertEquals("http://saved-server.test:9999", settings.upnpServer)
        assertEquals(7000, settings.networkCacheMs)
        assertEquals(81, settings.volume)
        assertEquals(45, settings.size)
        assertEquals("http://second.test", PlaybackSettings(context, "2").upnpServer)
        settings.save("http://changed.test", 2000)
        assertEquals("http://changed.test", PlaybackSettings(context).upnpServer)
        settings.removePlayer("1")
        assertEquals("", PlaybackSettings(context).upnpServer)
        assertEquals(listOf("2"), PlaybackSettings(context).playerIds)
    }

    @Test fun upgradeKeepsAnAlreadyReenteredServer() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://old.test").commit()
        context.getSharedPreferences("player_1", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://new.test").commit()
        assertEquals("http://new.test", PlaybackSettings(context).upnpServer)
    }

    @Test fun deletionKeepsOtherPlayersAndHistoryAndAllowsAnEmptyCollection() {
        val first = PlaybackSettings(context)
        val secondId = first.addPlayer()
        val second = PlaybackSettings(context, secondId)
        first.volume = 12
        second.volume = 78
        second.recordPlayedUrl("https://shared.test/video")
        first.removePlayer(secondId)
        assertEquals(listOf("1"), first.playerIds)
        assertEquals(12, first.volume)
        assertEquals(50, PlaybackSettings(context, secondId).volume)
        assertEquals(listOf("https://shared.test/video"), first.recentUrls)
        first.removePlayer("1")
        assertEquals(emptyList<String>(), PlaybackSettings(context).playerIds)
        assertEquals("3", first.addPlayer())
        assertEquals(listOf("3"), PlaybackSettings(context).playerIds)
    }

    @Test fun newInstallationHasNoHistory() {
        assertEquals(emptyList<String>(), PlaybackSettings(context).recentUrls)
    }

    @Test fun displayNamesPersistWithoutChangingUrlsOrHistoryOrder() {
        val first = "https://media.test/video?q=one&name=\"A\""
        val second = "performer:Example"
        val settings = PlaybackSettings(context)
        settings.recordPlayedUrl(first)
        settings.recordPlayedUrl(second)
        settings.nameRecentUrl(first, "My favourites 片")
        val reopened = PlaybackSettings(context)
        assertEquals("My favourites 片", reopened.recentUrlNames.getString(first))
        assertEquals(listOf(second, first), reopened.recentUrls)
        assertEquals(second, reopened.lastPlayedUrl)
        reopened.recordPlayedUrl(first)
        assertEquals("My favourites 片", PlaybackSettings(context).recentUrlNames.getString(first))
        assertEquals(first, reopened.lastPlayedUrl)
        reopened.nameRecentUrl(first, "Renamed")
        assertEquals("Renamed", PlaybackSettings(context).recentUrlNames.getString(first))
        reopened.nameRecentUrl(first, "")
        assertFalse(PlaybackSettings(context).recentUrlNames.has(first))
        assertEquals(listOf(first, second), reopened.recentUrls)
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
