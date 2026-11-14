package app.pippyplayer.tv

import android.view.View
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

internal class PlayerLoadingListener(
    private val player: Player,
    private val view: View,
    private val label: TextView
) : Player.Listener {
    private var hasPlayed = false
    val loading get() = PlaybackLoading.from(
        player.playbackState == Player.STATE_BUFFERING && !player.isPlaying,
        player.playWhenReady, hasPlayed)

    fun update() {
        val state = loading
        view.visibility = if (state.visible) View.VISIBLE else View.GONE
        label.text = state.label
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        hasPlayed = player.isPlaying
        update()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            hasPlayed = true
            view.visibility = View.GONE
        } else update()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING) update() else view.visibility = View.GONE
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { update() }
}
