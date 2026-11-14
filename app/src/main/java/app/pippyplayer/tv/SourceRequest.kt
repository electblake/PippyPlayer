package app.pippyplayer.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible

/** One source operation per player. All state and commit callbacks belong to the main dispatcher. */
class SourceRequest(
    private val fetch: suspend (String, String) -> List<PlaylistEntry> = { url, server ->
        runInterruptible(Dispatchers.IO) { MediaSource.load(url, server) }
    }
) {
    private var active: Job? = null
    var revision = 0L
        private set
    var requestId = ""
        private set
    var loading = false
        private set
    var error: String? = null
        private set

    suspend fun load(url: String, server: String, requestId: String = "", commit: (List<PlaylistEntry>) -> Unit = {}): List<PlaylistEntry> = coroutineScope {
        active?.cancel()
        val operation = currentCoroutineContext().job
        active = operation
        revision++
        this@SourceRequest.requestId = requestId
        loading = true
        error = null
        try {
            val entries = fetch(url, server)
            currentCoroutineContext().ensureActive()
            commit(entries)
            entries
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (active === operation) error = failure.stackTraceToString()
            throw failure
        } finally {
            if (active === operation) {
                active = null
                loading = false
            }
        }
    }

    fun cancel() {
        active?.cancel()
        active = null
        revision++
        requestId = ""
        loading = false
        error = null
    }
}
