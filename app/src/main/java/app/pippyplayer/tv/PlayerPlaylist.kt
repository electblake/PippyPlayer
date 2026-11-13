package app.pippyplayer.tv

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal fun PlaybackQueue.mediaItems(): List<MediaItem> {
    val ids = itemIds
    return entries.mapIndexed { index, entry ->
        MediaItem.Builder().setMediaId(ids[index]).setUri(entry.uri).setMimeType(entry.mimeType).build()
    }
}

/** Move the existing periods instead of replacing them, preserving current playback. */
internal fun Player.applyQueueOrder(ids: List<String>) {
    val currentIds = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }.toMutableList()
    ids.forEachIndexed { target, id ->
        val source = currentIds.indexOf(id)
        if (source != target) {
            moveMediaItem(source, target)
            currentIds.add(target, currentIds.removeAt(source))
        }
    }
}
