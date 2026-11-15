package app.pippyplayer.tv

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : Activity() {
    private val scope = MainScope()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = PlaybackSettings(this)
        val playerIds = shared.playerIds
        var settings = playerIds.firstOrNull()?.let { shared.player(it) }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, padding / 4, 0, padding / 4)
            content.addView(this)
        }
        label("Setting", 24f)
        val serverLabel = label("UPnP server URL (shared by all players)")
        val server = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(shared.upnpServer)
            contentDescription = "UPnP server URL"
        }
        serverLabel.labelFor = server.id
        content.addView(server)
        label("Set the server once for all players. Paste a supported Stash page URL into the remote’s video URL field and press Play.", 16f)
        content.addView(Button(this).apply {
            text = "Save shared server"
            setOnClickListener {
                shared.upnpServer = server.text.toString().trim()
                finish()
            }
        })
        label("Playlist cache", 20f)
        val hoursLabel = label("Cache duration (hours)")
        val hours = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(shared.playlistCacheHours.toString())
            contentDescription = "Playlist cache duration in hours"
        }
        hoursLabel.labelFor = hours.id
        content.addView(hours)
        label("Reuse downloaded playlists across all players. 0 disables caching. Video streams are not cached.", 16f)
        content.addView(Button(this).apply {
            text = "Save cache duration"
            setOnClickListener {
                shared.playlistCacheHours = hours.text.toString().toInt()
                finish()
            }
        })
        val cacheStatus = label("", 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(Button(this).apply {
            text = "Clear playlist cache"
            setOnClickListener {
                scope.launch {
                    PlaylistCache(this@SettingsActivity).clear()
                    cacheStatus.text = "Playlist cache cleared. Saved settings and current playback are unchanged."
                }
            }
        })
        label("Player buffer settings", 20f)
        if (playerIds.isEmpty()) label("Add a player from the remote to configure playback settings.", 16f)
        val playerSelector = Spinner(this).apply {
            contentDescription = "Player"
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                playerIds.map { shared.player(it).name })
        }
        content.addView(playerSelector)
        val cacheLabel = label("Resume buffer after a stall (milliseconds)")
        val cache = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(settings?.networkCacheMs?.toString().orEmpty())
            contentDescription = "Resume buffer after a stall in milliseconds"
        }
        cacheLabel.labelFor = cache.id
        content.addView(cache)
        label("Wait for this much buffered video after a delivery stall. Restart PippyPlayer's service to apply changes. Forward buffering targets 10–20 seconds within a 16 MiB sample budget; the next video preloads up to 3 seconds.", 16f)
        playerSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                settings = shared.player(playerIds[position])
                cache.setText(settings?.networkCacheMs?.toString().orEmpty())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        content.addView(Button(this).apply {
            text = "Save player buffer"
            isEnabled = playerIds.isNotEmpty()
            setOnClickListener {
                shared.player(playerIds[playerSelector.selectedItemPosition]).saveNetworkCacheMs(cache.text.toString().toInt())
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(content) })
        server.requestFocus()
    }
}
