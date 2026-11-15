package app.pippyplayer.tv

import android.content.Context
import android.os.HandlerThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Service-owned resources; runtimes release their players before these are closed. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackResources(private val context: Context) {
    private val thread = HandlerThread("PippyPlayback").apply { start() }
    val playbackLooper get() = thread.looper
    val loadControl = PipLoadControl(3000)
    private val database = StandaloneDatabaseProvider(context)
    private val cache = SimpleCache(File(context.cacheDir, "media-v1"),
        LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024), database)
    private val upstream = DefaultHttpDataSource.Factory()

    fun mediaSourceFactory(listener: TransferListener): MediaSource.Factory {
        val network = DefaultDataSource.Factory(context, upstream).setTransferListener(listener)
        val cached = DefaultMediaSourceFactory(DefaultDataSource.Factory(context,
            CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(network)
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)))
        val streaming = DefaultMediaSourceFactory(network)
        return object : MediaSource.Factory by streaming {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val configuration = mediaItem.localConfiguration!!
                val type = Util.inferContentTypeForUriAndMimeType(configuration.uri, configuration.mimeType)
                // Adaptive manifests must be fetched fresh; cache only progressive HTTP media.
                val http = configuration.uri.scheme in listOf("http", "https")
                return (if (http && type == C.CONTENT_TYPE_OTHER) cached else streaming).createMediaSource(mediaItem)
            }
        }
    }

    fun release() {
        cache.release()
        database.close()
        thread.quitSafely()
    }
}
