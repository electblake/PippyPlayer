package app.pippyplayer.tv

import java.net.URI

data class PlaylistEntry(val uri: String, val title: String?)

object VideoPlaylist {
    fun parse(text: String, source: URI): List<PlaylistEntry> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).toList()
        // HLS manifests describe one stream, not a queue of their media segments.
        if (lines.any { it.startsWith("#EXT-X-") }) {
            return listOf(PlaylistEntry(source.toString(), null))
        }
        var title: String? = null
        return buildList {
            for (line in lines) {
                when {
                    line.startsWith("#EXTINF:") -> title = line.substringAfter(',')
                    line.isNotEmpty() && !line.startsWith('#') -> {
                        add(PlaylistEntry(source.resolve(line).toString(), title))
                        title = null
                    }
                }
            }
        }
    }
}

class PlaybackQueue {
    var entries: List<PlaylistEntry> = emptyList()
        private set
    var index = 0
        private set
    val current get() = entries[index]
    val hasPrevious get() = index > 0
    val hasNext get() = index + 1 < entries.size

    fun load(items: List<PlaylistEntry>) { entries = items; index = 0 }
    fun next() { index++ }
    fun previous() { index-- }
    fun clear() { entries = emptyList(); index = 0 }
}
