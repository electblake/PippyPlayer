package app.pippyplayer.tv

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val history = DiagnosticLog.read(this)
        val previousRun = history.substringBeforeLast(" APP START", "").substringBeforeLast('\n', "")
        val padding = (12 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(this).apply {
            text = "Diagnostic log — last 40 lines before restart"
            textSize = 18f
        })
        val report = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            text = previousRun.lineSequence().toList().takeLast(40).joinToString("\n")
        }
        val actions = LinearLayout(this)
        actions.addView(Button(this).apply {
            text = "Before restart"
            setOnClickListener { report.text = previousRun.lineSequence().toList().takeLast(40).joinToString("\n") }
        })
        actions.addView(Button(this).apply {
            text = "Latest events"
            setOnClickListener { report.text = DiagnosticLog.read(this@DiagnosticActivity).lineSequence().toList().takeLast(40).joinToString("\n") }
        })
        actions.addView(Button(this).apply {
            text = "Full history"
            setOnClickListener { report.text = DiagnosticLog.read(this@DiagnosticActivity) }
        })
        content.addView(actions)
        content.addView(ScrollView(this).apply { isFocusable = true; addView(report) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
    }
}
