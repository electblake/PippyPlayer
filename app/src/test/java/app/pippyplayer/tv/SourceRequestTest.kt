package app.pippyplayer.tv

import java.io.IOException
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

class SourceRequestTest {
    @Test fun actualHttpFailureIsRethrownAndRetainedUntilAnotherSourceAttempt() {
        ServerSocket(0).use { server ->
            val responder = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (input.readLine().isNotEmpty()) { }
                    socket.getOutputStream().write(
                        "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            val request = SourceRequest()
            val failure = assertThrows(IOException::class.java) {
                request.load("http://127.0.0.1:${server.localPort}/video", "")
            }
            responder.join()
            assertEquals(failure.stackTraceToString(), request.error)
            assertTrue(request.error!!.contains("500"))
            assertNull(SourceRequest().error)
            request.load("rtsp://media.test/video", "")
            assertNull(request.error)
        }
    }
}
