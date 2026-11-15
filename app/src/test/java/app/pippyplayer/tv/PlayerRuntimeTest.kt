package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
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
    private lateinit var resources: PlaybackResources
    @After fun releaseResources() { resources.release() }
    @Before fun clearSettings() {
        resources = PlaybackResources(context)
        listOf("playback", "player_1", "player_2").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun stopDuringSourceLoadingCannotRestartPlaybackWhenTheResultArrives() = runBlocking {
        val shared = PlaybackSettings(context)
        val gate = CompletableDeferred<List<PlaylistEntry>>()
        val runtime = PlayerRuntime(context, "1", shared, resources, SourceRequest { _, _ -> gate.await() })
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
        val first = PlayerRuntime(context, "1", shared, resources, SourceRequest { _, _ -> gate.await() })
        val second = PlayerRuntime(context, secondId, shared, resources)
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
        val first = PlayerRuntime(context, "1", shared, resources)
        val second = PlayerRuntime(context, shared.addPlayer(), shared, resources)
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
        val runtime = PlayerRuntime(context, "1", shared, resources, SourceRequest { _, _ -> emptyList() })
        runtime.resume()
        assertEquals(View.GONE, runtime.window.visibility)
        runtime.loadSource("empty", "request")
        assertEquals(0, runtime.player.mediaItemCount)
        assertEquals(View.GONE, runtime.window.visibility)
        assertFalse(runtime.loading.visible)
        runtime.skip(10000L)
        runtime.release()
    }

    @Test fun screenOffsetsFollowPhysicalAxesAcrossPositionPresets() {
        val shared = PlaybackSettings(context)
        val runtime = PlayerRuntime(context, "1", shared, resources)
        val metrics = runtime.resources.displayMetrics
        val initialFlags = runtime.layout.flags
        assertEquals(0, runtime.layout.x)
        assertEquals(0, runtime.layout.y)
        runtime.configure(Position.TOP_LEFT, null, null, null, offsetX = 10, offsetY = -20)
        val right = WindowGeometry.percentPixels(10, metrics.widthPixels)
        val up = WindowGeometry.percentPixels(-20, metrics.heightPixels)
        assertEquals(right, runtime.layout.x)
        assertEquals(up, runtime.layout.y)
        assertTrue(runtime.layout.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertEquals(initialFlags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, runtime.layout.flags)
        runtime.configure(Position.MIDDLE_CENTER, null, null, null)
        assertEquals(right, runtime.layout.x)
        assertEquals(up, runtime.layout.y)
        runtime.configure(Position.BOTTOM_RIGHT, null, null, null)
        assertEquals(-right, runtime.layout.x)
        assertEquals(-up, runtime.layout.y)
        runtime.configure(null, null, null, null, offsetX = 0, offsetY = 0)
        assertEquals(0, runtime.layout.x)
        assertEquals(0, runtime.layout.y)
        assertEquals(initialFlags, runtime.layout.flags)
        runtime.release()
    }

    @Test fun edgeCropKeepsTheFullVideoSurfaceAndScalesWithPlayerDimensions() {
        val runtime = PlayerRuntime(context, "1", PlaybackSettings(context), resources)
        val width = runtime.layout.width
        val height = runtime.layout.height
        runtime.configure(Position.BOTTOM_RIGHT, null, 1.5f, null, cropTop = -10, cropRight = -15, cropBottom = -20, cropLeft = -25)
        val left = -WindowGeometry.percentPixels(-25, width)
        val right = -WindowGeometry.percentPixels(-15, width)
        val top = -WindowGeometry.percentPixels(-10, height)
        val bottom = -WindowGeometry.percentPixels(-20, height)
        assertEquals(width - left - right, runtime.layout.width)
        assertEquals(height - top - bottom, runtime.layout.height)
        assertEquals(right, runtime.layout.x)
        assertEquals(bottom, runtime.layout.y)
        assertEquals(width, runtime.videoSurface.layoutParams.width)
        assertEquals(height, runtime.videoSurface.layoutParams.height)
        val videoLayout = runtime.videoSurface.layoutParams as FrameLayout.LayoutParams
        assertEquals(-left, videoLayout.leftMargin)
        assertEquals(-top, videoLayout.topMargin)
        assertEquals(1.5f, runtime.videoSurface.scaleX)

        runtime.configure(Position.TOP_LEFT, 60, null, null)
        runtime.aspect = 9f / 16f
        runtime.updateWindow()
        val (portraitWidth, portraitHeight) = WindowGeometry.dimensions(
            runtime.resources.displayMetrics.widthPixels, runtime.resources.displayMetrics.heightPixels, 60, runtime.aspect,
        )
        val crop = WindowGeometry.crop(portraitWidth, portraitHeight, -10, -15, -20, -25)
        assertEquals(crop.left, runtime.layout.x)
        assertEquals(crop.top, runtime.layout.y)
        assertEquals(crop.width(), runtime.layout.width)
        assertEquals(crop.height(), runtime.layout.height)
        assertEquals(portraitWidth, runtime.videoSurface.layoutParams.width)
        assertEquals(portraitHeight, runtime.videoSurface.layoutParams.height)

        runtime.configure(null, null, null, null, cropTop = -50, cropRight = -50, cropBottom = -50, cropLeft = -50)
        assertEquals(1, runtime.layout.width)
        assertEquals(1, runtime.layout.height)
        runtime.configure(null, null, null, null, cropTop = 0, cropRight = 0, cropBottom = 0, cropLeft = 0)
        assertEquals(portraitWidth, runtime.layout.width)
        assertEquals(portraitHeight, runtime.layout.height)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, videoLayout.width)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, videoLayout.height)
        assertEquals(0, videoLayout.leftMargin)
        assertEquals(0, videoLayout.topMargin)
        runtime.release()
    }

    @Test fun uncroppedPlaybackKeepsTheSurfaceMatchedAndSkipsUnchangedWindowLayouts() {
        val runtime = PlayerRuntime(context, "1", PlaybackSettings(context), resources)
        val actualManager = runtime.manager
        var windowUpdates = 0
        runtime.manager = object : WindowManager by actualManager {
            override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
                windowUpdates++
                actualManager.updateViewLayout(view, params)
            }
        }
        val surface = runtime.videoSurface
        val initialFlags = runtime.layout.flags
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, surface.layoutParams.width)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, surface.layoutParams.height)
        runtime.configure(null, null, null, 12)
        runtime.configure(null, null, 1.5f, null)
        runtime.updateWindow()
        assertEquals(12, runtime.settings.volume)
        assertEquals(1.5f, surface.scaleX)
        assertEquals(0, windowUpdates)
        assertEquals(initialFlags, runtime.layout.flags)
        runtime.configure(Position.TOP_LEFT, 50, null, null)
        assertEquals(1, windowUpdates)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, surface.layoutParams.width)
        assertEquals(FrameLayout.LayoutParams.MATCH_PARENT, surface.layoutParams.height)
        runtime.configure(Position.TOP_LEFT, 50, null, null)
        assertEquals(1, windowUpdates)
        runtime.release()
    }

    @Test fun pauseAndHideDuringALoadRemainInEffectWhenTheSourceCompletes() = runBlocking {
        val shared = PlaybackSettings(context)
        var gate = CompletableDeferred<List<PlaylistEntry>>()
        val runtime = PlayerRuntime(context, "1", shared, resources, SourceRequest { _, _ -> gate.await() })
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
    @Test fun largeQueueKeepsOnlyNeighboursAndPreservesDuplicateIdentityAcrossSortAndPause() {
        val runtime = PlayerRuntime(context, "1", PlaybackSettings(context), resources)
        runtime.queue.load(List(1778) { PlaylistEntry("https://media.test/same.mp4", "Item ${1778 - it}") })
        runtime.loadQueue()
        assertEquals(2, runtime.player.mediaItemCount)
        repeat(30) { runtime.next(); assertEquals(3, runtime.player.mediaItemCount) }
        assertEquals(30, runtime.queue.index)
        assertEquals("30", runtime.player.currentMediaItem!!.mediaId)
        runtime.player.seekTo(12_000L)
        runtime.sort(PlaylistSort.NAME)
        assertEquals("30", runtime.player.currentMediaItem!!.mediaId)
        assertEquals(12_000L, runtime.player.currentPosition)
        runtime.pause()
        assertEquals(androidx.media3.common.Player.STATE_IDLE, runtime.player.playbackState)
        assertEquals("Paused", runtime.playbackState())
        assertEquals(12_000L, runtime.player.currentPosition)
        runtime.resume()
        assertTrue(runtime.player.playWhenReady)
        assertEquals(12_000L, runtime.player.currentPosition)
        runtime.toggleVisibility()
        assertEquals(View.GONE, runtime.window.visibility)
        assertEquals(androidx.media3.common.Player.STATE_IDLE, runtime.player.playbackState)
        runtime.toggleVisibility()
        assertTrue(runtime.player.playWhenReady)
        runtime.sort(PlaylistSort.DEFAULT)
        runtime.previous()
        assertEquals(29, runtime.queue.index)
        runtime.release()
    }

}
