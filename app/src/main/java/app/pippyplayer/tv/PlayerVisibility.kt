package app.pippyplayer.tv

import android.view.View
import androidx.media3.common.Player

internal fun Player.setWindowVisible(window: View, visible: Boolean) {
    if (visible) {
        window.visibility = View.VISIBLE
        play()
    } else {
        pause()
        window.visibility = View.GONE
    }
}
