package app.pippyplayer.tv

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reportFile = File(filesDir, PippyPlayerApplication.CRASH_REPORT)
        val report = reportFile.readText()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        content.addView(TextView(this).apply {
            setText(R.string.crash_title)
            textSize = 26f
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            setText(R.string.crash_description)
            textSize = 16f
            setTextColor(Color.LTGRAY)
        })
        val actions = LinearLayout(this)
        val copy = Button(this).apply {
            setText(R.string.crash_copy)
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("PippyPlayer crash report", report))
                setText(R.string.crash_copied)
            }
        }
        actions.addView(copy)
        actions.addView(Button(this).apply {
            setText(R.string.updates_title)
            setOnClickListener {
                startActivity(Intent(this@CrashActivity, UpdateActivity::class.java))
            }
        })
        actions.addView(Button(this).apply {
            setText(R.string.crash_reopen)
            setOnClickListener {
                java.nio.file.Files.delete(reportFile.toPath())
                startActivity(Intent(this@CrashActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        content.addView(actions)
        val scroll = ScrollView(this).apply {
            isFocusable = true
            addView(TextView(this@CrashActivity).apply {
                text = report
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                setPadding(0, padding / 2, 0, padding / 2)
            })
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
        copy.requestFocus()
    }
}
