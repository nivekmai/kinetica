package com.kinetica.keyboard.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kinetica.keyboard.R
import java.util.concurrent.Executors
import com.kinetica.keyboard.engine.KineticaConstants as K

/** One worker, one active inference, one replaceable pending request per editor session. */
class LocalPunctuation(context: Context, private val diagnostic: VoiceDiagnostics?,
                       private val status: (Int) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var backend: PunctuationBackend? = null // Worker-thread ownership only.
    private var closed = false
    private var epoch = 0L
    private var nextId = 0L
    private data class Work(val id: Long, val epoch: Long, val snapshot: VoiceDraft.PunctuationSnapshot,
                            val done: (String?) -> Boolean)
    private var active: Work? = null
    private var pending: Work? = null

    fun submit(snapshot: VoiceDraft.PunctuationSnapshot, done: (String?) -> Boolean) {
        if (closed) return
        pending = Work(++nextId, epoch, snapshot, done)
        pump()
    }

    private fun pump() {
        if (closed || active != null) return
        val work = pending ?: return
        pending = null
        active = work
        status(R.string.voice_punctuation_working)
        diagnostic?.event("punctuation_started", "job" to work.id, "input" to work.snapshot.request.input)
        val watchdog = Runnable {
            if (!closed && active == work && work.epoch == epoch) {
                epoch++
                pending = null
                status(R.string.voice_punctuation_failed)
                diagnostic?.event("punctuation_timeout", "job" to work.id)
            }
        }
        main.postDelayed(watchdog, K.VOICE_PUNCTUATION_TIMEOUT_MS)
        executor.execute {
            val start = SystemClock.elapsedRealtime()
            var error = ""
            val output = try {
                val engine = backend ?: PunctuationBackend(app).also { backend = it }
                engine.punctuate(work.snapshot.request.input)
            } catch (e: Exception) { error = e.javaClass.simpleName; null }
              catch (e: LinkageError) { error = e.javaClass.simpleName; null }
            val elapsed = SystemClock.elapsedRealtime() - start
            diagnostic?.event("punctuation_result", "job" to work.id, "output" to output,
                "elapsed_ms" to elapsed, "error" to error)
            main.post {
                main.removeCallbacks(watchdog)
                if (closed) return@post
                active = null
                if (work.epoch == epoch) {
                    val accepted = work.done(output)
                    status(if (output == null) R.string.voice_punctuation_failed else if (accepted)
                        R.string.voice_punctuation_ready else R.string.voice_punctuation_skipped)
                }
                pump()
            }
        }
    }

    fun release() {
        if (closed) return
        epoch++
        pending = null
        executor.execute { backend?.close(); backend = null }
        status(R.string.voice_punctuation_ready)
    }

    fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(null)
        epoch++
        pending = null
        executor.execute { backend?.close(); backend = null }
        executor.shutdown()
    }
}
