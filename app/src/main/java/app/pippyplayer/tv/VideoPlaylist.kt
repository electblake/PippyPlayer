package app.pippyplayer.tv

import java.net.URI

data class PlaylistEntry(val uri: String, val title: String?, val durationSeconds: Double? = null, val mimeType: String? = null)

enum class PlaylistSort { DEFAULT, LENGTH_SHORTEST, LENGTH_LONGEST, NAME, RANDOM }

object VideoPlaylist {
    fun parse(text: String, source: URI): List<PlaylistEntry> {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).toList()
        // HLS manifests describe one stream, not a queue of their media segments.
        if (lines.any { it.startsWith("#EXT-X-") }) {
            return listOf(PlaylistEntry(source.toString(), null, mimeType = "application/x-mpegURL"))
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
    private var original: List<IndexedValue<PlaylistEntry>> = emptyList()
    private var ordered: List<IndexedValue<PlaylistEntry>> = emptyList()
    val entries get() = ordered.map { it.value }
    val itemIds get() = ordered.map { it.index.toString() }
    val count get() = ordered.size
    var sorting = PlaylistSort.DEFAULT
        private set
    var index = 0
        private set
    val current get() = ordered[index].value
    val hasPrevious get() = index > 0
    val hasNext get() = index + 1 < ordered.size

    private fun sorted(sort: PlaylistSort) = when (sort) {
        PlaylistSort.RANDOM -> original.shuffled()
        PlaylistSort.DEFAULT -> original
        PlaylistSort.LENGTH_SHORTEST -> original.sortedBy { it.value.durationSeconds!! }
        PlaylistSort.LENGTH_LONGEST -> original.sortedByDescending { it.value.durationSeconds!! }
        PlaylistSort.NAME -> original.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.title!! })
    }

    fun sort(sort: PlaylistSort) {
        val currentId = ordered.getOrNull(index)?.index
        val result = sorted(sort)
        ordered = result
        sorting = sort
        index = if (currentId == null) 0 else ordered.indexOfFirst { it.index == currentId }
    }

    fun load(items: List<PlaylistEntry>) {
        original = items.withIndex().toList()
        ordered = sorted(sorting)
        index = 0
    }
    fun next() { index++ }
    fun previous() { index-- }
    fun select(index: Int) { this.index = index }
    fun clear() { original = emptyList(); ordered = emptyList(); index = 0 }
}
