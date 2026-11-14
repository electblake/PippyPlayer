package app.pippyplayer.tv

/** Retains the original source failure for the controller while the request still fails normally. */
class SourceRequest {
    @Volatile var error: String? = null
        private set

    fun load(url: String, upnpServer: String): List<PlaylistEntry> {
        error = null
        try {
            return MediaSource.load(url, upnpServer)
        } catch (failure: Exception) {
            error = failure.stackTraceToString()
            throw failure
        }
    }
}
