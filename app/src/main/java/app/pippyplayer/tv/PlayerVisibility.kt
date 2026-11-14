package app.pippyplayer.tv

import android.view.View
import androidx.media3.common.Player

internal class PlayerVisibilityListener(private val window: View) : Player.Listener {
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) window.visibility = View.VISIBLE
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) window.visibility = View.VISIBLE
    }
}

internal fun Player.setWindowVisible(window: View, visible: Boolean) {
    if (visible) {
        window.visibility = View.VISIBLE
        play()
    } else {
        pause()
        window.visibility = View.GONE
    }
}
