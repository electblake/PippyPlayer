package app.pippyplayer.tv

import android.app.Application
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticLogTest {
    @Test fun remoteTailIncludesRotationAndBoundsLinesAndBytes() {
        val context = RuntimeEnvironment.getApplication()
        val previous = java.io.File(context.filesDir, "diagnostic-previous.txt")
        val current = java.io.File(context.filesDir, "diagnostic-current.txt")
        DiagnosticLog.read(context)
        previous.writeText("previous event\n")
        current.writeText("current event\n")
        assertEquals("previous event\ncurrent event\n", DiagnosticLog.tail(context))
        current.writeText((1..300).joinToString("") { "event $it\n" })
        val tail = DiagnosticLog.tail(context)
        assertTrue(tail.startsWith("event 101\n"))
        assertTrue(tail.endsWith("event 300\n"))
        current.writeText("x".repeat(100_000) + "\nlatest\n")
        assertEquals("latest\n", DiagnosticLog.tail(context))
        DiagnosticLog.write(context, "queued event")
        assertTrue(DiagnosticLog.tail(context).contains("queued event"))
    }

    @Test fun restartKeepsLastOperationVisibleOnTv() {
        val context = RuntimeEnvironment.getApplication()
        DiagnosticLog.write(context, "APP START old run")
        DiagnosticLog.write(context, "player=1 WINDOW begin position=TOP_LEFT")
        DiagnosticLog.write(context, "APP START new run")
        DiagnosticLog.write(context, "SERVICE START")
        val activity = Robolectric.buildActivity(DiagnosticActivity::class.java).setup()
        fun texts(view: View): List<String> = when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }
        val report = texts(activity.get().window.decorView).single { it.contains("WINDOW begin") }
        assertTrue(report.contains("APP START old run"))
        assertFalse(report.contains("new run"))
        assertFalse(report.contains("SERVICE START"))
        activity.destroy()
        assertTrue(DiagnosticLog.read(context).contains("WINDOW begin"))
    }

    @Test fun rotationRetainsPreviousEventsAndBoundsStorage() {
        val context = RuntimeEnvironment.getApplication()
        DiagnosticLog.write(context, "previous " + "x".repeat(512 * 1024))
        DiagnosticLog.write(context, "current")
        val history = DiagnosticLog.read(context)
        assertTrue(history.contains("previous"))
        assertTrue(history.contains("current"))
        DiagnosticLog.write(context, "y".repeat(512 * 1024))
        DiagnosticLog.write(context, "newest")
        assertFalse(DiagnosticLog.read(context).contains("previous"))
        assertTrue(DiagnosticLog.read(context).contains("newest"))
    }
}
