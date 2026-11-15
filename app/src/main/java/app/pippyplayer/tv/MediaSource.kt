package app.pippyplayer.tv

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

internal data class LoadedPlaylist(val entries: List<PlaylistEntry>, val cacheable: Boolean)

object MediaSource {
    fun load(inputUrl: String, upnpServer: String): List<PlaylistEntry> {
        return read(resolveUrl(inputUrl, upnpServer)).entries
    }

    internal fun read(url: String): LoadedPlaylist {
        val uri = URI(url)
        return when (uri.scheme.lowercase()) {
            "upnp" -> LoadedPlaylist(DlnaPlaylist().load(url), true)
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
                        val playlist = VideoPlaylist.parse(input.reader(Charsets.UTF_8).readText(), source)
                        LoadedPlaylist(playlist, playlist.none { it.mimeType == "application/x-mpegURL" })
                    } else {
                        LoadedPlaylist(listOf(PlaylistEntry(url, null, mimeType = connection.contentType?.substringBefore(';')?.trim())), false)
                    }
                }
                connection.disconnect()
                entries
            }
            else -> LoadedPlaylist(listOf(PlaylistEntry(url, null)), false)
        }
    }

    internal fun resolveUrl(url: String, upnpServer: String): String {
        val source = URI(url)
        if (source.scheme != null && source.scheme.lowercase() !in setOf("http", "https")) return url
        val path = source.path.trim('/')
        if (!Regex("(?:scenes/[0-9]+|(?:performers|studios|tags|movies|groups)/[0-9]+(?:/scenes)?)").matches(path)) return url
        val objectId = path.removeSuffix("/scenes")
        val server = URI(upnpServer)
        return URI("upnp", server.userInfo, server.host, server.port, "/ctl", "ObjectID=$objectId", null).toString()
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
