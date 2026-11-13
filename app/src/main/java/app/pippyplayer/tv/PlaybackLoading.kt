package app.pippyplayer.tv

/** Buffering events are independent of VLC's playing/paused state. */
class PlaybackLoading {
    private var opening = false
    private var paused = false
    var percent: Float? = null
        private set

    val visible: Boolean get() = !paused && (opening || (percent != null && percent!! < 100f))
    val label: String get() = if (percent != null && percent!! < 100f)
        "Buffering… ${percent!!.toInt()}%" else "Loading…"

    fun start() {
        opening = true
        paused = false
        percent = null
    }

    fun buffering(value: Float) {
        opening = false
        percent = value
    }

    fun playing() {
        opening = false
        paused = false
    }

    fun pause() { paused = true }

    fun clear() {
        opening = false
        paused = false
        percent = null
    }
}
