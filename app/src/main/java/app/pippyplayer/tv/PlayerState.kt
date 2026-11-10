package app.pippyplayer.tv

import android.view.Gravity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class Position(val gravity: Int) {
    TOP_LEFT(Gravity.TOP or Gravity.LEFT), TOP_CENTER(Gravity.TOP or Gravity.CENTER_HORIZONTAL), TOP_RIGHT(Gravity.TOP or Gravity.RIGHT),
    MIDDLE_LEFT(Gravity.CENTER_VERTICAL or Gravity.LEFT), MIDDLE_CENTER(Gravity.CENTER), MIDDLE_RIGHT(Gravity.CENTER_VERTICAL or Gravity.RIGHT),
    BOTTOM_LEFT(Gravity.BOTTOM or Gravity.LEFT), BOTTOM_CENTER(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL), BOTTOM_RIGHT(Gravity.BOTTOM or Gravity.RIGHT)
}
object PlayerState {
    var running by mutableStateOf(false)
    var token by mutableStateOf(UUID.randomUUID().toString())
    var url = ""
    var size = 30
    var zoom = 1f
    var volume = 50
    var position = Position.BOTTOM_RIGHT
}
