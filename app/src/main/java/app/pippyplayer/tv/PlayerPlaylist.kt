package app.pippyplayer.tv

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder

internal fun PlaybackQueue.mediaItems(): List<MediaItem> {
    val ids = itemIds
    return entries.mapIndexed { index, entry ->
        MediaItem.Builder().setMediaId(ids[index]).setUri(entry.uri).setMimeType(entry.mimeType).build()
    }
}

/** Update traversal once; moving each item creates a full timeline for every move. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun ExoPlayer.applyQueueOrder(ids: List<String>) {
    val indices = (0 until mediaItemCount).associateBy { getMediaItemAt(it).mediaId }
    setShuffleOrder(DefaultShuffleOrder(ids.map { indices.getValue(it) }.toIntArray(), 0L))
    shuffleModeEnabled = true
}

internal val PlaybackQueue.windowStart get() = maxOf(0, index - 1)

/** At most the previous, current and next item enter ExoPlayer's timeline. */
internal fun PlaybackQueue.windowItems(): List<MediaItem> =
    (windowStart until minOf(count, index + 2)).map { position ->
        val entry = entryAt(position)
        MediaItem.Builder().setMediaId(idAt(position)).setUri(entry.uri).setMimeType(entry.mimeType).build()
    }
