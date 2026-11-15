package app.pippyplayer.tv

import android.content.Intent
import androidx.core.graphics.createBitmap
import android.net.ConnectivityManager
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.io.File

class MainActivity : ComponentActivity() {
    private var allowed by mutableStateOf(false)
    private var controllerOrigin by mutableStateOf("")
    override fun onResume() {
        super.onResume()
        allowed = Settings.canDrawOverlays(this)
        if (intent.hasExtra("controller_origin")) {
            controllerOrigin = intent.getStringExtra("controller_origin")!!
        } else {
            val network = getSystemService(ConnectivityManager::class.java)
            val address = network.getLinkProperties(network.activeNetwork)!!.linkAddresses
                .first { it.address is Inet4Address }.address.hostAddress!!
            controllerOrigin = "http://$address"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (File(filesDir, PippyPlayerApplication.CRASH_REPORT).exists()) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        setContent {
            AndroidView(factory = { createContent() }, update = {
                permission.visibility = if (allowed) View.GONE else View.VISIBLE
                start.isEnabled = allowed && !PlayerState.running
                stop.isEnabled = PlayerState.running
                debug.isEnabled = PlayerState.running
                debug.text = if (PlayerState.debugEnabled) "Debug info: On" else "Debug info: Off"
                val origin = if (intent.hasExtra("controller_origin")) controllerOrigin
                    else "$controllerOrigin:${PlayerState.controllerPort}"
                val url = "$origin/${PlayerState.token}"
                qr.visibility = if (PlayerState.running) View.VISIBLE else View.GONE
                address.visibility = if (PlayerState.running) View.VISIBLE else View.GONE
                instructions.text = if (PlayerState.running) "Scan to open the remote. Connect your phone to the same network as this TV."
                    else "Start PippyPlayer to show the remote’s QR code."
                if (PlayerState.running && address.text.toString() != url) {
                    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 420, 420, mapOf(EncodeHintType.MARGIN to 2))
                    qr.setImageBitmap(createBitmap(420, 420).apply {
                        setPixels(IntArray(420 * 420) { i -> if (matrix[i % 420, i / 420]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 420, 0, 0, 420, 420)
                    })
                    address.text = url
                }
            })
        }
    }

    private lateinit var permission: Button
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var debug: Button
    private lateinit var qr: ImageView
    private lateinit var address: TextView
    private lateinit var instructions: TextView

    private fun createContent(): View {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val remote = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, 0, 0)
        }
        content.addView(actions, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(remote, LinearLayout.LayoutParams(0, -2, 1f))
        fun label(parent: LinearLayout, value: String, size: Float = 16f) = TextView(this).apply {
            text = value
            textSize = size
            setPadding(0, 0, 0, padding / 3)
            parent.addView(this)
        }
        fun action(value: String, click: () -> Unit) = Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { click() }
            actions.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label(actions, "PippyPlayer", 24f)
        label(actions, "Play video over other apps. Control playback from your phone.")
        permission = action("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        }
        start = action("Start PippyPlayer") {
            startForegroundService(Intent(this, PlayerService::class.java))
        }
        stop = action("Stop PippyPlayer") {
            stopService(Intent(this, PlayerService::class.java))
        }
        debug = action("Debug info: Off") {
            startService(Intent(this, PlayerService::class.java).putExtra("debugEnabled", !PlayerState.debugEnabled))
        }
        action("App updates") { startActivity(Intent(this, UpdateActivity::class.java)) }
        action("Setting") { startActivity(Intent(this, SettingsActivity::class.java)) }
        label(remote, "Remote control", 20f)
        instructions = label(remote, "")
        qr = ImageView(this).apply {
            contentDescription = "Scan to open your PippyPlayer controller"
            adjustViewBounds = true
            val size = (200 * resources.displayMetrics.density).toInt()
            remote.addView(this, LinearLayout.LayoutParams(size, size))
        }
        address = label(remote, "", 14f).apply { setTextIsSelectable(true) }
        label(remote, "After choosing a video, press Home and open another app. Stop PippyPlayer to close the controller and video.", 14f)
        return ScrollView(this).apply { addView(content) }
    }
}
