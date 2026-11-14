package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit

/** Persistent settings belonging to one registered player; shared settings live in PlaybackSettings. */
class PlayerSettings internal constructor(context: Context, val id: String) {
    private val preferences = context.getSharedPreferences("player_$id", Context.MODE_PRIVATE)
    var name: String
        get() = preferences.getString("name", "Player $id")!!
        set(value) {
            val name = value.trim()
            preferences.edit { if (name.isEmpty()) remove("name") else putString("name", name) }
        }
    val size: Int get() = preferences.getInt("size", 30)
    val zoom: Float get() = preferences.getFloat("zoom", 1f)
    val position: Position get() = Position.valueOf(preferences.getString("position", Position.BOTTOM_RIGHT.name)!!)
    val offsetX: Int get() = preferences.getInt("offsetX", 0)
    val offsetY: Int get() = preferences.getInt("offsetY", 0)
    val cropTop: Int get() = preferences.getInt("cropTop", 0)
    val cropRight: Int get() = preferences.getInt("cropRight", 0)
    val cropBottom: Int get() = preferences.getInt("cropBottom", 0)
    val cropLeft: Int get() = preferences.getInt("cropLeft", 0)
    var sorting: PlaylistSort
        get() = PlaylistSort.valueOf(preferences.getString("sorting", PlaylistSort.DEFAULT.name)!!)
        set(value) { preferences.edit { putString("sorting", value.name) } }
    var repeatOne: Boolean
        get() = preferences.getBoolean("repeatOne", false)
        set(value) { preferences.edit { putBoolean("repeatOne", value) } }
    fun saveLayout(
        position: Position, size: Int, zoom: Float, offsetX: Int? = null, offsetY: Int? = null,
        cropTop: Int? = null, cropRight: Int? = null, cropBottom: Int? = null, cropLeft: Int? = null,
    ) {
        preferences.edit {
            putString("position", position.name)
            putInt("size", size)
            putFloat("zoom", zoom)
            offsetX?.let { putInt("offsetX", it) }
            offsetY?.let { putInt("offsetY", it) }
            cropTop?.let { putInt("cropTop", it) }
            cropRight?.let { putInt("cropRight", it) }
            cropBottom?.let { putInt("cropBottom", it) }
            cropLeft?.let { putInt("cropLeft", it) }
        }
    }

    val networkCacheMs: Int get() = preferences.getInt("networkCacheMs", 3000)
    var volume: Int
        get() = preferences.getInt("volume", 50)
        set(value) { preferences.edit { putInt("volume", value) } }
    var lastPlayedUrl: String
        get() = preferences.getString("lastPlayedUrl", "")!!
        set(value) { preferences.edit { putString("lastPlayedUrl", value) } }
    fun saveNetworkCacheMs(networkCacheMs: Int) {
        preferences.edit {
            putInt("networkCacheMs", networkCacheMs)
        }
    }
}
