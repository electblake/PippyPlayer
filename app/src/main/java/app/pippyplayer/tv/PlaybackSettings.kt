package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class PlaybackSettings(private val context: Context, playerId: String = "1") {
    private val shared = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val preferences = context.getSharedPreferences("player_$playerId", Context.MODE_PRIVATE)

    init {
        if (!shared.getBoolean("sharedUpnpServerRestored", false)) {
            val firstPlayerServer = context.getSharedPreferences("player_1", Context.MODE_PRIVATE)
                .getString("upnpServer", "")!!
            shared.edit(commit = true) {
                if ("1" in playerIds && firstPlayerServer.isNotEmpty()) {
                    putString("upnpServer", firstPlayerServer)
                } else if (shared.getString("upnpServer", "")!!.isEmpty()) {
                    val configuredServer = playerIds.map {
                        context.getSharedPreferences("player_$it", Context.MODE_PRIVATE).getString("upnpServer", "")!!
                    }.firstOrNull { it.isNotEmpty() }
                    if (configuredServer != null) putString("upnpServer", configuredServer)
                }
                putBoolean("sharedUpnpServerRestored", true)
            }
        }
        if (playerId == "1" && "1" in playerIds && !shared.getBoolean("player1SettingsMigrated", false)) {
            preferences.edit(commit = true) {
                for (key in listOf("lastPlayedUrl", "sorting")) {
                    if (shared.contains(key) && !preferences.contains(key)) putString(key, shared.getString(key, null))
                }
                for (key in listOf("networkCacheMs", "volume")) {
                    if (shared.contains(key) && !preferences.contains(key)) putInt(key, shared.getInt(key, 0))
                }
            }
            shared.edit(commit = true) { putBoolean("player1SettingsMigrated", true) }
        }
    }

    val playerIds: List<String> get() {
        val ids = JSONArray(shared.getString("players", "[\"1\"]")!!)
        return List(ids.length()) { ids.getString(it) }
    }
    fun addPlayer(): String {
        val ids = playerIds
        val nextId = shared.getInt("nextPlayerId", 2)
        val id = nextId.toString()
        shared.edit {
            putString("players", JSONArray(ids + id).toString())
            putInt("nextPlayerId", nextId + 1)
        }
        return id
    }
    fun removePlayer(id: String) {
        shared.edit { putString("players", JSONArray(playerIds.filterNot { it == id }).toString()) }
        context.deleteSharedPreferences("player_$id")
    }
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

    var upnpServer: String
        get() = shared.getString("upnpServer", "")!!
        set(value) { shared.edit { putString("upnpServer", value) } }
    val networkCacheMs: Int get() = preferences.getInt("networkCacheMs", 3000)
    var volume: Int
        get() = preferences.getInt("volume", 50)
        set(value) { preferences.edit { putInt("volume", value) } }
    val lastPlayedUrl: String
        get() = preferences.getString("lastPlayedUrl", "")!!
    val recentUrls: List<String>
        get() {
            val urls = JSONArray(shared.getString("recentUrls", "[]")!!)
            return List(urls.length()) { urls.getString(it) }
        }

    val recentUrlNames: JSONObject
        get() = JSONObject(shared.getString("recentUrlNames", "{}")!!)

    fun nameRecentUrl(url: String, name: String) {
        val names = recentUrlNames
        if (name.isEmpty()) names.remove(url) else names.put(url, name)
        shared.edit { putString("recentUrlNames", names.toString()) }
    }

    fun recordPlayedUrl(url: String) {
        val urls = listOf(url) + recentUrls.filterNot { it == url }
        preferences.edit { putString("lastPlayedUrl", url) }
        shared.edit { putString("recentUrls", JSONArray(urls).toString()) }
    }

    fun saveNetworkCacheMs(networkCacheMs: Int) {
        preferences.edit {
            putInt("networkCacheMs", networkCacheMs)
        }
    }
}
