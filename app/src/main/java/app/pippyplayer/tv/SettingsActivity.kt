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

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = PlaybackSettings(this)
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
        val serverLabel = label("UPnP server URL")
        val server = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(settings.upnpServer)
            contentDescription = "UPnP server URL"
        }
        serverLabel.labelFor = server.id
        content.addView(server)
        label("Set your server once. Paste a supported Stash page URL into the remote’s video URL field and press Play.", 16f)
        val cacheLabel = label("Network buffer cache (milliseconds)")
        val cache = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(settings.networkCacheMs.toString())
            contentDescription = "Network buffer cache in milliseconds"
        }
        cacheLabel.labelFor = cache.id
        content.addView(cache)
        label("A larger cache absorbs short network stalls but increases startup time. Changes apply when you next load a video or playlist item.", 16f)
        content.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener {
                settings.save(server.text.toString().trim(), cache.text.toString().toInt())
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(content) })
        server.requestFocus()
    }
}
