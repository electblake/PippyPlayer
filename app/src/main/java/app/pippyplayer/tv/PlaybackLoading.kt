package app.pippyplayer.tv

data class PlaybackLoading(val visible: Boolean, val label: String) {
    companion object {
        fun from(buffering: Boolean, playWhenReady: Boolean, hasPlayed: Boolean) = PlaybackLoading(
            visible = buffering && playWhenReady,
            label = if (hasPlayed) "Buffering…" else "Loading…"
        )
    }
}
