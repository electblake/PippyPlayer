package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

class PlaybackSettings(context: Context) {
    private val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    val upnpServer: String get() = preferences.getString("upnpServer", "")!!
    val networkCacheMs: Int get() = preferences.getInt("networkCacheMs", 3000)
    var volume: Int
        get() = preferences.getInt("volume", 50)
        set(value) { preferences.edit { putInt("volume", value) } }
    val lastPlayedUrl: String
        get() = preferences.getString("lastPlayedUrl", "")!!
    val recentUrls: List<String>
        get() {
            val urls = JSONArray(preferences.getString("recentUrls", "[]")!!)
            return List(urls.length()) { urls.getString(it) }
        }

    fun recordPlayedUrl(url: String) {
        val urls = listOf(url) + recentUrls.filterNot { it == url }
        preferences.edit {
            putString("lastPlayedUrl", url)
            putString("recentUrls", JSONArray(urls).toString())
        }
    }

    fun save(upnpServer: String, networkCacheMs: Int) {
        preferences.edit {
            putString("upnpServer", upnpServer)
            putInt("networkCacheMs", networkCacheMs)
        }
    }
}
