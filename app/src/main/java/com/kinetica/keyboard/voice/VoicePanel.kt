package com.kinetica.keyboard.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.kinetica.keyboard.R
import com.kinetica.keyboard.ui.KeyboardTheme

/** Independent recording/cleanup labels. Details are persistent and directly clickable. */
class VoicePanel(context: Context) : LinearLayout(context) {
    val record = icon(R.drawable.ic_voice_pause, R.string.voice_pause)
    val auto = icon(R.drawable.ic_voice_cleanup, R.string.voice_auto)
    val undo = icon(R.drawable.ic_voice_undo, R.string.voice_undo)
    val setup = icon(R.drawable.ic_voice_settings, R.string.voice_models)
    private val statusCell = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
    private val micLabel = TextView(context).apply { textSize = 11f; gravity = Gravity.CENTER }
    private val cleanupLabel = TextView(context).apply { textSize = 11f; gravity = Gravity.CENTER }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; visibility = GONE }
    private var status = R.string.voice_starting
    private var detail = R.string.voice_live_hint
    private var punctuationStatus: Int? = null
    private var captureMode = R.string.voice_streaming_mode
    private var recording = false
    private var extra = ""
    private var diagnostic = ""
    private var popup: PopupWindow? = null
    private var detailText: TextView? = null
    private var retryButton: Button? = null
    private var stopButton: Button? = null
    private var markerButton: Button? = null
    private var bg = 0xff202020.toInt()
    private var fg = 0xffffffff.toInt()
    var automatic = true
        private set
    var onAutomaticChanged: ((Boolean) -> Unit)? = null
    var onRetry: (() -> Unit)? = null
    var onStopDiagnostic: (() -> Unit)? = null
    var onMarkDiagnostic: (() -> Unit)? = null
    var canRetry = false
        set(value) { field = value; retryButton?.isEnabled = value }
    var diagnosticActive = false
        set(value) { field = value; stopButton?.isEnabled = value; markerButton?.isEnabled = value }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        statusCell.addView(micLabel); statusCell.addView(cleanupLabel)
        statusCell.addView(progress, LayoutParams(-1, (3 * resources.displayMetrics.density).toInt()))
        addView(statusCell, LayoutParams(0, -1, 3f))
        listOf(record, auto, undo, setup).forEach { addView(it, LayoutParams(0, -1, 1f)) }
        statusCell.setOnClickListener { showDetails() }
        statusCell.isFocusable = true
        auto.setOnClickListener { setAutomatic(!automatic) }
        refresh()
    }
    fun setAutomatic(enabled: Boolean, notify: Boolean = true) {
        automatic = enabled; auto.alpha = if (enabled) 1f else 0.4f; auto.isSelected = enabled
        auto.contentDescription = context.getString(if (enabled) R.string.voice_auto_on else R.string.voice_auto_off)
        auto.tooltipText = auto.contentDescription
        if (notify) onAutomaticChanged?.invoke(enabled)
        refresh()
    }
    fun setCaptureMode(streaming: Boolean) { captureMode = if (streaming) R.string.voice_streaming_mode else R.string.voice_legacy_mode; refresh() }
    fun setStatus(message: Int, alert: Boolean = false) { status = message; refresh(); if (alert) showDetails() }
    fun setDetail(message: Int, explanation: String = "") { detail = message; extra = explanation; refresh() }
    fun setPunctuation(message: Int?) { punctuationStatus = message; refresh() }
    fun setDiagnostic(message: String, active: Boolean) { diagnostic = message; diagnosticActive = active; refresh() }
    fun setRecording(armed: Boolean) {
        recording = armed
        record.setImageResource(if (armed) R.drawable.ic_voice_pause else R.drawable.ic_voice_play)
        record.contentDescription = context.getString(if (armed) R.string.voice_pause else R.string.voice_resume)
        record.tooltipText = record.contentDescription
        refresh()
    }
    private fun description() = listOf(context.getString(status), context.getString(captureMode),
        context.getString(detail), punctuationStatus?.let { context.getString(it) }.orEmpty(),
        extra, diagnostic).filter { it.isNotBlank() }.joinToString("\n\n")
    private fun refresh() {
        micLabel.text = context.getString(when {
            status == R.string.voice_listening -> R.string.voice_mic_listening
            !recording -> R.string.voice_mic_paused
            status == R.string.voice_starting -> R.string.voice_mic_starting
            else -> R.string.voice_mic_finishing
        }) + if (diagnosticActive) " • REC" else ""
        val aiBusy = automatic && detail == R.string.voice_cleaning
        val punctBusy = punctuationStatus == R.string.voice_punctuation_working
        cleanupLabel.setText(if (punctBusy && !aiBusy) R.string.voice_punctuation_working
            else if (!automatic) punctuationStatus ?: R.string.voice_cleanup_off else when (detail) {
            R.string.voice_cleaning -> R.string.voice_ai_working
            R.string.voice_cleanup_queued -> R.string.voice_ai_waiting
            R.string.voice_cleaned -> R.string.voice_progress_applied
            R.string.voice_already_clean -> R.string.voice_no_changes
            R.string.voice_cleanup_timed_out -> R.string.voice_short_timeout
            R.string.voice_cleanup_too_long -> R.string.voice_short_limit
            R.string.voice_model_needed -> R.string.voice_progress_model
            R.string.voice_cleanup_failed, R.string.voice_cleanup_rejected -> R.string.voice_short_failed
            else -> R.string.voice_cleanup_ready
        })
        progress.visibility = if (aiBusy || punctBusy) VISIBLE else GONE
        statusCell.contentDescription = description() + ". " + context.getString(R.string.voice_details)
        detailText?.text = description()
    }
    private fun showDetails() {
        if (windowToken == null || popup?.isShowing == true) return
        val body = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(20, 16, 20, 16); setBackgroundColor(bg)
        }
        detailText = TextView(context).apply { text = description(); setTextColor(fg); textSize = 14f }
        body.addView(detailText)
        fun button(label: Int, action: () -> Unit) = Button(context).apply {
            setText(label); isAllCaps = false; setOnClickListener { action() }; body.addView(this)
        }
        retryButton = button(R.string.voice_retry_cleanup) { onRetry?.invoke() }.apply { isEnabled = canRetry }
        markerButton = button(R.string.voice_mark_problem) { onMarkDiagnostic?.invoke() }.apply { isEnabled = diagnosticActive }
        stopButton = button(R.string.voice_stop_diagnostic) { onStopDiagnostic?.invoke() }.apply { isEnabled = diagnosticActive }
        button(R.string.voice_close_details) { popup?.dismiss() }
        val scroll = ScrollView(context).apply { addView(body) }
        popup = PopupWindow(scroll, width.coerceAtLeast(300), (300 * resources.displayMetrics.density).toInt(), false).apply {
            setBackgroundDrawable(ColorDrawable(bg)); isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener { detailText = null; retryButton = null; stopButton = null; markerButton = null; popup = null }
            // Owned by the IME window, so opening details never changes editor focus.
            showAsDropDown(this@VoicePanel, 0, -height - this@VoicePanel.height)
        }
    }
    override fun onDetachedFromWindow() { popup?.dismiss(); super.onDetachedFromWindow() }
    fun applyTheme(theme: KeyboardTheme) {
        bg = theme.suggestionBg; fg = theme.keyText; setBackgroundColor(bg)
        micLabel.setTextColor(fg); cleanupLabel.setTextColor(fg)
        progress.indeterminateTintList = ColorStateList.valueOf(fg)
        listOf(record, auto, undo, setup).forEach { it.imageTintList = ColorStateList.valueOf(fg) }
    }
    private fun icon(drawable: Int, label: Int) = ImageButton(context).apply {
        setImageResource(drawable); contentDescription = context.getString(label); tooltipText = contentDescription
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val padding = (8 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding)
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = attrs.getDrawable(0); attrs.recycle()
    }
}
