package com.kinetica.keyboard.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.view.View
import com.kinetica.keyboard.R

/** Permission and explicitly requested model downloads live outside the keyboard panel. */
class VoiceSetupActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var permission: Button
    private lateinit var wifi: Switch
    private lateinit var importButton: Button
    private lateinit var removeButton: Button
    private lateinit var cancelButton: Button
    private val modelButtons = mutableListOf<Pair<VoiceModel, Button>>()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.voice_setup)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun label(resource: Int) { content.addView(TextView(this).apply { setText(resource) }) }
        fun button(resource: Int, action: () -> Unit): Button = Button(this).apply {
            setText(resource); isAllCaps = false; setOnClickListener { action() }; content.addView(this)
        }
        button(R.string.voice_diagnostics) { startActivity(Intent(this, VoiceDiagnosticsActivity::class.java)) }
        label(R.string.voice_shortcuts_hint)
        button(R.string.voice_instructions) {
            startActivity(Intent(this, CleanupInstructionsActivity::class.java))
        }
        label(R.string.voice_punctuation_engine)
        val voicePrefs = getSharedPreferences("voice", MODE_PRIVATE)
        val selectedPunctuation = voicePrefs.getString(PunctuationText.PREFERENCE, null)
            ?: if (PunctuationBackend.bundled) PunctuationText.SMALL else
                if (voicePrefs.getBoolean("android_formatting", true)) PunctuationText.ANDROID else PunctuationText.OFF
        content.addView(RadioGroup(this).apply {
            for ((mode, title) in listOf(PunctuationText.SMALL to R.string.voice_punctuation_small,
                PunctuationText.ANDROID to R.string.voice_android_formatting, PunctuationText.OFF to R.string.voice_punctuation_none)) {
                addView(RadioButton(this@VoiceSetupActivity).apply {
                    id = View.generateViewId(); tag = mode; setText(title)
                    isEnabled = when (mode) {
                        PunctuationText.SMALL -> PunctuationBackend.bundled
                        PunctuationText.ANDROID -> android.os.Build.VERSION.SDK_INT >= 33
                        else -> true
                    }
                    isChecked = mode == selectedPunctuation
                })
            }
            setOnCheckedChangeListener { group, id ->
                val mode = group.findViewById<RadioButton>(id)?.tag as? String
                if (mode != null) voicePrefs.edit().putString(PunctuationText.PREFERENCE, mode).apply()
            }
        })
        label(if (PunctuationBackend.bundled) R.string.voice_punctuation_hint else R.string.voice_punctuation_not_built)
        content.addView(Switch(this).apply {
            setText(R.string.voice_fast_formatting)
            isChecked = voicePrefs.getBoolean(FastVoiceFormatting.PREFERENCE, true)
            setOnCheckedChangeListener { _, enabled ->
                voicePrefs.edit().putBoolean(FastVoiceFormatting.PREFERENCE, enabled).apply()
            }
        })
        label(R.string.voice_fast_hint)
        button(R.string.voice_protected_capitals) {
            startActivity(Intent(this, VoiceCapitalizationActivity::class.java))
        }
        button(R.string.chord_settings_title) {
            startActivity(Intent(this, com.kinetica.keyboard.settings.ChordSettingsActivity::class.java))
        }
        button(R.string.edge_swipe_settings_title) {
            startActivity(Intent(this, com.kinetica.keyboard.settings.EdgeSwipeSettingsActivity::class.java))
        }
        content.addView(Switch(this).apply {
            setText(R.string.voice_continuous_audio)
            isEnabled = android.os.Build.VERSION.SDK_INT >= 33
            isChecked = isEnabled && voicePrefs.getBoolean("continuous_audio", true)
            setOnCheckedChangeListener { _, enabled -> voicePrefs.edit().putBoolean("continuous_audio", enabled).apply() }
        })
        label(R.string.voice_continuous_hint)
        label(R.string.voice_setup_explanation)
        permission = button(R.string.voice_grant_permission) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        button(R.string.voice_app_settings) {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        status = TextView(this).also { content.addView(it) }
        wifi = Switch(this).apply {
            setText(R.string.voice_wifi_only)
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("wifi", true)
            setOnCheckedChangeListener { _, checked -> getPreferences(MODE_PRIVATE).edit().putBoolean("wifi", checked).apply() }
        }
        content.addView(wifi)
        label(R.string.voice_model_storage)
        VoiceModels.all.forEachIndexed { index, model ->
            content.addView(TextView(this).apply { text = model.name; textSize = 20f })
            label(if (index == 0) R.string.voice_model_large else R.string.voice_model_small)
            val download = button(R.string.voice_download) {
                if (VoiceModelStore.file(this, model).isFile) VoiceModelStore.select(this, model)
                else VoiceModelStore.download(this, model, wifi.isChecked)
                refresh()
            }
            modelButtons.add(model to download)
            button(R.string.voice_model_license) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.page))) }
                catch (_: android.content.ActivityNotFoundException) { }
            }
        }
        cancelButton = button(R.string.voice_cancel_download) { VoiceModelStore.cancel(this); refresh() }
        importButton = button(R.string.voice_import_model) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, 2)
        }
        removeButton = button(R.string.voice_remove_model) {
            VoiceModelStore.removeActive(this)
            refresh()
        }
        button(R.string.voice_done) { finish() }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun refresh() {
        permission.isEnabled = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        val progress = VoiceModelStore.progress(this)
        val busy = VoiceModelStore.busy(this)
        val active = VoiceModelStore.active(this)
        status.text = if (!LocalCleanup.available) getString(R.string.voice_llm_not_built)
            else progress ?: getString(if (active.isFile) R.string.voice_model_ready else R.string.voice_model_needed)
        for ((model, button) in modelButtons) {
            val file = VoiceModelStore.file(this, model)
            val selected = file.isFile && file == active
            button.setText(when {
                selected -> R.string.voice_model_selected
                file.isFile -> R.string.voice_use_model
                else -> R.string.voice_download
            })
            button.isEnabled = LocalCleanup.available && !busy && !selected
        }
        importButton.isEnabled = LocalCleanup.available && !busy
        removeButton.isEnabled = active.isFile && !busy
        cancelButton.isEnabled = busy
        wifi.isEnabled = !busy
    }

    override fun onResume() { super.onResume(); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (requestCode == 1 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED)
            status.setText(R.string.voice_permission_denied)
    }

    @Deprecated("Uses the platform document picker for a local copy")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2 || resultCode != RESULT_OK) return
        data?.data?.let { VoiceModelStore.import(this, it) }
        refresh()
    }
}
