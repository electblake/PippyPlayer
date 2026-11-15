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
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
internal class PlayerRuntime(context: android.content.Context, val id: String, private val shared: PlaybackSettings,
    private val playbackResources: PlaybackResources,
    val sourceRequest: SourceRequest = SourceRequest { url, server ->
        if (url == "favourites:") shared.favourites
        else PlaylistCache(context).load(url, server, shared.playlistCacheHours)
    }) : android.content.ContextWrapper(context) {
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
    private var updatingQueueWindow = false
    var rebufferMs = 0
    val networkBytes = AtomicLong()
    var decoderName: String? = null
    var lastResume = "No resume requested"
    private var previousProgress: PlaybackSample? = null
    private var previousProgressPosition: Long? = null
    private val progressTick = object : Runnable {
        override fun run() {
            recordPlaybackProgress()
            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE) main.postDelayed(this, 5000)
        }
    }
    val debugTick = object : Runnable {
        override fun run() {
            updateDebugInfo()
            main.postDelayed(this, 1000)
        }
    }
    init {
        DiagnosticLog.write(this, "player=$id CREATE position=$position")
        queue.sort(settings.sorting)
        rebufferMs = settings.networkCacheMs
        // Keep decoder prewarming disabled; only compressed samples are preloaded.
        val renderers = DefaultRenderersFactory(this@PlayerRuntime)
            .setMediaCodecSelector { mime, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                    .filter { !mime.startsWith("video/") || it.hardwareAccelerated }
            }
        val mediaSources = playbackResources.mediaSourceFactory(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork) networkBytes.addAndGet(bytesTransferred.toLong())
            }
        })
        playbackResources.loadControl.register(id, rebufferMs)
        player = ExoPlayer.Builder(this@PlayerRuntime, renderers)
            .setMediaSourceFactory(mediaSources)
            .setLoadControl(playbackResources.loadControl)
            .setPlaybackLooper(playbackResources.playbackLooper)
            .setName(id)
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
            addView(videoSurface, FrameLayout.LayoutParams(-1, -1, Gravity.TOP or Gravity.LEFT))
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id STATE $playbackState playing=${player.isPlaying} item=${queue.index}")
                scheduleProgress()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id PLAY_WHEN_READY value=$playWhenReady reason=$reason")
                scheduleProgress()
            }
            override fun onIsLoadingChanged(isLoading: Boolean) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id LOADING value=$isLoading bufferedMs=${player.totalBufferedDuration} sharedSampleBytes=${playbackResources.loadControl.allocatedBytes}")
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id DISCONTINUITY reason=$reason from=${oldPosition.mediaItem?.mediaId}@${oldPosition.positionMs} to=${newPosition.mediaItem?.mediaId}@${newPosition.positionMs}")
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id ERROR ${error.stackTraceToString()}")
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                DiagnosticLog.write(this@PlayerRuntime, "player=$id TRANSITION reason=$reason mediaId=${mediaItem?.mediaId} queueIndex=${queue.index} updatingWindow=$updatingQueueWindow")
                previousProgress = null
                previousProgressPosition = null
                if (mediaItem != null && !updatingQueueWindow) {
                    queue.selectId(mediaItem.mediaId)
                    syncQueueWindow()
                }
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
                previousProgress = null
                DiagnosticLog.write(this@PlayerRuntime, "player=$id DECODER $name initMs=$initializationDurationMs")
                previousSample = null
            }
            override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, name: String) {
                decoderName = null
                previousProgress = null
                DiagnosticLog.write(this@PlayerRuntime, "player=$id DECODER RELEASE $name")
                previousSample = null
            }
        })
        player.repeatMode = if (settings.repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        setDebugEnabled(PlayerState.debugEnabled)
    }
    fun snapshot(): JSONObject =
        JSONObject().put("id", id).put("name", settings.name).put("url", settings.lastPlayedUrl).put("size", size)
            .put("zoom", zoom).put("volume", volume)
            .put("queueLength", queue.count)
            .put("favourite", queue.count > 0 && shared.favourites.any { it.uri == queue.current.uri })
            .put("preparedItems", player.mediaItemCount)
            .put("sampleBytes", playbackResources.loadControl.allocatedBytes)
            .put("queueIndex", queue.index)
            .put("sorting", queue.sorting.name)
            .put("sourceRequestId", sourceRequest.requestId).put("sourceRevision", sourceRequest.revision).put("sourceLoading", sourceRequest.loading)
            .put("hasPrevious", queue.hasPrevious).put("hasNext", queue.hasNext)
            .put("position", position.name).put("playing", player.isPlaying)
            .put("offsetX", settings.offsetX).put("offsetY", settings.offsetY)
            .put("cropTop", settings.cropTop).put("cropRight", settings.cropRight)
            .put("cropBottom", settings.cropBottom).put("cropLeft", settings.cropLeft)
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

    fun favourite() { shared.addFavourite(queue.current) }

    suspend fun loadSource(url: String, requestId: String, favouriteUri: String? = null) {
        DiagnosticLog.write(this, "player=$id SOURCE begin request=$requestId")
        playWhenLoaded = true
        showWhenLoaded = true
        sourceRequest.load(url, shared.upnpServer, requestId) { entries ->
            queue.load(entries)
            if (favouriteUri != null) queue.select(queue.entries.indexOfFirst { it.uri == favouriteUri })
            loadQueue()
            settings.lastPlayedUrl = url
            if (url != "favourites:") shared.recordPlayedUrl(url)
            DiagnosticLog.write(this, "player=$id SOURCE loaded count=${queue.count}")
        }
    }

    fun sort(sorting: PlaylistSort) {
        queue.sort(sorting)
        syncQueueWindow()
        settings.sorting = sorting
    }

    fun resume() {
        DiagnosticLog.write(this, "player=$id RESUME")
        playWhenLoaded = true
        showWhenLoaded = true
        if (queue.count == 0) return
        lastResume = playbackSnapshot()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.setWindowVisible(window, true)
    }

    fun pause() {
        DiagnosticLog.write(this, "player=$id PAUSE")
        playWhenLoaded = false
        player.pause()
        player.stop() // Retains timeline and position while releasing codecs and sample buffers.
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
        DiagnosticLog.write(this, "player=$id STOP")
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

    fun configure(
        position: Position?, size: Int?, zoom: Float?, volume: Int?, offsetX: Int? = null, offsetY: Int? = null,
        cropTop: Int? = null, cropRight: Int? = null, cropBottom: Int? = null, cropLeft: Int? = null,
    ) {
        DiagnosticLog.write(this, "player=$id CONFIG position=$position size=$size zoom=$zoom volume=$volume offset=$offsetX,$offsetY crop=$cropTop,$cropRight,$cropBottom,$cropLeft")
        if (volume != null) {
            this.volume = volume
            settings.volume = volume
            player.volume = volume / 100f
        }
        if (position == null && size == null && zoom == null && offsetX == null && offsetY == null &&
            cropTop == null && cropRight == null && cropBottom == null && cropLeft == null) return
        if (position != null) this.position = position
        if (size != null) this.size = size
        if (zoom != null) this.zoom = zoom
        settings.saveLayout(this.position, this.size, this.zoom, offsetX, offsetY, cropTop, cropRight, cropBottom, cropLeft)
        updateWindow()
    }

    fun loadQueue() {
        if (queue.count == 0) {
            player.stop()
            player.clearMediaItems()
            window.visibility = View.GONE
            return
        }
        updatingQueueWindow = true
        player.setMediaItems(queue.windowItems(), queue.index - queue.windowStart, 0L)
        updatingQueueWindow = false
        if (playWhenLoaded) player.prepare()
        window.visibility = if (showWhenLoaded) View.VISIBLE else View.GONE
        player.playWhenReady = playWhenLoaded
    }

    private fun syncQueueWindow() {
        if (queue.count == 0 || updatingQueueWindow) return
        DiagnosticLog.write(this, "player=$id QUEUE_WINDOW begin index=${queue.index} current=${player.currentMediaItem?.mediaId}")
        updatingQueueWindow = true
        val items = queue.windowItems()
        // Retain matching MediaSources, including the next item's preloaded samples.
        val wanted = items.map { it.mediaId }.toSet()
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId !in wanted) player.removeMediaItem(index)
        }
        for ((index, item) in items.withIndex()) {
            val existing = (index until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == item.mediaId
            }
            if (existing == null) player.addMediaItem(index, item)
            else if (existing != index) player.moveMediaItem(existing, index)
        }
        updatingQueueWindow = false
        DiagnosticLog.write(this, "player=$id QUEUE_WINDOW done ids=${items.map { it.mediaId }} current=${player.currentMediaItem?.mediaId}")
    }

    private fun scheduleProgress() {
        main.removeCallbacks(progressTick)
        main.post(progressTick)
    }

    internal fun recordPlaybackProgress() {
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val previous = previousProgress
        val position = player.currentPosition
        val now = SystemClock.elapsedRealtime()
        val bytes = networkBytes.get()
        DiagnosticLog.write(this, "player=$id PROGRESS state=${playbackState()} playing=${player.isPlaying}" +
            " playWhenReady=${player.playWhenReady} suppression=${player.playbackSuppressionReason}" +
            " mediaId=${player.currentMediaItem?.mediaId} queueIndex=${queue.index}/${queue.count} prepared=${player.mediaItemCount}" +
            " positionMs=$position positionDeltaMs=${previousProgressPosition?.let { position - it }}" +
            " bufferedMs=${player.totalBufferedDuration} loading=${player.isLoading} sourceLoading=${sourceRequest.loading}" +
            " rendered=${counters?.renderedOutputBufferCount} renderedDelta=${counters?.let { current -> previous?.let { current.renderedOutputBufferCount - it.displayed } }}" +
            " queued=${counters?.queuedInputBufferCount} dropped=${counters?.droppedBufferCount}" +
            " sampleIntervalMs=${previous?.let { now - it.timeMs }} networkBytes=$bytes networkDelta=${previous?.let { bytes - it.bytes }}" +
            " sharedSampleBytes=${playbackResources.loadControl.allocatedBytes} decoder=$decoderName")
        previousProgress = counters?.let { PlaybackSample(now, it.renderedOutputBufferCount, it.queuedInputBufferCount, bytes) }
        previousProgressPosition = position
    }

    private fun setLayout(): Boolean {
        val metrics = resources.displayMetrics
        val (width, height) = WindowGeometry.dimensions(metrics.widthPixels, metrics.heightPixels, size, aspect)
        val crop = WindowGeometry.crop(width, height, settings.cropTop, settings.cropRight, settings.cropBottom, settings.cropLeft)
        val windowWidth = crop.width()
        val windowHeight = crop.height()
        val gravity = position.gravity
        val x = WindowGeometry.percentPixels(settings.offsetX, metrics.widthPixels)
        val y = WindowGeometry.percentPixels(settings.offsetY, metrics.heightPixels)
        // Place the cropped window over the matching part of the original frame.
        val windowX = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.LEFT -> x + crop.left
            Gravity.RIGHT -> width - crop.right - x
            else -> x + crop.left + (metrics.widthPixels - width) / 2 - (metrics.widthPixels - windowWidth) / 2
        }
        val windowY = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> y + crop.top
            Gravity.BOTTOM -> height - crop.bottom - y
            else -> y + crop.top + (metrics.heightPixels - height) / 2 - (metrics.heightPixels - windowHeight) / 2
        }
        // Preserve the flags WindowManager adds when it attaches the overlay.
        val noLimits = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val flags = if (x != 0 || y != 0) layout.flags or noLimits else layout.flags and noLimits.inv()
        val windowChanged = layout.width != windowWidth || layout.height != windowHeight ||
            layout.gravity != gravity || layout.x != windowX || layout.y != windowY || layout.flags != flags
        if (windowChanged) {
            layout.width = windowWidth
            layout.height = windowHeight
            layout.gravity = gravity
            layout.x = windowX
            layout.y = windowY
            layout.flags = flags
        }

        // Match the overlay for normal playback. Crops use layout coordinates so the
        // SurfaceView and the transparent region that reveals its video stay aligned.
        val cropped = crop.left != 0 || crop.top != 0 || windowWidth != width || windowHeight != height
        val surfaceWidth = if (cropped) width else FrameLayout.LayoutParams.MATCH_PARENT
        val surfaceHeight = if (cropped) height else FrameLayout.LayoutParams.MATCH_PARENT
        val videoLayout = videoSurface.layoutParams as FrameLayout.LayoutParams
        if (videoLayout.width != surfaceWidth || videoLayout.height != surfaceHeight ||
            videoLayout.leftMargin != -crop.left || videoLayout.topMargin != -crop.top) {
            videoLayout.width = surfaceWidth
            videoLayout.height = surfaceHeight
            videoLayout.leftMargin = -crop.left
            videoLayout.topMargin = -crop.top
            videoSurface.layoutParams = videoLayout
        }
        videoSurface.scaleX = zoom
        videoSurface.scaleY = zoom
        return windowChanged
    }
    fun updateWindow() {
        DiagnosticLog.write(this, "player=$id WINDOW begin position=$position size=$size zoom=$zoom")
        if (setLayout()) manager.updateViewLayout(window, layout)
        DiagnosticLog.write(this, "player=$id WINDOW done")
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
            "Rebuffer threshold: $rebufferMs ms · Next-item preload: single active player only · Shared sample budget: 32 MiB",
            "Before last Resume: $lastResume"
        ).joinToString("\n")
        debugView.text = debugText
    }

    fun playbackState(): String = when {
        player.playerError != null -> "Error"
        player.playbackState == Player.STATE_ENDED -> "Ended"
        player.playbackState == Player.STATE_IDLE && queue.count > 0 -> "Paused"
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
        DiagnosticLog.write(this, "player=$id RELEASE begin")
        sourceRequest.cancel()
        player.stop()
        manager.removeView(window)
        player.release()
        main.removeCallbacksAndMessages(null)
        playbackResources.loadControl.unregister(id)
    }
}
