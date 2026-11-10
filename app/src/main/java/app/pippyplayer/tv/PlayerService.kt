package app.pippyplayer.tv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import androidx.core.net.toUri
import android.view.TextureView
import android.widget.FrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import android.view.View
import android.view.WindowManager
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import android.os.Handler
import android.os.Looper
import java.util.UUID

class PlayerService : Service() {
    private lateinit var engine: LibVLC
    private lateinit var player: MediaPlayer
    private var media: Media? = null
    private val queue = PlaybackQueue()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var texture: TextureView
    private lateinit var window: FrameLayout
    private lateinit var manager: WindowManager
    private lateinit var layout: WindowManager.LayoutParams
    private var aspect = 16f / 9f
    private val server = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
        routing {
            route("/${PlayerState.token}") {
                get {
                    call.respondText(assets.open("controller.html").bufferedReader().use { it.readText() }, ContentType.Text.Html)
                }
                get("/state") {
                    val state = withContext(Dispatchers.Main) {
                        JSONObject().put("url", PlayerState.url).put("size", PlayerState.size)
                            .put("zoom", PlayerState.zoom).put("volume", PlayerState.volume)
                            .put("queue", JSONArray(queue.entries.map { JSONObject().put("url", it.uri).put("title", it.title) }))
                            .put("queueIndex", queue.index)
                            .put("sorting", queue.sorting.name)
                            .put("hasPrevious", queue.hasPrevious).put("hasNext", queue.hasNext)
                            .put("position", PlayerState.position.name).put("playing", player.isPlaying)
                            .put("playbackState", player.playerState)
                            .put("error", if (player.playerState == IMedia.State.Error) "LibVLC playback error" else JSONObject.NULL)
                            .put("width", layout.width).put("height", layout.height)
                            .put("displayedFrames", media?.stats?.displayedPictures ?: JSONObject.NULL)
                            .put("decodedFrames", media?.stats?.decodedVideo ?: JSONObject.NULL)
                            .put("elapsed", player.time).toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/config") {
                    val fields = call.receiveParameters()
                    val position = Position.valueOf(fields.getAll("position")!!.single())
                    val size = fields.getAll("size")!!.single().toInt()
                    val zoom = fields.getAll("zoom")!!.single().toFloat()
                    val volume = fields.getAll("volume")!!.single().toInt()
                    withContext(Dispatchers.Main) {
                        PlayerState.position = position
                        PlayerState.size = size
                        PlayerState.zoom = zoom
                        PlayerState.volume = volume
                        player.volume = volume
                        updateWindow()
                    }
                    call.respondText("OK")
                }
                post("/source") {
                    val fields = call.receiveParameters()
                    val url = fields.getAll("url")!!.single()
                    val entries = withContext(Dispatchers.IO) { MediaSource.load(url) }
                    withContext(Dispatchers.Main) {
                        PlayerState.url = url
                        queue.load(entries)
                        playCurrent()
                    }
                    call.respondText("OK")
                }
                post("/sorting") {
                    val fields = call.receiveParameters()
                    val sorting = PlaylistSort.valueOf(fields.getAll("sorting")!!.single())
                    withContext(Dispatchers.Main) {
                        queue.sort(sorting)
                        getSharedPreferences("playback", MODE_PRIVATE).edit()
                            .putString("sorting", sorting.name).apply()
                    }
                    call.respondText("OK")
                }
                post("/next") {
                    withContext(Dispatchers.Main) {
                        if (queue.hasNext) { queue.next(); playCurrent() }
                    }
                    call.respondText("OK")
                }
                post("/previous") {
                    withContext(Dispatchers.Main) {
                        if (queue.hasPrevious) { queue.previous(); playCurrent() }
                    }
                    call.respondText("OK")
                }
                post("/play") {
                    withContext(Dispatchers.Main) {
                        if (player.playerState == IMedia.State.Ended) player.stop()
                        player.play()
                    }
                    call.respondText("OK")
                }
                post("/pause") { withContext(Dispatchers.Main) { player.pause() }; call.respondText("OK") }
                post("/stop") {
                    withContext(Dispatchers.Main) {
                        player.stop()
                        player.media = null
                        media?.release()
                        media = null
                        queue.clear()
                        PlayerState.url = ""
                        window.visibility = View.GONE
                    }
                    call.respondText("OK")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        queue.sort(PlaylistSort.valueOf(getSharedPreferences("playback", MODE_PRIVATE)
            .getString("sorting", PlaylistSort.DEFAULT.name)!!))
        PlayerState.token = UUID.randomUUID().toString()
        PlayerState.url = ""
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("playback", "PippyPlayer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "playback").setContentTitle("PippyPlayer")
            .setContentText("Local controller is running").setSmallIcon(R.drawable.icon)
            .setContentIntent(open).setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        engine = LibVLC(this, arrayListOf("--no-video-title-show", "--no-audio-time-stretch", "--vout=android-display"))
        player = MediaPlayer(engine)
        player.volume = PlayerState.volume
        texture = TextureView(this)
        window = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            addView(texture, FrameLayout.LayoutParams(-1, -1))
            visibility = View.GONE
        }
        manager = getSystemService(WindowManager::class.java)
        layout = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE)
        setLayout()
        manager.addView(window, layout)
        player.vlcVout.setVideoView(texture)
        player.vlcVout.attachViews { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
            // Zero dimensions signal surface reset; resize on the next video layout.
            if (width > 0 && height > 0) {
                aspect = visibleWidth.toFloat() * sarNum / (visibleHeight * sarDen)
                updateWindow()
            }
        }
        player.setEventListener { event ->
            if (event.type == MediaPlayer.Event.EndReached) {
                main.post {
                    if (player.playerState == IMedia.State.Ended && queue.hasNext) {
                        queue.next()
                        playCurrent()
                    }
                }
            }
        }
        server.start(wait = false)
        PlayerState.controllerPort = runBlocking { server.engine.resolvedConnectors().single().port }
        PlayerState.running = true
    }

    private fun playCurrent() {
        val item = queue.current
        window.visibility = View.VISIBLE
        player.stop()
        media?.release()
        media = Media(engine, item.uri.toUri()).apply { setHWDecoderEnabled(false, false) }
        player.media = media
        player.play()
    }

    private fun setLayout() {
        val metrics = resources.displayMetrics
        val dimensions = WindowGeometry.dimensions(metrics.widthPixels, metrics.heightPixels, PlayerState.size, aspect)
        layout.width = dimensions.first
        layout.height = dimensions.second
        layout.gravity = PlayerState.position.gravity
        texture.scaleX = PlayerState.zoom
        texture.scaleY = PlayerState.zoom
    }
    private fun updateWindow() {
        setLayout()
        manager.updateViewLayout(window, layout)
        player.vlcVout.setWindowSize(layout.width, layout.height)
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        server.stop(0, 0)
        player.stop()
        player.vlcVout.detachViews()
        manager.removeView(window)
        player.release()
        media?.release()
        engine.release()
        PlayerState.running = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder? = null
}
