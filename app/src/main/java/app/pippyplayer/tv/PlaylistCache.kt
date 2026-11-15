package app.pippyplayer.tv

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Stores parsed playlist metadata only; video bytes and player settings are not cached here. */
internal class PlaylistCache(
    private val directory: File,
    private val fetch: (String) -> LoadedPlaylist = MediaSource::read,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(File(context.cacheDir, "playlists-v1"))

    suspend fun load(url: String, server: String, hours: Int): List<PlaylistEntry> {
        val resolved = MediaSource.resolveUrl(url, server)
        val key = MessageDigest.getInstance("SHA-256").digest(resolved.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val file = File(directory, "$key.json")
        return sources.computeIfAbsent(file.absolutePath) { Mutex() }.withLock {
            val (cached, startedGeneration) = withContext(Dispatchers.IO) {
                synchronized(filesLock) {
                    val cached = if (hours > 0 && file.exists()) {
                        val json = JSONObject(file.readText(Charsets.UTF_8))
                        if (now() - json.getLong("savedAt") < hours * 3_600_000L) {
                            val entries = json.getJSONArray("entries")
                            List(entries.length()) { index ->
                                val entry = entries.getJSONObject(index)
                                PlaylistEntry(entry.getString("uri"),
                                    if (entry.isNull("title")) null else entry.getString("title"),
                                    if (entry.isNull("durationSeconds")) null else entry.getDouble("durationSeconds"),
                                    if (entry.isNull("mimeType")) null else entry.getString("mimeType"))
                            }
                        } else null
                    } else null
                    cached to generation
                }
            }
            if (cached != null) return@withLock cached
            val loaded = runInterruptible(Dispatchers.IO) { fetch(resolved) }
            if (hours > 0 && loaded.cacheable) {
                withContext(Dispatchers.IO) {
                    synchronized(filesLock) {
                        // Clearing while a fetch is running must not restore the cleared cache.
                        if (generation == startedGeneration) {
                            Files.createDirectories(directory.toPath())
                            val entries = JSONArray(loaded.entries.map { entry ->
                                JSONObject().put("uri", entry.uri)
                                    .put("title", entry.title ?: JSONObject.NULL)
                                    .put("durationSeconds", entry.durationSeconds ?: JSONObject.NULL)
                                    .put("mimeType", entry.mimeType ?: JSONObject.NULL)
                            })
                            val pending = File(directory, "$key.tmp")
                            pending.writeText(JSONObject().put("savedAt", now()).put("entries", entries).toString(), Charsets.UTF_8)
                            Files.move(pending.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                        }
                    }
                }
            }
            loaded.entries
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(filesLock) {
            generation++
            if (directory.exists()) {
                Files.list(directory.toPath()).use { paths -> paths.forEach { Files.delete(it) } }
            }
        }
    }

    private companion object {
        val sources = ConcurrentHashMap<String, Mutex>()
        val filesLock = Any()
        var generation = 0L
    }
}
