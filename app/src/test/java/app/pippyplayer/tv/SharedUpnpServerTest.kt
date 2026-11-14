package app.pippyplayer.tv

import android.app.Application
import android.content.Context
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SharedUpnpServerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSettings() {
        listOf("playback", "player_1", "player_2", "player_3").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun bothPlayersLoadStashPerformerSourcesThroughTheSameActualSoapEndpoint() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val serverUrl = "http://127.0.0.1:${server.localPort}"
            // Reproduce the upgrade: the shared server exists, but Player 2 has no server setting.
            context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
                .putString("upnpServer", serverUrl).commit()
            val first = PlaybackSettings(context)
            first.addPlayer()
            val second = PlaybackSettings(context)
            val requests = mutableListOf<Pair<String, String>>()
            val responder = thread {
                repeat(4) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val requestLine = reader.readLine()
                        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                        val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }
                            .substringAfter(':').trim().toInt()
                        val body = String(CharArray(length) { reader.read().toChar() })
                        requests.add(requestLine to body)
                        val contents = if (body.contains("<BrowseFlag>BrowseMetadata</BrowseFlag>"))
                            "<container id=\"performers/5649\"/>"
                        else """<item id="video"><dc:title>Test video</dc:title><u:class>object.item.videoItem</u:class><res protocolInfo="http-get:*:video/mp4:*">/video.mp4</res></item>"""
                        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:u="urn:schemas-upnp-org:metadata-1-0/upnp/">$contents</DIDL-Lite>"""
                        val escaped = didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        val response = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><BrowseResponse><Result>$escaped</Result><NumberReturned>1</NumberReturned><TotalMatches>1</TotalMatches></BrowseResponse></s:Body></s:Envelope>""".toByteArray()
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n").toByteArray() + response)
                    }
                }
            }
            for (settings in listOf(first, second)) {
                val request = SourceRequest()
                val entries = kotlinx.coroutines.runBlocking { request.load("https://stash.test/performers/5649/scenes", settings.upnpServer) }
                assertEquals(listOf("$serverUrl/video.mp4"), entries.map { it.uri })
                assertNull(request.error)
            }
            responder.join()
            assertEquals(4, requests.size)
            assertTrue(requests.all { it.first == "POST /ctl HTTP/1.1" })
            assertTrue(requests.all { it.second.contains("<ObjectID>performers/5649</ObjectID>") })
        }
    }

    @Test fun serverConfiguredDuring0121BecomesSharedAndSurvivesDeletingAllPlayers() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("players", "[\"1\",\"2\"]").putInt("nextPlayerId", 3)
            .putBoolean("player1SettingsMigrated", true).putString("upnpServer", "http://old.test").commit()
        context.getSharedPreferences("player_1", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://working.test:9999").commit()
        val second = PlaybackSettings(context)
        assertEquals("http://working.test:9999", second.upnpServer)
        assertEquals(second.upnpServer, PlaybackSettings(context).upnpServer)
        second.removePlayer("1")
        second.removePlayer("2")
        val third = second.player(second.addPlayer())
        assertEquals("http://working.test:9999", second.upnpServer)
        second.upnpServer = "http://changed.test"
        assertEquals("http://changed.test", second.upnpServer)
        third.saveNetworkCacheMs(6000)
        assertEquals("http://changed.test", PlaybackSettings(context).upnpServer)
    }

    @Test fun serverConfiguredOnlyOnRemainingPlayerIsPreserved() {
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
            .putString("players", "[\"2\"]").commit()
        context.getSharedPreferences("player_2", Context.MODE_PRIVATE).edit()
            .putString("upnpServer", "http://remaining.test").commit()
        assertEquals("http://remaining.test", PlaybackSettings(context).upnpServer)
    }
}
