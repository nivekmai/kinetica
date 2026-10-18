package com.kinetica.keyboard.voice

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kinetica.keyboard.R

/** Explicit Save keeps preset previews and unsaved edits out of live dictation. */
class CleanupInstructionsActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var bundled: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.voice_instructions)
        bundled = assets.open("voice_cleanup_prompt.txt").bufferedReader().use { it.readText() }.trim()
        val prefs = getSharedPreferences("voice", MODE_PRIVATE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.voice_instructions_hint, CleanupInstructions.MAX_CHARS)
        })
        val presets = LinearLayout(this)
        fun preset(label: Int, text: String) {
            presets.addView(Button(this).apply {
                setText(label); isAllCaps = false
                setOnClickListener { editor.setText(text); editor.error = null }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        preset(R.string.voice_preset_light, CleanupInstructions.light)
        preset(R.string.voice_preset_clean, CleanupInstructions.clean)
        preset(R.string.voice_preset_structured, CleanupInstructions.structured)
        content.addView(presets)
        editor = EditText(this).apply {
            hint = getString(R.string.voice_instructions)
            contentDescription = hint
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 8
            maxLines = 14
            setText(savedInstanceState?.getString("draft") ?: CleanupInstructions.effective(
                prefs.getString(CleanupInstructions.PREFERENCE, null), bundled))
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(this).apply {
            setText(R.string.voice_restore_instructions)
            setOnClickListener { editor.setText(bundled); editor.error = null }
        })
        content.addView(Button(this).apply {
            setText(R.string.voice_save_instructions)
            setOnClickListener {
                val text = editor.text.toString().trim()
                if (!CleanupInstructions.valid(text)) {
                    editor.error = getString(R.string.voice_instructions_invalid, CleanupInstructions.MAX_CHARS)
                    return@setOnClickListener
                }
                LocalCleanup.cancel()
                prefs.edit().apply {
                    if (text == bundled) remove(CleanupInstructions.PREFERENCE)
                    else putString(CleanupInstructions.PREFERENCE, text)
                }.apply()
                android.widget.Toast.makeText(this@CleanupInstructionsActivity,
                    R.string.voice_instructions_saved, android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        content.addView(Button(this).apply {
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", editor.text.toString())
        super.onSaveInstanceState(outState)
    }
}
