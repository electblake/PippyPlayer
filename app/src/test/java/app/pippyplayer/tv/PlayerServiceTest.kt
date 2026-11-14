package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerServiceTest {
    @Test fun actualHttpRoutesKeepPlayerConfigurationAndLifecycleIndependent() {
        val context = RuntimeEnvironment.getApplication()
        listOf("playback", "player_1", "player_2").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val executor = Executors.newSingleThreadExecutor()
        val controller = Robolectric.buildService(PlayerService::class.java).create()
        val base = "http://127.0.0.1:${PlayerState.controllerPort}/${PlayerState.token}"
        fun request(path: String, method: String = "GET", body: String = ""): String {
            val future = executor.submit(Callable {
                val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = method
                if (method == "POST") {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = connection.responseCode
                val result = (if (code < 400) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
                connection.disconnect()
                assertEquals(result, 200, code)
                result
            })
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!future.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            return future.get(1, java.util.concurrent.TimeUnit.SECONDS)
        }
        try {
            assertTrue(request("").contains("playerTemplate"))
            val firstState = JSONObject(request("/state"))
            assertEquals(1, firstState.getJSONArray("players").length())
            val secondId = JSONObject(request("/players", "POST")).getString("id")
            request("/players/1/config", "POST", "size=60&position=TOP_LEFT&volume=23")
            request("/players/$secondId/config", "POST", "volume=81")
            request("/players/1/config", "POST", "zoom=2")
            val state = JSONObject(request("/state"))
            assertTrue(state.getLong("revision") > firstState.getLong("revision"))
            val first = state.getJSONArray("players").getJSONObject(0)
            val second = state.getJSONArray("players").getJSONObject(1)
            assertEquals(60, first.getInt("size"))
            assertEquals(23, first.getInt("volume"))
            assertEquals(2.0, first.getDouble("zoom"), 0.0)
            assertEquals(30, second.getInt("size"))
            assertEquals(81, second.getInt("volume"))
            request("/players/1/play", "POST")
            request("/players/1/skip-forward", "POST")
            assertFalse(JSONObject(request("/state")).getJSONArray("players").getJSONObject(0).getBoolean("visible"))
            java.net.ServerSocket(0).use { sourceServer ->
                sourceServer.soTimeout = 10000
                val responder = kotlin.concurrent.thread {
                    sourceServer.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (reader.readLine().isNotEmpty()) { }
                        val playlist = "#EXTM3U\n".toByteArray()
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: audio/x-mpegurl\r\nContent-Length: ${playlist.size}\r\nConnection: close\r\n\r\n").toByteArray() + playlist)
                    }
                }
                val sourceUrl = "http://127.0.0.1:${sourceServer.localPort}/empty.m3u"
                request("/players/$secondId/source", "POST", "requestId=api-source&url=" + java.net.URLEncoder.encode(sourceUrl, "UTF-8"))
                responder.join()
                val loaded = JSONObject(request("/state")).getJSONArray("players").getJSONObject(1)
                assertEquals("api-source", loaded.getString("sourceRequestId"))
                assertEquals(1L, loaded.getLong("sourceRevision"))
                assertFalse(loaded.getBoolean("sourceLoading"))
                assertFalse(loaded.getBoolean("visible"))
                assertEquals(sourceUrl, loaded.getString("url"))
                request("/players/$secondId/stop", "POST")
            }
            request("/players/1", "DELETE")
            val remaining = JSONObject(request("/state")).getJSONArray("players")
            assertEquals(1, remaining.length())
            assertEquals(secondId, remaining.getJSONObject(0).getString("id"))
            assertEquals(81, remaining.getJSONObject(0).getInt("volume"))
        } finally {
            controller.destroy()
            executor.shutdownNow()
        }
    }
}
