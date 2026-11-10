package app.pippyplayer.tv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var status: TextView
    private lateinit var notes: TextView
    private lateinit var check: Button
    private lateinit var download: Button
    private lateinit var permission: Button
    private lateinit var progress: ProgressBar
    private lateinit var installedVersion: String
    private var release: AppRelease? = null
    private var busy = false
    private var newer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installedVersion = packageManager.getPackageInfo(packageName, 0).versionName!!
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
        label(getString(R.string.updates_title), 28f)
        label(getString(R.string.updates_installed, installedVersion))
        label(getString(R.string.updates_explanation), 16f)
        status = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        check = Button(this).apply {
            setText(R.string.updates_check)
            setOnClickListener { checkForUpdates() }
        }
        content.addView(check)
        permission = Button(this).apply {
            setText(R.string.updates_permission)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
            }
        }
        content.addView(permission)
        download = Button(this).apply {
            setText(R.string.updates_download)
            setOnClickListener { downloadUpdate() }
        }
        content.addView(download)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        content.addView(progress)
        notes = label("", 16f)
        setContentView(ScrollView(this).apply { addView(content) })
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    private fun updateButtons() {
        val allowed = packageManager.canRequestPackageInstalls()
        check.isEnabled = !busy
        permission.visibility = if (newer && !allowed) View.VISIBLE else View.GONE
        permission.isEnabled = !busy
        download.visibility = if (newer) View.VISIBLE else View.GONE
        download.isEnabled = newer && allowed && !busy
    }

    private fun checkForUpdates() {
        busy = true
        newer = false
        updateButtons()
        status.setText(R.string.updates_checking)
        notes.text = ""
        scope.launch {
            val latest = withContext(Dispatchers.IO) { GitHubUpdates.latest() }
            release = latest
            newer = ReleaseVersion.parse(latest.tag) > ReleaseVersion.parse(installedVersion)
            status.text = if (newer) getString(R.string.updates_available, latest.tag)
                else getString(R.string.updates_current, installedVersion, latest.tag)
            notes.text = latest.notes
            busy = false
            updateButtons()
            if (newer) {
                if (packageManager.canRequestPackageInstalls()) download.requestFocus() else permission.requestFocus()
            } else check.requestFocus()
        }
    }

    private fun downloadUpdate() {
        val latest = release!!
        busy = true
        updateButtons()
        progress.progress = 0
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.updates_downloading, 0)
        scope.launch {
            val apk = withContext(Dispatchers.IO) {
                val directory = File(filesDir, "updates")
                java.nio.file.Files.createDirectories(directory.toPath())
                val file = File(directory, "pippyplayer.apk")
                GitHubUpdates.download(latest, file) { percent ->
                    withContext(Dispatchers.Main) {
                        progress.progress = percent
                        status.text = getString(R.string.updates_downloading, percent)
                    }
                }
                file
            }
            val uri = FileProvider.getUriForFile(this@UpdateActivity, "$packageName.updates", apk)
            status.setText(R.string.updates_installing)
            busy = false
            updateButtons()
            stopService(Intent(this@UpdateActivity, PlayerService::class.java))
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class UpdateFileProvider : FileProvider()
