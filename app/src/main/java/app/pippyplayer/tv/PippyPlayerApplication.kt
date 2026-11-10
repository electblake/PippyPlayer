package app.pippyplayer.tv

import android.app.Application
import android.content.Intent
import android.os.Build
import java.io.File
import java.time.Instant

class PippyPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The report viewer runs independently of the process that crashed.
        if (getProcessName() != packageName) return
        val version = packageManager.getPackageInfo(packageName, 0)
        val platformHandler = Thread.getDefaultUncaughtExceptionHandler()!!
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            File(filesDir, CRASH_REPORT).writeText(buildString {
                appendLine("PippyPlayer crash report")
                appendLine("Time: ${Instant.now()}")
                appendLine("App: ${version.versionName} (${version.longVersionCode})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                append(failure.stackTraceToString())
            })
            startActivity(Intent(this, CrashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            platformHandler.uncaughtException(thread, failure)
        }
    }

    companion object {
        const val CRASH_REPORT = "last-crash.txt"
    }
}
