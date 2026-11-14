package app.pippyplayer.tv

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CrashActivityTest {
    @Test fun updatesButtonOpensUpdaterWithoutPlaybackAndKeepsReport() {
        val app = RuntimeEnvironment.getApplication()
        val report = File(app.filesDir, PippyPlayerApplication.CRASH_REPORT)
        report.writeText("Test startup crash")
        val controller = Robolectric.buildActivity(CrashActivity::class.java).create()
        val activity = controller.get()
        val buttons = mutableListOf<Button>()
        fun visit(view: View) {
            if (view is Button) buttons.add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(activity.findViewById(android.R.id.content))
        buttons.single { it.text == activity.getString(R.string.updates_title) }.performClick()
        assertEquals(UpdateActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        assertNull(shadowOf(app).nextStartedService)
        assertEquals("Test startup crash", report.readText())
        controller.destroy()
        report.delete()
    }
}
