package app.pippyplayer.tv

import android.app.Activity
import android.graphics.Color
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

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var settings = PlaybackSettings(this)
        val playerIds = settings.playerIds
        val padding = (32 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        fun label(value: String, size: Float = 18f) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, padding / 4, 0, padding / 4)
            content.addView(this)
        }
        label("Playback settings", 28f)
        val serverLabel = label("UPnP server URL (shared by all players)")
        val server = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(settings.upnpServer)
            contentDescription = "UPnP server URL"
        }
        serverLabel.labelFor = server.id
        content.addView(server)
        label("Set the server once for all players. Paste a supported Stash page URL into the remote’s video URL field and press Play.", 16f)
        content.addView(Button(this).apply {
            text = "Save shared server"
            setOnClickListener {
                settings.upnpServer = server.text.toString().trim()
                finish()
            }
        })
        label("Player buffer settings", 24f)
        if (playerIds.isEmpty()) label("Add a player from the remote to configure playback settings.", 16f)
        val playerSelector = Spinner(this).apply {
            contentDescription = "Player"
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item,
                playerIds.map { "Player $it" })
        }
        content.addView(playerSelector)
        val cacheLabel = label("Resume buffer after a stall (milliseconds)")
        val cache = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(settings.networkCacheMs.toString())
            contentDescription = "Resume buffer after a stall in milliseconds"
        }
        cacheLabel.labelFor = cache.id
        content.addView(cache)
        label("Wait for this much buffered video after a delivery stall. Restart PippyPlayer's service to apply changes. Forward buffering targets 10–20 seconds within a 16 MiB sample budget; the next video preloads up to 3 seconds.", 16f)
        playerSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                settings = PlaybackSettings(this@SettingsActivity, playerIds[position])
                cache.setText(settings.networkCacheMs.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        content.addView(Button(this).apply {
            text = "Save player buffer"
            isEnabled = playerIds.isNotEmpty()
            setOnClickListener {
                settings.saveNetworkCacheMs(cache.text.toString().toInt())
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(content) })
        server.requestFocus()
    }
}
