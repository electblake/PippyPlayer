package app.pippyplayer.tv

import android.app.Application
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaylistCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val entries = listOf(PlaylistEntry("https://media.test/a", "Name 片", 42.5, "video/mp4"))

    @Test fun diskCacheSurvivesNewInstancesAndExpiresAtConfiguredDuration() = runBlocking {
        val directory = folder.newFolder()
        var time = 0L
        var requests = 0
        val fetch: (String) -> LoadedPlaylist = { requests++; LoadedPlaylist(entries, true) }
        val first = PlaylistCache(directory, fetch) { time }
        assertEquals(entries, first.load("performers/1", "http://server.test:1338", 2))
        val reopened = PlaylistCache(directory, fetch) { time }
        time = 7_199_999
        assertEquals(entries, reopened.load("https://stash.test/performers/1/scenes", "http://server.test:1338", 2))
        assertEquals(1, requests)
        time = 7_200_000
        assertEquals(entries, reopened.load("performers/1", "http://server.test:1338", 2))
        assertEquals(2, requests)
        reopened.load("performers/1", "http://another.test:1338", 2)
        assertEquals(3, requests)
    }

    @Test fun concurrentPlayersShareOneFetch() = runBlocking {
        val directory = folder.newFolder()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val fetch: (String) -> LoadedPlaylist = {
            requests.incrementAndGet()
            entered.countDown()
            release.await()
            LoadedPlaylist(entries, true)
        }
        val first = async(Dispatchers.Default) { PlaylistCache(directory, fetch).load("http://media.test/list.m3u", "", 24) }
        withContext(Dispatchers.IO) { entered.await() }
        val second = async(Dispatchers.Default) { PlaylistCache(directory, fetch).load("http://media.test/list.m3u", "", 24) }
        release.countDown()
        assertEquals(entries, first.await())
        assertEquals(entries, second.await())
        assertEquals(1, requests.get())
    }

    @Test fun clearDuringFetchDoesNotRepopulateCacheOrTouchSavedState() = runBlocking {
        val directory = folder.newFolder()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cache = PlaylistCache(directory, {
            entered.countDown()
            release.await()
            LoadedPlaylist(entries, true)
        })
        val shared = PlaybackSettings(RuntimeEnvironment.getApplication())
        shared.upnpServer = "http://saved.test"
        shared.playlistCacheHours = 5
        shared.recordPlayedUrl("http://saved.test/list")
        shared.nameRecentUrl("http://saved.test/list", "Saved name")
        val history = shared.recentUrls
        val names = shared.recentUrlNames.toString()
        val players = shared.playerIds
        val pending = async(Dispatchers.Default) { cache.load("http://media.test/list.m3u", "", 24) }
        withContext(Dispatchers.IO) { entered.await() }
        cache.clear()
        release.countDown()
        assertEquals(entries, pending.await())
        assertTrue(directory.listFiles()!!.isEmpty())
        assertEquals("http://saved.test", shared.upnpServer)
        assertEquals(5, PlaybackSettings(RuntimeEnvironment.getApplication()).playlistCacheHours)
        assertEquals(history, shared.recentUrls)
        assertEquals(names, shared.recentUrlNames.toString())
        assertEquals(players, shared.playerIds)
    }

    @Test fun zeroDurationAndNonPlaylistSourcesDoNotCacheAndClearForcesRefetch() = runBlocking {
        val directory = File(folder.root, "cache")
        var requests = 0
        val cache = PlaylistCache(directory, { requests++; LoadedPlaylist(entries, true) })
        repeat(2) { cache.load("http://media.test/list.m3u", "", 0) }
        assertEquals(2, requests)
        assertFalse(directory.exists())
        repeat(2) { cache.load("http://media.test/list.m3u", "", 24) }
        assertEquals(3, requests)
        cache.clear()
        cache.load("http://media.test/list.m3u", "", 24)
        assertEquals(4, requests)
        val direct = PlaylistCache(directory, { requests++; LoadedPlaylist(entries, false) })
        repeat(2) { direct.load("http://media.test/video", "", 24) }
        assertEquals(6, requests)
    }
}
