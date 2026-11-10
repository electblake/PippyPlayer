package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit

class PlaybackSettings(context: Context) {
    private val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    val upnpServer: String get() = preferences.getString("upnpServer", "")!!
    val networkCacheMs: Int get() = preferences.getInt("networkCacheMs", 3000)
    var lastPlayedUrl: String
        get() = preferences.getString("lastPlayedUrl", "")!!
        set(value) { preferences.edit { putString("lastPlayedUrl", value) } }

    fun save(upnpServer: String, networkCacheMs: Int) {
        preferences.edit {
            putString("upnpServer", upnpServer)
            putInt("networkCacheMs", networkCacheMs)
        }
    }
}
