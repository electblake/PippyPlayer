package app.pippyplayer.tv

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Shared configuration and registered players. Player settings require an explicit ID. */
class PlaybackSettings(private val context: Context) {
    private val shared = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    init {
        if (shared.getInt("settingsSchemaVersion", 0) == 0) {
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
            if ("1" in playerIds && !shared.getBoolean("player1SettingsMigrated", false)) {
                val preferences = context.getSharedPreferences("player_1", Context.MODE_PRIVATE)
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
            shared.edit(commit = true) { putInt("settingsSchemaVersion", 1) }
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
    var upnpServer: String
        get() = shared.getString("upnpServer", "")!!
        set(value) { shared.edit { putString("upnpServer", value) } }
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
        shared.edit { putString("recentUrls", JSONArray(urls).toString()) }
    }

    fun player(id: String): PlayerSettings {
        playerIds.single { it == id }
        return PlayerSettings(context, id)
    }

    var debugEnabled: Boolean
        get() = shared.getBoolean("debugEnabled", false)
        set(value) { shared.edit { putBoolean("debugEnabled", value) } }
}
