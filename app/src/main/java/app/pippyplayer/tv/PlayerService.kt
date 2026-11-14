package app.pippyplayer.tv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.SurfaceView
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import java.util.concurrent.atomic.AtomicLong

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerService : Service() {
    private lateinit var player: ExoPlayer
    private val queue = PlaybackQueue()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var videoSurface: SurfaceView
    private lateinit var window: FrameLayout
    private lateinit var manager: WindowManager
    private lateinit var layout: WindowManager.LayoutParams
    private var aspect = 16f / 9f
    private lateinit var debugView: TextView
    private var previousSample: PlaybackSample? = null
    private var hasPlayed = false
    private val loading get() = PlaybackLoading.from(
        player.playbackState == Player.STATE_BUFFERING, player.playWhenReady, hasPlayed)
    private lateinit var loadingView: LinearLayout
    private lateinit var loadingLabel: TextView
    private var debugText = "No media loaded"
    private var rebufferMs = 0
    private val networkBytes = AtomicLong()
    private var decoderName: String? = null
    private var lastResume = "No resume requested"
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
                            .put("recentUrls", JSONArray(PlaybackSettings(this@PlayerService).recentUrls))
                            .put("recentUrlNames", PlaybackSettings(this@PlayerService).recentUrlNames)
                            .put("zoom", PlayerState.zoom).put("volume", PlayerState.volume)
                            .put("queueLength", queue.count)
                            .put("queueIndex", queue.index)
                            .put("sorting", queue.sorting.name)
                            .put("hasPrevious", queue.hasPrevious).put("hasNext", queue.hasNext)
                            .put("position", PlayerState.position.name).put("playing", player.isPlaying)
                            .put("visible", window.visibility == View.VISIBLE)
                            .put("repeatOne", player.repeatMode == Player.REPEAT_MODE_ONE)
                            .put("playbackState", playbackState())
                            .put("loading", loading.visible).put("loadingText", loading.label)
                            .put("bufferedMs", player.totalBufferedDuration)
                            .put("decoder", decoderName ?: JSONObject.NULL)
                            .put("seekable", player.isCurrentMediaItemSeekable && player.duration != C.TIME_UNSET)
                            .put("error", player.playerError?.toString() ?: JSONObject.NULL)
                            .put("width", layout.width).put("height", layout.height)
                            .put("debugEnabled", PlayerState.debugEnabled).put("debugText", debugText)
                            .put("elapsed", player.currentPosition).toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/recent-url-name") {
                    val fields = call.receiveParameters()
                    val url = fields.getAll("url")!!.single()
                    val name = fields.getAll("name")!!.single()
                    withContext(Dispatchers.Main) { PlaybackSettings(this@PlayerService).nameRecentUrl(url, name) }
                    call.respondText("OK")
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
                        PlaybackSettings(this@PlayerService).volume = volume
                        player.volume = volume / 100f
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
                        loadQueue()
                        settings.recordPlayedUrl(url)
                    }
                    call.respondText("OK")
                }
                post("/sorting") {
                    val fields = call.receiveParameters()
                    val sorting = PlaylistSort.valueOf(fields.getAll("sorting")!!.single())
                    withContext(Dispatchers.Main) {
                        queue.sort(sorting)
                        player.applyQueueOrder(queue.itemIds)
                        getSharedPreferences("playback", MODE_PRIVATE).edit()
                            .putString("sorting", sorting.name).apply()
                    }
                    call.respondText("OK")
                }
                post("/next") {
                    withContext(Dispatchers.Main) {
                        if (queue.hasNext) { player.seekToNextMediaItem(); player.setWindowVisible(window, true) }
                    }
                    call.respondText("OK")
                }
                post("/previous") {
                    withContext(Dispatchers.Main) {
                        if (queue.hasPrevious) { player.seekToPreviousMediaItem(); player.setWindowVisible(window, true) }
                    }
                    call.respondText("OK")
                }
                post("/play") {
                    withContext(Dispatchers.Main) {
                        lastResume = playbackSnapshot()
                        android.util.Log.i("PippyPlayer", "Resume requested: $lastResume")
                        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                        player.setWindowVisible(window, true)
                    }
                    call.respondText("OK")
                }
                post("/skip-backward") {
                    withContext(Dispatchers.Main) {
                        player.seekTo((player.currentPosition - 10_000L).coerceIn(0L, player.duration))
                    }
                    call.respondText("OK")
                }
                post("/skip-forward") {
                    withContext(Dispatchers.Main) {
                        player.seekTo((player.currentPosition + 10_000L).coerceIn(0L, player.duration))
                    }
                    call.respondText("OK")
                }
                post("/toggle-visibility") {
                    withContext(Dispatchers.Main) {
                        if (queue.count > 0) player.setWindowVisible(window, window.visibility != View.VISIBLE)
                    }
                    call.respondText("OK")
                }
                post("/toggle-repeat") {
                    withContext(Dispatchers.Main) { player.toggleRepeatOne() }
                    call.respondText("OK")
                }
                post("/pause") { withContext(Dispatchers.Main) { player.pause() }; call.respondText("OK") }
                post("/stop") {
                    withContext(Dispatchers.Main) {
                        player.stop()
                        player.clearMediaItems()
                        hasPlayed = false
                        resetDebugInfo()
                        updateLoadingView()
                        queue.clear()
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
        PlayerState.volume = PlaybackSettings(this).volume
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("playback", "PippyPlayer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "playback").setContentTitle("PippyPlayer")
            .setContentText("Local controller is running").setSmallIcon(R.drawable.icon)
            .setContentIntent(open).setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        rebufferMs = PlaybackSettings(this).networkCacheMs
        // Keep decoder prewarming disabled; only compressed samples are preloaded.
        val renderers = DefaultRenderersFactory(this)
            .setMediaCodecSelector { mime, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                    .filter { !mime.startsWith("video/") || it.hardwareAccelerated }
            }
        val dataSource = DefaultDataSource.Factory(this).setTransferListener(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork) networkBytes.addAndGet(bytesTransferred.toLong())
            }
        })
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(PipLoadControl(rebufferMs))
            .build()
        player.preloadConfiguration = ExoPlayer.PreloadConfiguration(3_000_000L)
        player.setAudioAttributes(AudioAttributes.DEFAULT, false)
        player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(1280, 720)
            .setMaxVideoFrameRate(30)
            .build()
        player.volume = PlayerState.volume / 100f
        videoSurface = SurfaceView(this)
        player.setVideoSurfaceView(videoSurface)
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
        player.addListener(PlayerVisibilityListener(window))
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) queue.select(queue.itemIds.indexOf(mediaItem.mediaId))
                hasPlayed = player.isPlaying
                resetDebugInfo()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) hasPlayed = true
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    updateWindow()
                }
            }
            override fun onEvents(player: Player, events: Player.Events) { updateLoadingView() }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, name: String,
                initializedTimestampMs: Long, initializationDurationMs: Long) {
                decoderName = name
                previousSample = null
            }
            override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, name: String) {
                decoderName = null
                previousSample = null
            }
        })
        setDebugEnabled(getSharedPreferences("playback", MODE_PRIVATE).getBoolean("debugEnabled", false))
        server.start(wait = false)
        PlayerState.controllerPort = runBlocking { server.engine.resolvedConnectors().single().port }
        PlayerState.running = true
    }

    private fun loadQueue() {
        player.setMediaItems(queue.mediaItems(), queue.index, 0L)
        player.prepare()
        player.setWindowVisible(window, true)
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
        debugText = if (player.mediaItemCount == 0) "No media loaded" else "Waiting for playback statistics…"
        debugView.text = debugText
    }

    private fun updateDebugInfo() {
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val sample = counters?.let {
            PlaybackSample(SystemClock.elapsedRealtime(), it.renderedOutputBufferCount, it.queuedInputBufferCount, networkBytes.get())
        }
        val previous = previousSample
        val rates = if (sample != null && previous != null && sample.timeMs > previous.timeMs)
            sample.ratesSince(previous) else null
        previousSample = sample
        fun number(value: Double?) = if (value == null) "—" else String.format(Locale.US, "%.1f", value)
        val track = player.videoFormat
        val sourceFps = track?.frameRate?.takeIf { it > 0 }?.toDouble()
        debugText = listOf(
            playbackSnapshot(),
            "Hardware video decoder: ${decoderName ?: "—"}",
            "FPS shown ${number(rates?.displayedFps)} / queued ${number(rates?.decodedFps)} / source ${number(sourceFps)}",
            "Network input (including preload): ${number(rates?.megabitsPerSecond)} Mbps · Dropped ${counters?.droppedBufferCount ?: "—"}",
            if (track == null) "Video format —" else "${track.width}×${track.height} · ${track.sampleMimeType}",
            "Rebuffer threshold: $rebufferMs ms · Next-item preload: 3000 ms · Sample buffer target: 16 MiB",
            "Before last Resume: $lastResume"
        ).joinToString("\n")
        debugView.text = debugText
    }

    private fun playbackState(): String = when {
        player.playerError != null -> "Error"
        player.playbackState == Player.STATE_ENDED -> "Ended"
        player.playbackState == Player.STATE_IDLE -> "Stopped"
        !player.playWhenReady -> "Paused"
        player.playbackState == Player.STATE_BUFFERING -> "Buffering"
        player.isPlaying -> "Playing"
        else -> "Suppressed"
    }

    private fun playbackSnapshot() = "${playbackState()} · ${player.currentPosition / 1000}s · " +
        "Buffered ${player.totalBufferedDuration} ms · playWhenReady=${player.playWhenReady} · " +
        "suppression=${player.playbackSuppressionReason} · rendered=${player.videoDecoderCounters?.renderedOutputBufferCount}"

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra("debugEnabled")) setDebugEnabled(intent.getBooleanExtra("debugEnabled", false))
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        server.stop(0, 0)
        player.stop()
        manager.removeView(window)
        player.release()
        PlayerState.running = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent): IBinder? = null
}
