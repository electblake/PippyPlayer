package app.pippyplayer.tv

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** Bounded, durable event history; each entry reaches storage before the operation continues. */
internal object DiagnosticLog {
    private const val CURRENT = "diagnostic-current.txt"
    private const val PREVIOUS = "diagnostic-previous.txt"

    @Synchronized fun write(context: Context, event: String) {
        val file = File(context.filesDir, CURRENT)
        if (file.length() >= 512 * 1024) {
            Files.move(file.toPath(), File(context.filesDir, PREVIOUS).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        FileOutputStream(file, true).use { output ->
            output.write("${Instant.now()} +${SystemClock.elapsedRealtime()} $event\n".toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    @Synchronized fun read(context: Context): String =
        context.filesDir.listFiles()!!.filter { it.name == CURRENT || it.name == PREVIOUS }
            .sortedBy { if (it.name == PREVIOUS) 0 else 1 }.joinToString("") { it.readText() }
}
