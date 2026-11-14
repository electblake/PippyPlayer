package app.pippyplayer.tv

import android.content.Intent
import androidx.core.graphics.createBitmap
import android.net.ConnectivityManager
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
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
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9AF3C5))) {
                val buttonColors = ButtonDefaults.colors(
                    containerColor = Color(0xFF26373F), contentColor = Color(0xFFEEF5F3),
                    focusedContainerColor = Color(0xFF9AF3C5), focusedContentColor = Color(0xFF101820),
                    pressedContainerColor = Color(0xFF5DDDA0), pressedContentColor = Color(0xFF101820),
                    disabledContainerColor = Color(0xFF1A252C), disabledContentColor = Color(0xFF829096))
                val buttonBorder = ButtonDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, Color.White)))
                Row(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(44.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("PIPPYPLAYER", color = Color(0xFF9AF3C5), fontSize = 18.sp)
                        Text("A little window.\nA bigger picture.", color = Color.White, fontSize = 34.sp)
                        Text("Play a video beside whateverâ€™s on TV. Control it from your phone.", color = Color(0xFFBAC8CD), fontSize = 18.sp)
                        if (!allowed) {
                            Button(colors = buttonColors, border = buttonBorder, onClick = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())) }) {
                                Text("Allow display over other apps")
                            }
                        }
                        Button(colors = buttonColors, border = buttonBorder, enabled = allowed && !PlayerState.running, onClick = {
                            startForegroundService(Intent(this@MainActivity, PlayerService::class.java))
                        }) { Text("Start PippyPlayer") }
                        Button(colors = buttonColors, border = buttonBorder, enabled = PlayerState.running, onClick = {
                            stopService(Intent(this@MainActivity, PlayerService::class.java))
                        }) { Text("Stop PippyPlayer") }
                        Button(colors = buttonColors, border = buttonBorder, enabled = PlayerState.running, onClick = {
                            startService(Intent(this@MainActivity, PlayerService::class.java)
                                .putExtra("debugEnabled", !PlayerState.debugEnabled))
                        }) { Text(if (PlayerState.debugEnabled) "Debug info: On" else "Debug info: Off") }
                        Button(colors = buttonColors, border = buttonBorder, onClick = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }) { Text("Playback settings") }
                        Text("After choosing a video, press Home and open another app. Stop PippyPlayer to close the controller and video.", color = Color(0xFFBAC8CD), fontSize = 14.sp)
                    }
                    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(colors = buttonColors, border = buttonBorder, onClick = {
                            startActivity(Intent(this@MainActivity, UpdateActivity::class.java))
                        }) { Text("App updates") }
                        if (PlayerState.running) {
                            val origin = if (intent.hasExtra("controller_origin")) controllerOrigin
                                else "$controllerOrigin:${PlayerState.controllerPort}"
                            val url = "$origin/${PlayerState.token}"
                            val qr = remember(url) {
                                val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 420, 420, mapOf(EncodeHintType.MARGIN to 2))
                                createBitmap(420, 420).apply {
                                    setPixels(IntArray(420 * 420) { i -> if (matrix[i % 420, i / 420]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 420, 0, 0, 420, 420)
                                }.asImageBitmap()
                            }
                            Image(qr, "Scan to open your PippyPlayer controller", Modifier.size(230.dp))
                            Text("Scan. Choose. Play.", color = Color.White, fontSize = 22.sp)
                            Text("Connect your phone to the same home network as this TV.", color = Color(0xFFBAC8CD), fontSize = 14.sp)
                            Text(url, color = Color(0xFF9AF3C5), fontSize = 12.sp)
                        } else {
                            Text("Your phone is the remote.", color = Color.White, fontSize = 26.sp)
                            Text("Start PippyPlayer to show your private controller QR code. No companion app needed.", color = Color(0xFFBAC8CD), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
