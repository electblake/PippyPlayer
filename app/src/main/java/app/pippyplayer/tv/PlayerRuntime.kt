package app.pippyplayer.tv

import android.graphics.PixelFormat
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
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlayerRuntime(context: android.content.Context, val id: String, private val shared: PlaybackSettings, val sourceRequest: SourceRequest = SourceRequest()) : android.content.ContextWrapper(context) {
    val settings = shared.player(id)
    private var playWhenLoaded = true
    private var showWhenLoaded = true
    var size = settings.size
        private set
    var zoom = settings.zoom
        private set
    var volume = settings.volume
        private set
    var position = settings.position
        private set
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
    lateinit var loadingListener: PlayerLoadingListener
    val loading get() = loadingListener.loading
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
        val renderers = DefaultRenderersFactory(this@PlayerRuntime)
            .setMediaCodecSelector { mime, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                    .filter { !mime.startsWith("video/") || it.hardwareAccelerated }
            }
        val dataSource = DefaultDataSource.Factory(this@PlayerRuntime).setTransferListener(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork) networkBytes.addAndGet(bytesTransferred.toLong())
            }
        })
        player = ExoPlayer.Builder(this@PlayerRuntime, renderers)
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
        videoSurface = SurfaceView(this@PlayerRuntime)
        player.setVideoSurfaceView(videoSurface)
        debugView = TextView(this@PlayerRuntime).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xCC101820.toInt())
            textSize = 11f
            setPadding(8, 4, 8, 4)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        loadingLabel = TextView(this@PlayerRuntime).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        loadingView = LinearLayout(this@PlayerRuntime).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xDD101820.toInt())
            addView(ProgressBar(this@PlayerRuntime), LinearLayout.LayoutParams(
                (28 * resources.displayMetrics.density).toInt(),
                (28 * resources.displayMetrics.density).toInt()))
            addView(loadingLabel)
            visibility = View.GONE
        }
        window = FrameLayout(this@PlayerRuntime).apply {
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
        loadingListener = PlayerLoadingListener(player, loadingView, loadingLabel)
        player.addListener(loadingListener)
        player.addListener(PlayerVisibilityListener(window))
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) queue.select(queue.itemIds.indexOf(mediaItem.mediaId))
                resetDebugInfo()
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    updateWindow()
                }
            }
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
            .put("zoom", zoom).put("volume", volume)
            .put("queueLength", queue.count)
            .put("queueIndex", queue.index)
            .put("sorting", queue.sorting.name)
            .put("sourceRequestId", sourceRequest.requestId).put("sourceRevision", sourceRequest.revision).put("sourceLoading", sourceRequest.loading)
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

    suspend fun loadSource(url: String, requestId: String) {
        playWhenLoaded = true
        showWhenLoaded = true
        sourceRequest.load(url, shared.upnpServer, requestId) { entries ->
            queue.load(entries)
            loadQueue()
            settings.lastPlayedUrl = url
            shared.recordPlayedUrl(url)
        }
    }

    fun sort(sorting: PlaylistSort) {
        queue.sort(sorting)
        player.applyQueueOrder(queue.itemIds)
        settings.sorting = sorting
    }

    fun resume() {
        playWhenLoaded = true
        showWhenLoaded = true
        if (queue.count == 0) return
        lastResume = playbackSnapshot()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.setWindowVisible(window, true)
    }

    fun pause() {
        playWhenLoaded = false
        player.pause()
    }

    fun next() { if (queue.hasNext) { player.seekToNextMediaItem(); resume() } }
    fun previous() { if (queue.hasPrevious) { player.seekToPreviousMediaItem(); resume() } }
    fun skip(offset: Long) {
        if (player.isCurrentMediaItemSeekable && player.duration != C.TIME_UNSET) {
            player.seekTo((player.currentPosition + offset).coerceIn(0L, player.duration))
        }
    }
    fun toggleVisibility() {
        if (queue.count == 0) return
        if (window.visibility == View.VISIBLE) {
            showWhenLoaded = false
            pause()
            window.visibility = View.GONE
        } else resume()
    }
    fun toggleRepeat() {
        player.toggleRepeatOne()
        settings.repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE
    }
    fun stop() {
        sourceRequest.cancel()
        playWhenLoaded = false
        showWhenLoaded = false
        player.stop()
        player.clearMediaItems()
        queue.clear()
        resetDebugInfo()
        loadingListener.update()
        window.visibility = View.GONE
    }

    fun configure(position: Position?, size: Int?, zoom: Float?, volume: Int?) {
        if (position != null) this.position = position
        if (size != null) this.size = size
        if (zoom != null) this.zoom = zoom
        if (volume != null) {
            this.volume = volume
            settings.volume = volume
            player.volume = volume / 100f
        }
        settings.saveLayout(this.position, this.size, this.zoom)
        updateWindow()
    }

    fun loadQueue() {
        if (queue.count == 0) {
            player.stop()
            player.clearMediaItems()
            window.visibility = View.GONE
            return
        }
        player.setMediaItems(queue.mediaItems(), queue.index, 0L)
        player.applyQueueOrder(queue.itemIds)
        player.prepare()
        window.visibility = if (showWhenLoaded) View.VISIBLE else View.GONE
        player.playWhenReady = playWhenLoaded
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
        sourceRequest.cancel()
        main.removeCallbacksAndMessages(null)
        player.stop()
        manager.removeView(window)
        player.release()
    }
}
