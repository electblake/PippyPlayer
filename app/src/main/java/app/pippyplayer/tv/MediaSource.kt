package app.pippyplayer.tv

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

object MediaSource {
    fun load(url: String): List<PlaylistEntry> {
        val uri = URI(url)
        return when (uri.scheme.lowercase()) {
            "upnp" -> DlnaPlaylist().load(url)
            "http", "https" -> {
                val connection = uri.toURL().openConnection() as HttpURLConnection
                val entries = connection.inputStream.buffered().use { input ->
                    input.mark(256)
                    val bytes = ByteArrayOutputStream()
                    while (bytes.size() < 256) {
                        val byte = input.read()
                        if (byte == -1) break
                        bytes.write(byte)
                    }
                    input.reset()
                    val source = connection.url.toURI()
                    if (isPlaylist(source, connection.contentType, bytes.toString("UTF-8"))) {
                        VideoPlaylist.parse(input.reader(Charsets.UTF_8).readText(), source)
                    } else {
                        listOf(PlaylistEntry(url, null))
                    }
                }
                connection.disconnect()
                entries
            }
            else -> listOf(PlaylistEntry(url, null))
        }
    }

    internal fun isPlaylist(uri: URI, contentType: String?, prefix: String): Boolean {
        val path = uri.path.lowercase()
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
        val text = prefix.removePrefix("\uFEFF").trimStart()
        return path.endsWith(".m3u") || path.endsWith(".m3u8") ||
            mime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "application/mpegurl", "audio/mpegurl", "audio/x-mpegurl") ||
            text.startsWith("#EXTM3U") || text.startsWith("http://") || text.startsWith("https://") || text.startsWith("rtsp://")
    }
}
