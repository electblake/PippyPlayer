package app.pippyplayer.tv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import androidx.core.net.toUri
import android.view.SurfaceView
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import android.os.SystemClock
import android.widget.TextView
import java.util.Locale
import java.util.UUID

class PlayerService : Service() {
    private lateinit var engine: LibVLC
    private lateinit var player: MediaPlayer
    private var media: Media? = null
    private val queue = PlaybackQueue()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var videoSurface: SurfaceView
    private lateinit var window: FrameLayout
    private lateinit var manager: WindowManager
    private lateinit var layout: WindowManager.LayoutParams
    private var aspect = 16f / 9f
    private lateinit var debugView: TextView
    private var previousSample: PlaybackSample? = null
    private val loading = PlaybackLoading()
    private lateinit var loadingView: LinearLayout
    private lateinit var loadingLabel: TextView
    private var debugText = "No media loaded"
    private var activeNetworkCacheMs: Int? = null
    private val debugTick = object : Runnable {
        override fun run() {
            updateDebugInfo()
            main.postDelayed(this, 1000)
        }
    }
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
                            .put("loading", loading.visible).put("loadingText", loading.label)
                            .put("bufferingPercent", loading.percent ?: JSONObject.NULL)
                            .put("seekable", player.isSeekable)
                            .put("error", if (player.playerState == IMedia.State.Error) "LibVLC playback error" else JSONObject.NULL)
                            .put("width", layout.width).put("height", layout.height)
                            .put("debugEnabled", PlayerState.debugEnabled).put("debugText", debugText)
                            .put("elapsed", player.time).toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/debug") {
                    val fields = call.receiveParameters()
                    val enabled = fields.getAll("enabled")!!.single().toBooleanStrict()
                    withContext(Dispatchers.Main) { setDebugEnabled(enabled) }
                    call.respondText("OK")
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
                    val settings = PlaybackSettings(this@PlayerService)
                    val entries = withContext(Dispatchers.IO) { MediaSource.load(url, settings.upnpServer) }
                    withContext(Dispatchers.Main) {
                        PlayerState.url = url
                        queue.load(entries)
                        playCurrent()
                        settings.lastPlayedUrl = url
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
                        if (player.playerState == IMedia.State.Ended) {
                            player.stop()
                            resetDebugInfo()
                        }
                        player.play()
                    }
                    call.respondText("OK")
                }
                post("/skip-backward") {
                    withContext(Dispatchers.Main) {
                        player.setTime((player.time - 10_000L).coerceIn(0L, player.length))
                    }
                    call.respondText("OK")
                }
                post("/skip-forward") {
                    withContext(Dispatchers.Main) {
                        player.setTime((player.time + 10_000L).coerceIn(0L, player.length))
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
                        resetDebugInfo()
                        loading.clear()
                        updateLoadingView()
                        queue.clear()
                        player.vlcVout.detachViews()
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
        PlayerState.url = PlaybackSettings(this).lastPlayedUrl
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("playback", "PippyPlayer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "playback").setContentTitle("PippyPlayer")
            .setContentText("Local controller is running").setSmallIcon(R.drawable.icon)
            .setContentIntent(open).setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        engine = LibVLC(this, arrayListOf("--no-video-title-show", "--no-audio-time-stretch", "--vout=android-display"))
        player = MediaPlayer(engine)
        player.volume = PlayerState.volume
        videoSurface = SurfaceView(this)
        debugView = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xCC101820.toInt())
            textSize = 11f
            setPadding(8, 4, 8, 4)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        loadingLabel = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xDD101820.toInt())
            addView(ProgressBar(this@PlayerService), LinearLayout.LayoutParams(
                (28 * resources.displayMetrics.density).toInt(),
                (28 * resources.displayMetrics.density).toInt()))
            addView(loadingLabel)
            visibility = View.GONE
        }
        window = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            addView(videoSurface, FrameLayout.LayoutParams(-1, -1))
            addView(debugView, FrameLayout.LayoutParams(-1, -2))
            addView(loadingView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            visibility = View.GONE
        }
        manager = getSystemService(WindowManager::class.java)
        layout = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE)
        setLayout()
        manager.addView(window, layout)
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> loading.start()
                MediaPlayer.Event.Buffering -> loading.buffering(event.buffering)
                MediaPlayer.Event.Playing -> {
                    player.volume = PlayerState.volume
                    loading.playing()
                }
                MediaPlayer.Event.Paused -> loading.pause()
                MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached,
                MediaPlayer.Event.EncounteredError -> loading.clear()
            }
            updateLoadingView()
            if (event.type == MediaPlayer.Event.EndReached) {
                main.post {
                    if (player.playerState == IMedia.State.Ended && queue.hasNext) {
                        queue.next()
                        playCurrent()
                    }
                }
            }
        }
        setDebugEnabled(getSharedPreferences("playback", MODE_PRIVATE).getBoolean("debugEnabled", false))
        server.start(wait = false)
        PlayerState.controllerPort = runBlocking { server.engine.resolvedConnectors().single().port }
        PlayerState.running = true
    }

    private fun attachVideoSurface() {
        player.vlcVout.setVideoView(videoSurface)
        player.vlcVout.attachViews { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
            // Zero dimensions signal surface reset; resize on the next video layout.
            if (width > 0 && height > 0) {
                aspect = visibleWidth.toFloat() * sarNum / (visibleHeight * sarDen)
                updateWindow()
            }
        }
    }

    private fun playCurrent() {
        val item = queue.current
        player.stop()
        player.vlcVout.detachViews()
        attachVideoSurface()
        window.visibility = View.VISIBLE
        media?.release()
        activeNetworkCacheMs = PlaybackSettings(this).networkCacheMs
        media = Media(engine, item.uri.toUri()).apply {
            addOption(":network-caching=$activeNetworkCacheMs")
            setHWDecoderEnabled(true, false)
            addOption(":mediacodec-dr")
        }
        resetDebugInfo()
        player.media = media
        loading.start()
        updateLoadingView()
        player.play()
    }

    private fun updateLoadingView() {
        val visibility = if (loading.visible) View.VISIBLE else View.GONE
        if (loadingView.visibility != visibility) loadingView.visibility = visibility
        if (loading.visible && loadingLabel.text != loading.label) loadingLabel.text = loading.label
    }

    private fun setLayout() {
        val metrics = resources.displayMetrics
        val dimensions = WindowGeometry.dimensions(metrics.widthPixels, metrics.heightPixels, PlayerState.size, aspect)
        layout.width = dimensions.first
        layout.height = dimensions.second
        layout.gravity = PlayerState.position.gravity
        videoSurface.scaleX = PlayerState.zoom
        videoSurface.scaleY = PlayerState.zoom
    }
    private fun updateWindow() {
        setLayout()
        manager.updateViewLayout(window, layout)
        player.vlcVout.setWindowSize(layout.width, layout.height)
    }
    private fun setDebugEnabled(enabled: Boolean) {
        PlayerState.debugEnabled = enabled
        getSharedPreferences("playback", MODE_PRIVATE).edit().putBoolean("debugEnabled", enabled).apply()
        previousSample = null
        main.removeCallbacks(debugTick)
        debugView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) main.post(debugTick)
    }

    private fun resetDebugInfo() {
        previousSample = null
        debugText = if (media == null) "No media loaded" else "Waiting for playback statistics…"
        debugView.text = debugText
    }

    private fun updateDebugInfo() {
        val stats = media?.stats
        val sample = stats?.let {
            PlaybackSample(SystemClock.elapsedRealtime(), it.displayedPictures, it.decodedVideo, it.readBytes)
        }
        val previous = previousSample
        val rates = if (sample != null && previous != null && sample.timeMs > previous.timeMs)
            sample.ratesSince(previous) else null
        previousSample = sample
        fun number(value: Double?) = if (value == null) "—" else String.format(Locale.US, "%.1f", value)
        val track = player.currentVideoTrack
        val sourceFps = if (track != null && track.frameRateDen > 0)
            track.frameRateNum.toDouble() / track.frameRateDen else null
        val state = when (player.playerState) {
            IMedia.State.NothingSpecial -> "Idle"
            IMedia.State.Opening -> "Opening"
            IMedia.State.Playing -> "Playing"
            IMedia.State.Paused -> "Paused"
            IMedia.State.Stopped -> "Stopped"
            IMedia.State.Ended -> "Ended"
            IMedia.State.Error -> "Playback error"
            else -> player.playerState.toString()
        }
        debugText = listOf(
            "$state · ${player.time / 1000}s · Hardware decoding requested",
            "FPS shown ${number(rates?.displayedFps)} / decoded ${number(rates?.decodedFps)} / source ${number(sourceFps)}",
            "Media input ${number(rates?.megabitsPerSecond)} Mbps · Dropped ${stats?.lostPictures ?: "—"}",
            "Last buffering progress ${number(loading.percent?.toDouble())}%",
            if (track == null) "Video format —" else "${track.width}×${track.height} · ${track.codec}",
            "Network cache target: ${activeNetworkCacheMs ?: "—"} ms · Occupancy: unavailable"
        ).joinToString("\n")
        debugView.text = debugText
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra("debugEnabled")) setDebugEnabled(intent.getBooleanExtra("debugEnabled", false))
        return START_NOT_STICKY
    }
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
