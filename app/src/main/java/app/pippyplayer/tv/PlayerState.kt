package app.pippyplayer.tv

import android.view.Gravity
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class Position(relativeGravity: Int) {
    TOP_LEFT(Gravity.TOP or Gravity.START), TOP_CENTER(Gravity.TOP or Gravity.CENTER_HORIZONTAL), TOP_RIGHT(Gravity.TOP or Gravity.END),
    MIDDLE_LEFT(Gravity.CENTER_VERTICAL or Gravity.START), MIDDLE_CENTER(Gravity.CENTER), MIDDLE_RIGHT(Gravity.CENTER_VERTICAL or Gravity.END),
    BOTTOM_LEFT(Gravity.BOTTOM or Gravity.START), BOTTOM_CENTER(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL), BOTTOM_RIGHT(Gravity.BOTTOM or Gravity.END);

    // The controller grid selects physical screen positions, independent of locale.
    val gravity = Gravity.getAbsoluteGravity(relativeGravity, View.LAYOUT_DIRECTION_LTR)
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
