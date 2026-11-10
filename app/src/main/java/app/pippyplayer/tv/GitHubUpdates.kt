package app.pippyplayer.tv

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

internal data class AppRelease(val tag: String, val notes: String, val apkUrl: String, val size: Long)

internal object GitHubUpdates {
    fun latest(): AppRelease {
        val connection = URI("https://api.github.com/repos/electblake/PippyPlayer/releases/latest")
            .toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        connection.setRequestProperty("User-Agent", "PippyPlayer")
        val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        connection.disconnect()
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).map(assets::getJSONObject)
            .single { it.getString("name") == "pippyplayer.apk" }
        return AppRelease(release.getString("tag_name"), release.getString("body"),
            apk.getString("browser_download_url"), apk.getLong("size"))
    }

    suspend fun download(release: AppRelease, destination: File, progress: suspend (Int) -> Unit) {
        val connection = URI(release.apkUrl).toURL().openConnection() as HttpURLConnection
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var reportedPercent = -1
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                    total += count
                    val percent = (total * 100 / release.size).toInt()
                    if (percent != reportedPercent) {
                        progress(percent)
                        reportedPercent = percent
                    }
                }
            }
        }
        connection.disconnect()
    }
}
