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
    private val players = linkedMapOf<String, PlayerRuntime>()
    private fun addPlayer(id: String): PlayerRuntime = PlayerRuntime(id).also { players[id] = it }
    private val server = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
        routing {
            route("/${PlayerState.token}") {
                get {
                    call.respondText(assets.open("controller.html").bufferedReader().use { it.readText() }, ContentType.Text.Html)
                }
                get("/state") {
                    val state = withContext(Dispatchers.Main) {
                        JSONObject().put("players", JSONArray(players.values.map { it.snapshot() }))
                            .put("recentUrls", JSONArray(PlaybackSettings(this@PlayerService).recentUrls))
                            .put("recentUrlNames", PlaybackSettings(this@PlayerService).recentUrlNames)
                            .put("debugEnabled", PlayerState.debugEnabled).toString()
                    }
                    call.respondText(state, ContentType.Application.Json)
                }
                post("/players") {
                    val state = withContext(Dispatchers.Main) {
                        val id = PlaybackSettings(this@PlayerService).addPlayer()
                        addPlayer(id).snapshot().toString()
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
                route("/players/{playerId}") {
                    delete {
                        val id = call.parameters["playerId"]!!
                        withContext(Dispatchers.Main) {
                            players.getValue(id).release()
                            players.remove(id)
                            PlaybackSettings(this@PlayerService).removePlayer(id)
                        }
                        call.respondText("OK")
                    }
                    post("/config") {
                        val fields = call.receiveParameters()
                        val position = Position.valueOf(fields.getAll("position")!!.single())
                        val size = fields.getAll("size")!!.single().toInt()
                        val zoom = fields.getAll("zoom")!!.single().toFloat()
                        val volume = fields.getAll("volume")!!.single().toInt()
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                this.position = position
                                this.size = size
                                this.zoom = zoom
                                this.volume = volume
                                settings.volume = volume
                                player.volume = volume / 100f
                                settings.saveLayout(position, size, zoom)
                                updateWindow()
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/source") {
                        val fields = call.receiveParameters()
                        val url = fields.getAll("url")!!.single()
                        val settings = PlaybackSettings(this@PlayerService, call.parameters["playerId"]!!)
                        val request = withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).sourceRequest
                        }
                        val entries = withContext(Dispatchers.IO) { request.load(url, settings.upnpServer) }
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                queue.load(entries)
                                loadQueue()
                                settings.recordPlayedUrl(url)
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/sorting") {
                        val fields = call.receiveParameters()
                        val sorting = PlaylistSort.valueOf(fields.getAll("sorting")!!.single())
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                queue.sort(sorting)
                                player.applyQueueOrder(queue.itemIds)
                                settings.sorting = sorting
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/next") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                if (queue.hasNext) { player.seekToNextMediaItem(); player.setWindowVisible(window, true) }
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/previous") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                if (queue.hasPrevious) { player.seekToPreviousMediaItem(); player.setWindowVisible(window, true) }
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/play") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                lastResume = playbackSnapshot()
                                android.util.Log.i("PippyPlayer", "Resume requested: $lastResume")
                                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                                player.setWindowVisible(window, true)
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/skip-backward") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                player.seekTo((player.currentPosition - 10_000L).coerceIn(0L, player.duration))
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/skip-forward") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                player.seekTo((player.currentPosition + 10_000L).coerceIn(0L, player.duration))
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/toggle-visibility") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                if (queue.count > 0) player.setWindowVisible(window, window.visibility != View.VISIBLE)
                            }
                        }
                        call.respondText("OK")
                    }
                    post("/toggle-repeat") {
                        withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).run { player.toggleRepeatOne(); settings.repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE } }
                        call.respondText("OK")
                    }
                    post("/pause") { withContext(Dispatchers.Main) { players.getValue(call.parameters["playerId"]!!).player.pause() }; call.respondText("OK") }
                    post("/stop") {
                        withContext(Dispatchers.Main) {
                            players.getValue(call.parameters["playerId"]!!).run {
                                player.stop()
                                player.clearMediaItems()
                                hasPlayed = false
                                resetDebugInfo()
                                updateLoadingView()
                                queue.clear()
                                window.visibility = View.GONE
                            }
                        }
                        call.respondText("OK")
                    }
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        PlayerState.token = UUID.randomUUID().toString()
        PlayerState.debugEnabled = getSharedPreferences("playback", MODE_PRIVATE).getBoolean("debugEnabled", false)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("playback", "PippyPlayer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "playback").setContentTitle("PippyPlayer")
            .setContentText("Local controller is running").setSmallIcon(R.drawable.icon)
            .setContentIntent(open).setOngoing(true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        PlaybackSettings(this).playerIds.forEach { addPlayer(it) }
        server.start(wait = false)
        PlayerState.controllerPort = runBlocking { server.engine.resolvedConnectors().single().port }
        PlayerState.running = true
    }
    private fun setDebugEnabled(enabled: Boolean) {
        PlayerState.debugEnabled = enabled
        getSharedPreferences("playback", MODE_PRIVATE).edit().putBoolean("debugEnabled", enabled).apply()
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

    private inner class PlayerRuntime(val id: String) {
        val sourceRequest = SourceRequest()
        val settings = PlaybackSettings(this@PlayerService, id)
        var size = settings.size
        var zoom = settings.zoom
        var volume = settings.volume
        var position = settings.position
        lateinit var player: ExoPlayer
        val queue = PlaybackQueue()
        val main = Handler(Looper.getMainLooper())
        lateinit var videoSurface: SurfaceView
        lateinit var window: FrameLayout
        lateinit var manager: WindowManager
        lateinit var layout: WindowManager.LayoutParams
        var aspect = 16f / 9f
        lateinit var debugView: TextView
        var previousSample: PlaybackSample? = null
        var hasPlayed = false
        val loading get() = PlaybackLoading.from(
            player.playbackState == Player.STATE_BUFFERING, player.playWhenReady, hasPlayed)
        lateinit var loadingView: LinearLayout
        lateinit var loadingLabel: TextView
        var debugText = "No media loaded"
        var rebufferMs = 0
        val networkBytes = AtomicLong()
        var decoderName: String? = null
        var lastResume = "No resume requested"
        val debugTick = object : Runnable {
            override fun run() {
                updateDebugInfo()
                main.postDelayed(this, 1000)
            }
        }
        init {
            queue.sort(settings.sorting)
            rebufferMs = settings.networkCacheMs
            // Keep decoder prewarming disabled; only compressed samples are preloaded.
            val renderers = DefaultRenderersFactory(this@PlayerService)
                .setMediaCodecSelector { mime, secure, tunneling ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                        .filter { !mime.startsWith("video/") || it.hardwareAccelerated }
                }
            val dataSource = DefaultDataSource.Factory(this@PlayerService).setTransferListener(object : TransferListener {
                override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
                override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
                override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
                override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                    if (isNetwork) networkBytes.addAndGet(bytesTransferred.toLong())
                }
            })
            player = ExoPlayer.Builder(this@PlayerService, renderers)
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
            player.volume = volume / 100f
            videoSurface = SurfaceView(this@PlayerService)
            player.setVideoSurfaceView(videoSurface)
            debugView = TextView(this@PlayerService).apply {
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xCC101820.toInt())
                textSize = 11f
                setPadding(8, 4, 8, 4)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            loadingLabel = TextView(this@PlayerService).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
            }
            loadingView = LinearLayout(this@PlayerService).apply {
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
            window = FrameLayout(this@PlayerService).apply {
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
            player.repeatMode = if (settings.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setDebugEnabled(PlayerState.debugEnabled)
        }
        fun snapshot(): JSONObject =
            JSONObject().put("id", id).put("name", "Player $id").put("url", settings.lastPlayedUrl).put("size", size)
                .put("recentUrls", JSONArray(settings.recentUrls))
                .put("recentUrlNames", settings.recentUrlNames)
                .put("zoom", zoom).put("volume", volume)
                .put("queueLength", queue.count)
                .put("queueIndex", queue.index)
                .put("sorting", queue.sorting.name)
                .put("hasPrevious", queue.hasPrevious).put("hasNext", queue.hasNext)
                .put("position", position.name).put("playing", player.isPlaying)
                .put("visible", window.visibility == View.VISIBLE)
                .put("repeatOne", player.repeatMode == Player.REPEAT_MODE_ONE)
                .put("playbackState", playbackState())
                .put("loading", loading.visible).put("loadingText", loading.label)
                .put("bufferedMs", player.totalBufferedDuration)
                .put("decoder", decoderName ?: JSONObject.NULL)
                .put("seekable", player.isCurrentMediaItemSeekable && player.duration != C.TIME_UNSET)
                .put("error", sourceRequest.error ?: player.playerError?.toString() ?: JSONObject.NULL)
                .put("width", layout.width).put("height", layout.height)
                .put("debugText", debugText)
                .put("elapsed", player.currentPosition)

        fun loadQueue() {
            player.setMediaItems(queue.mediaItems(), queue.index, 0L)
            player.prepare()
            player.setWindowVisible(window, true)
        }

        fun updateLoadingView() {
            val visibility = if (loading.visible) View.VISIBLE else View.GONE
            if (loadingView.visibility != visibility) loadingView.visibility = visibility
            if (loading.visible && loadingLabel.text != loading.label) loadingLabel.text = loading.label
        }

        fun setLayout() {
            val metrics = resources.displayMetrics
            val dimensions = WindowGeometry.dimensions(metrics.widthPixels, metrics.heightPixels, size, aspect)
            layout.width = dimensions.first
            layout.height = dimensions.second
            layout.gravity = position.gravity
            videoSurface.scaleX = zoom
            videoSurface.scaleY = zoom
        }
        fun updateWindow() {
            setLayout()
            manager.updateViewLayout(window, layout)
        }
        fun setDebugEnabled(enabled: Boolean) {
            previousSample = null
            main.removeCallbacks(debugTick)
            debugView.visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) main.post(debugTick)
        }

        fun resetDebugInfo() {
            previousSample = null
            debugText = if (player.mediaItemCount == 0) "No media loaded" else "Waiting for playback statistics…"
            debugView.text = debugText
        }

        fun updateDebugInfo() {
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

        fun playbackState(): String = when {
            player.playerError != null -> "Error"
            player.playbackState == Player.STATE_ENDED -> "Ended"
            player.playbackState == Player.STATE_IDLE -> "Stopped"
            !player.playWhenReady -> "Paused"
            player.playbackState == Player.STATE_BUFFERING -> "Buffering"
            player.isPlaying -> "Playing"
            else -> "Suppressed"
        }

        fun playbackSnapshot() = "${playbackState()} · ${player.currentPosition / 1000}s · " +
            "Buffered ${player.totalBufferedDuration} ms · playWhenReady=${player.playWhenReady} · " +
            "suppression=${player.playbackSuppressionReason} · rendered=${player.videoDecoderCounters?.renderedOutputBufferCount}"

        fun release() {
            main.removeCallbacksAndMessages(null)
            player.stop()
            manager.removeView(window)
            player.release()
        }
    }
}
