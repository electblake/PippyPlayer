package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import android.view.View
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerRuntimeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun clearSettings() {
        listOf("playback", "player_1", "player_2").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun stopDuringSourceLoadingCannotRestartPlaybackWhenTheResultArrives() = runBlocking {
        val shared = PlaybackSettings(context)
        val gate = CompletableDeferred<List<PlaylistEntry>>()
        val runtime = PlayerRuntime(context, "1", shared, SourceRequest { _, _ -> gate.await() })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runtime.loadSource("old", "request") }
        runtime.stop()
        gate.complete(listOf(PlaylistEntry("http://media.test/video.mp4", null)))
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals(0, runtime.queue.count)
        assertEquals(View.GONE, runtime.window.visibility)
        assertFalse(runtime.sourceRequest.loading)
        assertEquals("", runtime.settings.lastPlayedUrl)
        assertTrue(shared.recentUrls.isEmpty())
        runtime.release()
    }

    @Test fun deletingALoadingPlayerDoesNotResurrectSettingsOrAffectAnotherPlayer() = runBlocking {
        val shared = PlaybackSettings(context)
        val secondId = shared.addPlayer()
        val gate = CompletableDeferred<List<PlaylistEntry>>()
        val first = PlayerRuntime(context, "1", shared, SourceRequest { _, _ -> gate.await() })
        val second = PlayerRuntime(context, secondId, shared)
        second.configure(Position.TOP_LEFT, 45, 1.5f, 72)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { first.loadSource("old", "request") }
        first.release()
        shared.removePlayer("1")
        gate.complete(emptyList())
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals(listOf(secondId), shared.playerIds)
        assertThrows(NoSuchElementException::class.java) { shared.player("1") }
        assertEquals(72, second.volume)
        assertEquals(Position.TOP_LEFT, second.position)
        second.release()
    }

    @Test fun partialConfigurationKeepsOtherFieldsAndOtherPlayersUnchanged() {
        val shared = PlaybackSettings(context)
        val first = PlayerRuntime(context, "1", shared)
        val second = PlayerRuntime(context, shared.addPlayer(), shared)
        first.configure(Position.TOP_RIGHT, 50, 2f, 12)
        first.configure(null, null, null, 81)
        assertEquals(Position.TOP_RIGHT, first.position)
        assertEquals(50, first.size)
        assertEquals(2f, first.zoom)
        assertEquals(81, first.volume)
        assertEquals(50, second.volume)
        assertEquals(30, second.size)
        assertEquals(81, shared.player("1").volume)
        first.stop()
        assertEquals(50f, second.player.volume * 100f, 0.001f)
        first.release()
        second.release()
    }

    @Test fun emptySourcesAndResumeWithoutMediaDoNotShowBlackWindows() = runBlocking {
        val shared = PlaybackSettings(context)
        val runtime = PlayerRuntime(context, "1", shared, SourceRequest { _, _ -> emptyList() })
        runtime.resume()
        assertEquals(View.GONE, runtime.window.visibility)
        runtime.loadSource("empty", "request")
        assertEquals(0, runtime.player.mediaItemCount)
        assertEquals(View.GONE, runtime.window.visibility)
        assertFalse(runtime.loading.visible)
        runtime.skip(10000L)
        runtime.release()
    }

    @Test fun pauseAndHideDuringALoadRemainInEffectWhenTheSourceCompletes() = runBlocking {
        val shared = PlaybackSettings(context)
        var gate = CompletableDeferred<List<PlaylistEntry>>()
        val runtime = PlayerRuntime(context, "1", shared, SourceRequest { _, _ -> gate.await() })
        val entries = listOf(PlaylistEntry("https://media.test/video.mp4", "Video"))
        val first = async(start = CoroutineStart.UNDISPATCHED) { runtime.loadSource("one", "one") }
        runtime.pause()
        gate.complete(entries)
        first.await()
        assertFalse(runtime.player.playWhenReady)
        assertEquals(View.VISIBLE, runtime.window.visibility)
        gate = CompletableDeferred()
        val second = async(start = CoroutineStart.UNDISPATCHED) { runtime.loadSource("two", "two") }
        runtime.toggleVisibility()
        gate.complete(entries)
        second.await()
        assertFalse(runtime.player.playWhenReady)
        assertEquals(View.GONE, runtime.window.visibility)
        runtime.release()
    }
}
