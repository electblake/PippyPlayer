package app.pippyplayer.tv

import kotlin.math.min
import kotlin.math.roundToInt

object WindowGeometry {
    fun dimensions(screenWidth: Int, screenHeight: Int, size: Int, aspect: Float): Pair<Int, Int> {
        val scale = size / 100f
        val width = min(screenWidth * scale, screenHeight * scale * aspect)
        return width.roundToInt() to (width / aspect).roundToInt()
    }
}
