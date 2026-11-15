package app.pippyplayer.tv

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class)
class DeviceInfoTest {
    @Test fun deviceReportWorksOnMinimumAndCurrentSdkWithoutCreatingADecoder() {
        val report = DeviceInfo.report(RuntimeEnvironment.getApplication())
        assertTrue(report.contains("CPU cores available to the app: ${Runtime.getRuntime().availableProcessors()}"))
        assertTrue(report.contains("Hardware video decoder entries:"))
        assertTrue(report.contains("upper-bound hints, not a guaranteed player count"))
        assertTrue(report.contains("excludes decoder/GPU memory"))
    }
}
