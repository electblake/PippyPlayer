package app.pippyplayer.tv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlayerService : Service() {
    private lateinit var shared: PlaybackSettings
    private var stateRevision = 0L
    private val players = linkedMapOf<String, PlayerRuntime>()
    private fun addPlayer(id: String): PlayerRuntime = PlayerRuntime(this, id, shared).also { players[id] = it }
    private val server = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
        routing {
            route("/${PlayerState.token}") {
                get {
                    call.respondText(assets.open("controller.html").bufferedReader().use { it.readText() }, ContentType.Text.Html)
                }
                get("/state") {
                    val state = withContext(Dispatchers.Main) {
                        JSONObject().put("revision", ++stateRevision).put("players", JSONArray(players.values.map { it.snapshot() }))
                            .put("recentUrls", JSONArray(shared.recentUrls))
                            .put("recentUrlNames", shared.recentUrlNames)
                            .put("debugEnabled", PlayerState.debugEnabled).toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/players") {
                    val state = withContext(Dispatchers.Main) {
                        val id = shared.addPlayer()
                        addPlayer(id).snapshot().toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/recent-url-name") {
                    val fields = call.receiveParameters()
                    val url = fields.getAll("url")!!.single()
                    val name = fields.getAll("name")!!.single()
                    withContext(Dispatchers.Main) { shared.nameRecentUrl(url, name) }
                    call.respondText("OK")
                }
                post("/debug") {
                    val fields = call.receiveParameters()
                    val enabled = fields.getAll("enabled")!!.single().toBooleanStrict()
                    withContext(Dispatchers.Main) { setDebugEnabled(enabled) }
                    call.respondText("OK")
                }
                route("/players/{playerId}") {
                    delete {
                        val id = call.parameters["playerId"]!!
                        withContext(Dispatchers.Main) {
                            players.getValue(id).release()
                            players.remove(id)
                            shared.removePlayer(id)
                        }
                        call.respondText("OK")
                    }
                    post("/config") {
                        val fields = call.receiveParameters()
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).configure(
                                position = fields["position"]?.let(Position::valueOf),
                                size = fields["size"]?.toInt(), zoom = fields["zoom"]?.toFloat(),
                                volume = fields["volume"]?.toInt())
                        }
                        call.respondText("OK")
                    }
                    post("/source") {
                        val fields = call.receiveParameters()
                        val url = fields.getAll("url")!!.single()
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).loadSource(url, fields.getAll("requestId")!!.single())
                        }
                        call.respondText("OK")
                    }
                    post("/sorting") {
                        val fields = call.receiveParameters()
                        val sorting = PlaylistSort.valueOf(fields.getAll("sorting")!!.single())
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).sort(sorting) }
                        call.respondText("OK")
                    }
                    post("/next") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).next() }
                        call.respondText("OK")
                    }
                    post("/previous") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).previous() }
                        call.respondText("OK")
                    }
                    post("/play") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).resume() }
                        call.respondText("OK")
                    }
                    post("/pause") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).pause() }
                        call.respondText("OK")
                    }
                    post("/stop") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).stop() }
                        call.respondText("OK")
                    }
                    post("/skip-backward") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).skip(-10_000L) }
                        call.respondText("OK")
                    }
                    post("/skip-forward") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).skip(10_000L) }
                        call.respondText("OK")
                    }
                    post("/toggle-visibility") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).toggleVisibility() }
                        call.respondText("OK")
                    }
                    post("/toggle-repeat") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).toggleRepeat() }
                        call.respondText("OK")
                    }

                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        shared = PlaybackSettings(this)
        PlayerState.token = UUID.randomUUID().toString()
        PlayerState.debugEnabled = shared.debugEnabled
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("playback", "PippyPlayer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "playback").setContentTitle("PippyPlayer")
            .setContentText("Local controller is running").setSmallIcon(R.drawable.icon)
            .setContentIntent(open).setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        shared.playerIds.forEach { addPlayer(it) }
        server.start(wait = false)
        PlayerState.controllerPort = runBlocking { server.engine.resolvedConnectors().single().port }
        PlayerState.running = true
    }
    private fun setDebugEnabled(enabled: Boolean) {
        PlayerState.debugEnabled = enabled
        shared.debugEnabled = enabled
        players.values.forEach { it.setDebugEnabled(enabled) }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra("debugEnabled")) setDebugEnabled(intent.getBooleanExtra("debugEnabled", false))
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        server.stop(0, 0)
        players.values.forEach { it.release() }
        players.clear()
        PlayerState.running = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder? = null

}
