package com.kinetica.keyboard.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.kinetica.keyboard.R
import java.io.File

/** User-controlled capture/export. SAF can save the self-contained ZIP directly into Drive. */
class VoiceDiagnosticsActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var selected: File? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val finishPoll = object : Runnable {
        override fun run() { if (VoiceDiagnostics.busy()) handler.postDelayed(this, 1000) else refresh() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.voice_diagnostics)
        val padding = (20 * resources.displayMetrics.density).toInt()
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding) }
        setContentView(ScrollView(this).apply { addView(content) })
        selected = savedInstanceState?.getString("selected")?.let { name ->
            VoiceDiagnostics.sessions(this).firstOrNull { it.name == name }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("selected", selected?.name); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); refresh(); if (VoiceDiagnostics.busy()) handler.postDelayed(finishPoll, 1000) }
    override fun onPause() { handler.removeCallbacks(finishPoll); super.onPause() }
    private fun refresh() {
        content.removeAllViews()
        content.addView(TextView(this).apply { setText(R.string.voice_diagnostic_explanation) })
        content.addView(Switch(this).apply {
            setText(R.string.voice_arm_diagnostic)
            val prefs = getSharedPreferences("voice", MODE_PRIVATE)
            isChecked = prefs.getBoolean(VoiceDiagnostics.ARM, false)
            setOnCheckedChangeListener { _, enabled -> prefs.edit().putBoolean(VoiceDiagnostics.ARM, enabled).apply() }
        })
        status = TextView(this).apply { setText(if (VoiceDiagnostics.busy()) R.string.voice_diagnostic_busy else R.string.voice_diagnostic_available) }
        content.addView(status)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }; content.addView(this)
        }
        VoiceDiagnostics.sessions(this).forEach { file ->
            val ready = File(file, "manifest.json").isFile
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.lastModified()))
            button(getString(R.string.voice_export_diagnostic, date)) {
                selected = file
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, "kinetica-" + file.name + ".zip")
                }, 10)
            }.isEnabled = ready
        }
        button(getString(R.string.voice_delete_diagnostics)) { VoiceDiagnostics.delete(this); refresh() }.isEnabled = !VoiceDiagnostics.busy()
        button(getString(R.string.voice_done)) { finish() }
    }
    @Deprecated("Platform document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        val file = selected ?: return
        val uri = data?.data ?: return
        status.setText(R.string.voice_diagnostic_exporting)
        Thread {
            val success = runCatching {
                check(File(file, "manifest.json").isFile)
                contentResolver.openOutputStream(uri, "wt")!!.use { VoiceDiagnostics.export(file, it) }
            }.isSuccess
            runOnUiThread { if (!isFinishing && !isDestroyed) status.setText(if (success) R.string.voice_diagnostic_exported else R.string.voice_diagnostic_export_failed) }
        }.start()
    }
}
