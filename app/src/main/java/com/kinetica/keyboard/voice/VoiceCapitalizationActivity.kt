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
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Editable exceptions to immediate mid-sentence lowercasing. Save applies next session. */
class VoiceCapitalizationActivity : Activity() {
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.voice_protected_capitals)
        val prefs = getSharedPreferences("voice", MODE_PRIVATE)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.voice_protected_capitals_hint, K.VOICE_PROTECTED_WORDS_MAX_CHARS)
        })
        editor = EditText(this).apply {
            hint = getString(R.string.voice_protected_capitals)
            contentDescription = hint
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 8
            maxLines = 14
            setText(savedInstanceState?.getString("draft") ?: prefs.getString(
                FastVoiceFormatting.PROTECTED_WORDS_PREFERENCE, FastVoiceFormatting.DEFAULT_PROTECTED_WORDS))
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(this).apply {
            setText(R.string.voice_restore_instructions)
            setOnClickListener { editor.setText(FastVoiceFormatting.DEFAULT_PROTECTED_WORDS); editor.error = null }
        })
        content.addView(Button(this).apply {
            setText(R.string.voice_save_instructions)
            setOnClickListener {
                val words = editor.text.toString().trim()
                if (words.length > K.VOICE_PROTECTED_WORDS_MAX_CHARS) {
                    editor.error = getString(R.string.voice_protected_capitals_invalid, K.VOICE_PROTECTED_WORDS_MAX_CHARS)
                    return@setOnClickListener
                }
                // An explicitly empty list is meaningful; only an absent preference uses defaults.
                prefs.edit().putString(FastVoiceFormatting.PROTECTED_WORDS_PREFERENCE, words).apply()
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
