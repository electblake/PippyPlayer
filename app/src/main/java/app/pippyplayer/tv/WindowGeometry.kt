package app.pippyplayer.tv

import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt

object WindowGeometry {
    fun percentPixels(percent: Int, length: Int): Int = (length * (percent / 100.0)).roundToInt()

    fun dimensions(screenWidth: Int, screenHeight: Int, size: Int, aspect: Float): Pair<Int, Int> {
        val scale = size / 100f
        val width = min(screenWidth * scale, screenHeight * scale * aspect)
        return width.roundToInt() to (width / aspect).roundToInt()
    }

    fun crop(width: Int, height: Int, top: Int, right: Int, bottom: Int, left: Int): Rect {
        val x = -percentPixels(left, width)
        val y = -percentPixels(top, height)
        // Opposite edges can meet at -50%; retain one pixel for a nonempty window.
        return Rect(
            x, y,
            maxOf(width + percentPixels(right, width), x + minOf(1, width)),
            maxOf(height + percentPixels(bottom, height), y + minOf(1, height)),
        )
    }
}
