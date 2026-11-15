package app.pippyplayer.tv

import android.content.Context
import android.os.SystemClock
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors

/** Ordered, bounded event history. File I/O never blocks a playback callback. */
internal object DiagnosticLog {
    private const val CURRENT = "diagnostic-current.txt"
    private const val PREVIOUS = "diagnostic-previous.txt"
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "PippyDiagnostics").apply { isDaemon = true }
    }

    fun write(context: Context, event: String) {
        val directory = context.filesDir
        val entry = "${Instant.now()} +${SystemClock.elapsedRealtime()} $event\n".toByteArray(Charsets.UTF_8)
        writer.execute {
            val file = File(directory, CURRENT)
            if (file.length() >= 512 * 1024) {
                Files.move(file.toPath(), File(directory, PREVIOUS).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            FileOutputStream(file, true).use { it.write(entry) }
        }
    }

    // A requested report waits behind pending writes to preserve event order.
    fun read(context: Context): String = writer.submit<String> {
        context.filesDir.listFiles()!!.filter { it.name == CURRENT || it.name == PREVIOUS }
            .sortedBy { if (it.name == PREVIOUS) 0 else 1 }.joinToString("") { it.readText() }
    }.get()
}
