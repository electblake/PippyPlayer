package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsActivityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSettings() {
        listOf("playback", "player_1", "player_2").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun changingSelectedPlayerDoesNotReplaceTheSharedServerInput() {
        val settings = PlaybackSettings(context)
        settings.upnpServer = "http://saved.test"
        settings.player(settings.addPlayer()).name = "Kitchen TV"
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { controller ->
            val views = descendants(controller.get().window.decorView)
            val server = views.filterIsInstance<EditText>().single { it.contentDescription == "UPnP server URL" }
            server.setText("http://new.test")
            val selector = views.filterIsInstance<Spinner>().single()
            assertEquals("Player 1", selector.adapter.getItem(0))
            assertEquals("Kitchen TV", selector.adapter.getItem(1))
            selector.setSelection(1)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("http://new.test", server.text.toString())
            views.filterIsInstance<Button>().single { it.text == "Save shared server" }.performClick()
            assertEquals("http://new.test", PlaybackSettings(context).upnpServer)
            assertEquals("http://new.test", PlaybackSettings(context).upnpServer)
        }
    }

    @Test fun sharedServerCanBeSavedWithoutAnyPlayers() {
        PlaybackSettings(context).removePlayer("1")
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { controller ->
            val views = descendants(controller.get().window.decorView)
            views.filterIsInstance<EditText>().single { it.contentDescription == "UPnP server URL" }
                .setText("http://shared.test")
            val save = views.filterIsInstance<Button>().single { it.text == "Save shared server" }
            assertTrue(save.isEnabled)
            save.performClick()
            val settings = PlaybackSettings(context)
            assertEquals("http://shared.test", PlaybackSettings(context).upnpServer)
        }
    }
}
