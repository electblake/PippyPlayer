package app.pippyplayer.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceLifecycleTest {
    @Test fun newerSourceCancelsOlderSourceAndOnlyNewerSourceCommits() = runBlocking {
        val oldResult = CompletableDeferred<List<PlaylistEntry>>()
        val request = SourceRequest { url, _ -> if (url == "old") oldResult.await() else emptyList() }
        val committed = mutableListOf<String>()
        val older = async(start = CoroutineStart.UNDISPATCHED) { request.load("old", "", "old") { committed.add("old") } }
        request.load("new", "", "new") { committed.add("new") }
        oldResult.complete(emptyList())
        older.join()
        assertTrue(older.isCancelled)
        assertEquals(listOf("new"), committed)
        assertEquals("new", request.requestId)
        assertFalse(request.loading)
        assertNull(request.error)
    }

    @Test fun cancelIsPerPlayerAndCannotBeUndoneByALateResult() = runBlocking {
        val firstGate = CompletableDeferred<List<PlaylistEntry>>()
        val secondGate = CompletableDeferred<List<PlaylistEntry>>()
        val first = SourceRequest { _, _ -> firstGate.await() }
        val second = SourceRequest { _, _ -> secondGate.await() }
        val committed = mutableListOf<String>()
        val one = async(start = CoroutineStart.UNDISPATCHED) { first.load("one", "", "one") { committed.add("one") } }
        val two = async(start = CoroutineStart.UNDISPATCHED) { second.load("two", "", "two") { committed.add("two") } }
        first.cancel()
        assertFalse(first.loading)
        assertTrue(second.loading)
        firstGate.complete(emptyList())
        secondGate.complete(emptyList())
        one.join()
        two.await()
        assertTrue(one.isCancelled)
        assertEquals(listOf("two"), committed)
        assertNull(first.error)
        assertNull(second.error)
    }
}
