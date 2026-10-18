package com.kinetica.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Editor-bound dictation: continuous PCM segments on Android 13+, legacy utterances as fallback. */
class VoiceSession(
    private val context: Context,
    val panel: VoicePanel,
    private val language: String,
    private val update: (String, String) -> Boolean,
    private val diagnostic: VoiceDiagnostics? = null,
    private val close: () -> Unit,
    precedingText: String = "",
) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    // Capture the setting once, including across compatibility-mode restarts.
    private val punctuationMode = prefs.getString(PunctuationText.PREFERENCE, null)
        ?: if (PunctuationBackend.bundled) PunctuationText.SMALL else
            if (prefs.getBoolean("android_formatting", true)) PunctuationText.ANDROID else PunctuationText.OFF
    private val smallPunctuation = punctuationMode == PunctuationText.SMALL &&
        PunctuationBackend.bundled && FastVoiceFormatting.supports(language)
    private val androidFormatting = Build.VERSION.SDK_INT >= 33 && punctuationMode == PunctuationText.ANDROID
    private val protectedWords = FastVoiceFormatting.protectedWords(prefs.getString(FastVoiceFormatting.PROTECTED_WORDS_PREFERENCE, null))
    private val draft = VoiceDraft(precedingText, prefs.getBoolean(FastVoiceFormatting.PREFERENCE, true), language, protectedWords, smallPunctuation)
    private var recognizer: SpeechRecognizer? = null
    private var audioSource: VoiceAudioSource? = null
    private var streamingUnavailable = false
    private var recognitionRun: VoiceRecognitionRun? = null
    private var closed = false
    private var armed = false
    private var stopping = false
    private val cleanup = CleanupCoordinator()
    private var audioId = 0
    private var cleanupDetail = R.string.voice_live_hint
    private var cleanupExplanation = ""
    private var cleanupTimeout: Runnable? = null
    private var failures = 0
    private val restart = Runnable { if (armed && !closed) listen() }
    private val cleanLater = Runnable { clean() }
    private var lastPunctuation: VoiceDraft.PunctuationSnapshot? = null
    private var lastPunctuationTime = 0L
    private var punctuationScheduled = false
    private val punctuation = if (smallPunctuation) LocalPunctuation(context, diagnostic) { message ->
        if (!closed && draft.punctuationEnabled) panel.setPunctuation(message)
    } else null
    private val punctuateLater = Runnable { punctuationScheduled = false; punctuate() }
    private val finishTimeout = Runnable { finishUtterance("") }
    private val timeout = Runnable { stopUtterance() }
    private val readyTimeout = Runnable {
        if (recognitionRun?.streaming == true) useCompatibilityMode() else fail(R.string.voice_timeout)
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            refreshCleanup()
            diagnostic?.let {
                panel.setDiagnostic(context.getString(if (it.recording) R.string.voice_diagnostic_recording else R.string.voice_diagnostic_stopped, it.stopReason), it.recording)
            }
            handler.postDelayed(this, 1000)
        }
    }

    init {
        diagnostic?.event("formatting_config", "fast_enabled" to draft.fastFormatting, "language" to language,
            "case_policy" to "denylist", "protected_words" to protectedWords.sorted().joinToString("\n"),
            "android_formatting_requested" to androidFormatting,
            "small_punctuation" to smallPunctuation, "punctuation_mode" to punctuationMode,
            "android_formatting_strategy" to if (androidFormatting) "latency" else "off")
        panel.setAutomatic(prefs.getBoolean("automatic_cleanup", true), notify = false)
        cleanup.setEnabled(panel.automatic, now())
        if (smallPunctuation) panel.setPunctuation(R.string.voice_punctuation_ready)
        else if (punctuationMode == PunctuationText.SMALL) panel.setPunctuation(R.string.voice_punctuation_unavailable)
        panel.onAutomaticChanged = { enabled ->
            prefs.edit().putBoolean("automatic_cleanup", enabled).apply()
            diagnostic?.event("automatic_cleanup", "enabled" to enabled)
            cleanup.setEnabled(enabled, now())
            clearCleanupTimeout()
            LocalCleanup.cancel()
            handler.removeCallbacks(cleanLater)
            if (enabled) scheduleCleanup() else setCleanupDetail(R.string.voice_live_hint)
        }
        panel.onRetry = {
            diagnostic?.event("cleanup_retry")
            cleanup.retry(now()); scheduleCleanup()
        }
        panel.onStopDiagnostic = { diagnostic?.stop(); panel.setDiagnostic(context.getString(R.string.voice_diagnostic_stopped, "user_stop"), false) }
        panel.onMarkDiagnostic = { diagnostic?.event("user_marker", "draft" to draft.text); panel.setDiagnostic(context.getString(R.string.voice_marker_saved), true) }
        handler.post(tick)
        panel.record.setOnClickListener {
            diagnostic?.event("record_toggle", "was_armed" to armed)
            if (armed) {
                armed = false
                handler.removeCallbacks(restart)
                stopUtterance()
                buttons()
            } else start()
        }
        panel.undo.setOnClickListener {
            panel.setAutomatic(false) // Do not immediately repeat the edit being undone.
            diagnostic?.event("undo_cleanup")
            if (draft.undo()) {
                handler.removeCallbacks(punctuateLater)
                punctuationScheduled = false
                punctuation?.release()
                panel.setPunctuation(null)
                render("undo"); refreshCleanup()
            }
        }
        panel.setup.setOnClickListener {
            close()
            context.startActivity(Intent(context, VoiceSetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        buttons()
    }

    fun start() {
        if (closed) return
        armed = true
        if (smallPunctuation && !render("resume")) return
        buttons()
        listen()
    }

    private fun listen() {
        if (closed || !armed || recognizer != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { fail(R.string.voice_unsupported); return }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail(R.string.voice_permission_needed); return
        }
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { fail(R.string.voice_unavailable); return }
            val active = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = active
            stopping = false
            val currentRun = VoiceRecognitionRun(Build.VERSION.SDK_INT >= 33 && !streamingUnavailable &&
                prefs.getBoolean("continuous_audio", true))
            recognitionRun = currentRun
            panel.setCaptureMode(currentRun.streaming)
            diagnostic?.event("recognizer_start", "streaming" to currentRun.streaming, "language" to language,
                "android_formatting_requested" to androidFormatting)
            if (!currentRun.streaming) diagnostic?.event("audio_unavailable", "reason" to "compatibility mode owns microphone")
            active.setRecognitionListener(object : RecognitionListener {
                private fun current() = !closed && recognizer === active
                override fun onReadyForSpeech(params: Bundle?) {
                    if (current()) {
                        diagnostic?.event("asr_ready")
                        handler.removeCallbacks(readyTimeout)
                        setStatus(R.string.voice_listening)
                    }
                }
                override fun onBeginningOfSpeech() {
                    if (current() && armed) {
                        diagnostic?.event("asr_speech_begin")
                        handler.removeCallbacks(finishTimeout)
                        handler.removeCallbacks(readyTimeout)
                        setStatus(R.string.voice_listening)
                    }
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (current()) diagnostic?.event("asr_speech_end")
                    if (current() && currentRun.waitForFinalOnSilence) {
                        setStatus(R.string.voice_finishing)
                        handler.postDelayed(finishTimeout, K.VOICE_FINAL_WAIT_MS)
                    }
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    diagnostic?.event("asr_error", "code" to error)
                    if (currentRun.streaming && error in setOf(SpeechRecognizer.ERROR_AUDIO,
                            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_SERVER)) {
                        if (armed && !stopping) useCompatibilityMode() else finishUtterance("")
                        return
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        finishUtterance("")
                    } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && ++failures <= 3) {
                        finishUtterance("")
                    } else {
                        draft.finish()
                        render("asr_error_final")
                        fail(when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission_needed
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_language_unavailable
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_busy
                            else -> R.string.voice_error
                        })
                        finalized()
                    }
                }
                override fun onSegmentResults(segmentResults: Bundle) {
                    if (!current()) return
                    handler.removeCallbacks(readyTimeout)
                    hypotheses("segment", segmentResults)
                    val text = currentRun.segment(recognized(segmentResults)) ?: return
                    diagnostic?.event("asr_segment", "text" to text)
                    val hadSpeech = text.isNotBlank() || draft.partial.isNotBlank()
                    draft.finish(text)
                    if (render("asr_segment") && hadSpeech) finalized()
                    // Deliberately keep both recognizer and microphone alive across silence.
                }
                override fun onEndOfSegmentedSession() {
                    if (!current()) return
                    diagnostic?.event("asr_session_end")
                    val text = currentRun.complete("") ?: return
                    if (armed && !stopping) markCompatibilityMode()
                    finishUtterance(text)
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    failures = 0
                    hypotheses("final", results)
                    diagnostic?.event("asr_results", "text" to recognized(results))
                    val text = currentRun.complete(recognized(results)) ?: return
                    if (currentRun.streaming && armed && !stopping) markCompatibilityMode()
                    finishUtterance(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!current()) return
                    hypotheses("partial", partialResults)
                    val text = recognized(partialResults)
                    if (text.isNotBlank()) {
                        handler.removeCallbacks(readyTimeout)
                        diagnostic?.event("asr_partial", "text" to text)
                        draft.partial(text); render("asr_partial")
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            setStatus(R.string.voice_starting)
            buttons()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (Build.VERSION.SDK_INT >= 33 && androidFormatting) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
                    putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
                }
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, K.VOICE_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, K.VOICE_SILENCE_MS)
            }
            if (currentRun.streaming && Build.VERSION.SDK_INT >= 33) {
                val source = VoiceAudioSource(diagnostic, ++audioId) {
                    handler.post { if (!closed && recognizer === active) useCompatibilityMode() }
                }
                audioSource = source
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source.descriptor)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, K.VOICE_SAMPLE_RATE)
                intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            handler.postDelayed(readyTimeout, K.VOICE_READY_WAIT_MS)
            active.startListening(intent)
            // Only the legacy single-utterance route needs a forced stop. Never end a live pipe on silence.
            if (!currentRun.streaming) handler.postDelayed(timeout, K.VOICE_UTTERANCE_MS)
        } catch (_: Exception) {
            if (recognitionRun?.streaming == true) useCompatibilityMode() else fail(R.string.voice_error)
        }
    }

    private fun hypotheses(kind: String, results: Bundle?) {
        diagnostic?.event("asr_hypotheses", "callback" to kind,
            "android_formatting_requested" to androidFormatting,
            "selected_text" to recognized(results),
            "alternatives" to results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
            "confidence" to results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.toList())
    }

    // With formatting honored, hypothesis 0 is formatted and 1 is raw. If ignored,
    // hypothesis 0 remains the best transcript. Never append both or guess support
    // from the number of hypotheses: providers can return ordinary alternatives.
    private fun recognized(results: Bundle?) = results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun markCompatibilityMode() {
        streamingUnavailable = true
        setStatus(R.string.voice_compatibility, alert = true)
    }

    private fun useCompatibilityMode() {
        markCompatibilityMode()
        finishUtterance("")
    }

    private fun stopUtterance() {
        if (recognizer == null) {
            if (smallPunctuation) render("paused")
            setStatus(R.string.voice_paused); return
        }
        stopping = true
        handler.removeCallbacks(readyTimeout)
        setStatus(R.string.voice_finishing)
        try {
            val source = audioSource
            if (source != null) source.finish() else recognizer?.stopListening()
        } catch (_: RuntimeException) { finishUtterance(""); return }
        handler.removeCallbacks(finishTimeout)
        handler.postDelayed(finishTimeout, K.VOICE_FINAL_WAIT_MS)
    }

    private fun finishUtterance(text: String) {
        if (closed) return
        releaseRecognizer()
        val hadSpeech = text.isNotBlank() || draft.partial.isNotBlank()
        draft.finish(text)
        diagnostic?.event("utterance_finished", "text" to text)
        if (!render("asr_final")) return
        if (hadSpeech) finalized()
        setStatus(if (armed) R.string.voice_continuing else R.string.voice_paused)
        buttons()
        if (armed) handler.postDelayed(restart, K.VOICE_RESTART_MS)
    }

    private fun render(source: String): Boolean {
        if (closed) return false
        draft.punctuationComplete = !armed && recognizer == null
        if (draft.text != draft.textWithoutFastFormatting) diagnostic?.event("fast_format",
            "source" to source, "before" to draft.textWithoutFastFormatting, "after" to draft.text)
        diagnostic?.event("draft_render", "source" to source, "text" to draft.text, "original" to draft.original,
            "revision" to cleanup.revision, "active_job" to cleanup.active?.id,
            "punctuation_complete" to draft.punctuationComplete)
        if (!update(draft.text, source)) { close(); return false }
        buttons()
        if (draft.punctuationEnabled && !punctuationScheduled) {
            punctuationScheduled = true
            handler.postDelayed(punctuateLater, (lastPunctuationTime + K.VOICE_PUNCTUATION_INTERVAL_MS - now()).coerceAtLeast(0))
        }
        return true
    }

    private fun punctuate() {
        if (closed || !draft.punctuationEnabled) return
        val snapshot = draft.punctuationSnapshot()
        if (snapshot == null) {
            panel.setPunctuation(if (draft.punctuationPendingText.isBlank()) R.string.voice_punctuation_ready else R.string.voice_punctuation_limit)
            return
        }
        if (snapshot == lastPunctuation) return
        lastPunctuation = snapshot
        lastPunctuationTime = now()
        punctuation?.submit(snapshot) { output ->
            val accepted = output != null && draft.applyPunctuation(snapshot, output)
            diagnostic?.event("punctuation_applied", "source" to snapshot.request.source,
                "before" to snapshot.request.before, "input" to snapshot.request.input,
                "context_words" to snapshot.request.contextWords, "generation" to snapshot.generation,
                "output" to output, "accepted" to accepted)
            if (accepted) render("punctuation_apply")
            accepted
        }
    }

    private fun finalized() {
        cleanup.finalized(now())
        diagnostic?.event("revision", "revision" to cleanup.revision, "text" to draft.text)
        scheduleCleanup()
    }
    private fun setCleanupDetail(message: Int, explanation: String = "") {
        cleanupDetail = message; cleanupExplanation = explanation
        diagnostic?.event("cleanup_status", "message" to context.getString(message), "explanation" to explanation,
            "revision" to cleanup.revision, "applied_revision" to cleanup.appliedRevision, "active_job" to cleanup.active?.id)
        refreshCleanup()
    }
    private fun refreshCleanup() {
        val job = cleanup.active
        val details = if (job != null) context.getString(R.string.voice_job_details, job.id, job.revision,
            (now() - job.started) / 1000, cleanup.revision) else context.getString(R.string.voice_revision_details,
            cleanup.appliedRevision, cleanup.revision)
        val fast = context.getString(if (draft.fastFormatting && FastVoiceFormatting.supports(language))
            R.string.voice_fast_active else R.string.voice_fast_inactive)
        val android = context.getString(if (androidFormatting) R.string.voice_android_formatting_requested else R.string.voice_android_formatting_off)
        panel.setDetail(cleanupDetail, listOf(cleanupExplanation, details, fast, android).filter { it.isNotEmpty() }.joinToString("\n"))
        panel.canRetry = cleanup.enabled && job == null && cleanup.revision > 0
    }
    private fun scheduleCleanup(showWaiting: Boolean = true) {
        handler.removeCallbacks(cleanLater)
        if (closed) return
        val delay = cleanup.delay(now()) ?: return
        if (showWaiting) setCleanupDetail(R.string.voice_cleanup_queued)
        handler.postDelayed(cleanLater, delay)
    }
    private fun clean() {
        if (closed) return
        val job = cleanup.start(now()) ?: return
        val snapshot = draft.snapshot()
        if (snapshot == null) {
            cleanup.finish(job, false, true, now())
            setCleanupDetail(R.string.voice_cleanup_too_long); return
        }
        if (!LocalCleanup.available || !LocalCleanup.modelFile(context).isFile) {
            cleanup.finish(job, false, true, now())
            setCleanupDetail(R.string.voice_model_needed); return
        }
        diagnostic?.event("ai_started", "job" to job.id, "revision" to job.revision,
            "input" to snapshot.input, "original" to snapshot.original, "model_transcript" to snapshot.original)
        cleanupTimeout = Runnable {
            if (!closed && cleanup.finish(job, false, true, now())) {
                cleanupTimeout = null
                LocalCleanup.cancel(); handler.removeCallbacks(cleanLater)
                diagnostic?.event("ai_watchdog", "job" to job.id)
                setCleanupDetail(R.string.voice_cleanup_timed_out, "UI watchdog: ${K.VOICE_CLEANUP_UI_TIMEOUT_MS / 1000} s")
            }
        }.also { handler.postDelayed(it, K.VOICE_CLEANUP_UI_TIMEOUT_MS) }
        setCleanupDetail(R.string.voice_cleaning)
        LocalCleanup.clean(context, snapshot, job, diagnostic) result@{ result ->
            if (closed || cleanup.active != job) {
                diagnostic?.event("ai_stale_ignored", "job" to job.id); return@result
            }
            clearCleanupTimeout()
            val text = result.text
            val accepted = text != null && draft.apply(snapshot, text)
            cleanup.finish(job, accepted, !accepted, now())
            diagnostic?.event("ai_applied", "job" to job.id, "accepted" to accepted, "text" to draft.text,
                "rejection" to if (text != null && !accepted) {
                    if (!CleanupGuard.respectsContext(snapshot.original, text, snapshot.context)) "CONTEXT_LEAK" else "FIDELITY_OR_STALE"
                } else null)
            setCleanupDetail(when {
                result.code == "TIMEOUT" -> R.string.voice_cleanup_timed_out
                result.code == "INPUT_LIMIT" || result.code == "OUTPUT_LIMIT" -> R.string.voice_cleanup_too_long
                text == null -> R.string.voice_cleanup_failed
                !accepted -> R.string.voice_cleanup_rejected
                cleanup.pending -> R.string.voice_cleanup_queued
                text.trim() == snapshot.input -> R.string.voice_already_clean
                else -> R.string.voice_cleaned
            }, if (text == null) "${result.code}: ${result.payload}" else "")
            if (accepted && !render("ai_apply")) return@result
            scheduleCleanup(showWaiting = false)
        }
    }

    private fun clearCleanupTimeout() {
        cleanupTimeout?.let { handler.removeCallbacks(it) }
        cleanupTimeout = null
    }

    fun trimMemory() {
        diagnostic?.event("memory_pressure")
        val hadWork = cleanup.active != null || cleanup.pending
        cleanup.suspendWork()
        clearCleanupTimeout()
        handler.removeCallbacks(cleanLater)
        LocalCleanup.release()
        punctuation?.release()
        handler.removeCallbacks(punctuateLater)
        punctuationScheduled = false
        lastPunctuation = null
        if (hadWork) setCleanupDetail(R.string.voice_cleanup_failed, context.getString(R.string.voice_memory_pressure))
    }

    fun destroy() {
        closed = true
        panel.setDetail(R.string.voice_live_hint)
        armed = false
        cleanup.active?.let { diagnostic?.event("ai_cancelled", "job" to it.id, "revision" to it.revision, "reason" to "voice_session_closed") }
        cleanup.cancel()
        clearCleanupTimeout()
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        punctuation?.close()
        LocalCleanup.release()
        diagnostic?.event("session_closed", "text" to draft.text)
        diagnostic?.stop("session_closed")
        draft.clear()
    }

    private fun releaseRecognizer() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(readyTimeout)
        handler.removeCallbacks(finishTimeout)
        val old = recognizer
        recognizer = null
        recognitionRun = null
        try { old?.cancel() } catch (_: RuntimeException) { }
        try { old?.destroy() } catch (_: RuntimeException) { }
        audioSource?.close()
        audioSource = null
    }
    private fun setStatus(message: Int, alert: Boolean = false) {
        diagnostic?.event("recognition_status", "message" to context.getString(message))
        panel.setStatus(message, alert)
    }
    private fun fail(message: Int) {
        armed = false
        handler.removeCallbacks(restart)
        releaseRecognizer()
        setStatus(message, alert = true)
        buttons()
    }
    private fun buttons() {
        panel.setRecording(armed)
        panel.undo.isEnabled = draft.canUndo
    }
}
