package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit

/** Persistent settings belonging to one registered player; shared settings live in PlaybackSettings. */
class PlayerSettings internal constructor(context: Context, val id: String) {
    private val preferences = context.getSharedPreferences("player_$id", Context.MODE_PRIVATE)
    val size: Int get() = preferences.getInt("size", 30)
    val zoom: Float get() = preferences.getFloat("zoom", 1f)
    val position: Position get() = Position.valueOf(preferences.getString("position", Position.BOTTOM_RIGHT.name)!!)
    var sorting: PlaylistSort
        get() = PlaylistSort.valueOf(preferences.getString("sorting", PlaylistSort.DEFAULT.name)!!)
        set(value) { preferences.edit { putString("sorting", value.name) } }
    var repeatOne: Boolean
        get() = preferences.getBoolean("repeatOne", false)
        set(value) { preferences.edit { putBoolean("repeatOne", value) } }
    fun saveLayout(position: Position, size: Int, zoom: Float) {
        preferences.edit { putString("position", position.name); putInt("size", size); putFloat("zoom", zoom) }
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
