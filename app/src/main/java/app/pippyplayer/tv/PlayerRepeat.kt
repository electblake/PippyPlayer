package app.pippyplayer.tv

import androidx.media3.common.Player

internal fun Player.toggleRepeatOne() {
    repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
}
