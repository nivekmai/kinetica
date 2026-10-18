package com.kinetica.keyboard.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Optional JNI backend: local inference only; dictated text and bounded read-only sentence context. */
object LocalCleanup {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val available: Boolean by lazy {
        try {
            System.loadLibrary("kinetica_voice")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun modelFile(context: Context) = VoiceModelStore.active(context)

    fun cancel() {
        if (available) nextRequest()
    }

    fun release() {
        cancel()
        if (available) executor.execute { releaseEngine() }
    }

    fun clean(context: Context, snapshot: VoiceDraft.Snapshot, job: CleanupCoordinator.Job,
              diagnostic: VoiceDiagnostics?, callback: (CleanupResult) -> Unit) {
        if (!available || !modelFile(context).isFile) {
            callback(CleanupResult("MODEL_ERROR", "model unavailable")); return
        }
        val ticket = nextRequest()
        val path = modelFile(context).absolutePath
        val app = context.applicationContext
        val saved = app.getSharedPreferences("voice", Context.MODE_PRIVATE)
            .getString(CleanupInstructions.PREFERENCE, null)
        executor.execute {
            diagnostic?.event("ai_worker_started", "job" to job.id, "revision" to job.revision)
            val result = try {
                val instruction = app.assets.open("voice_cleanup_prompt.txt").bufferedReader().use { it.readText() }
                val request = CleanupInstructions.request(CleanupInstructions.effective(saved, instruction),
                    snapshot.input, snapshot.context, snapshot.original)
                diagnostic?.event("ai_request", "job" to job.id, "revision" to job.revision,
                    "system" to request.system, "user" to request.user,
                    "model" to java.io.File(path).name, "model_bytes" to java.io.File(path).length(),
                    "input_tokens_limit" to K.VOICE_LLM_INPUT_TOKENS, "output_tokens_limit" to K.VOICE_LLM_OUTPUT_TOKENS,
                    "timeout_ms" to K.VOICE_LLM_TIMEOUT_MS)
                CleanupResult.decode(generate(ticket, path, request.system.toByteArray(Charsets.UTF_8),
                    request.user.toByteArray(Charsets.UTF_8), K.VOICE_LLM_INPUT_TOKENS,
                    K.VOICE_LLM_OUTPUT_TOKENS, K.VOICE_LLM_TIMEOUT_MS))
            } catch (e: Exception) { CleanupResult("ENGINE_ERROR", e.javaClass.simpleName) }
            diagnostic?.event("ai_result", "job" to job.id, "revision" to job.revision,
                "code" to result.code, "payload" to result.payload)
            main.post { callback(result) }
        }
    }

    private external fun releaseEngine()
    private external fun nextRequest(): Long
    private external fun generate(ticket: Long, path: String, system: ByteArray, prompt: ByteArray,
                                  inputLimit: Int, outputLimit: Int, timeoutMs: Long): ByteArray?
}
