package com.kinetica.keyboard.voice

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.kinetica.keyboard.engine.KineticaConstants as K
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicitly armed, one-session capture. Never log text to logcat or upload it. */
class VoiceDiagnostics private constructor(context: Context, metadata: Map<String, Any?>) {
    private val start = SystemClock.elapsedRealtimeNanos()
    private val root = root(context)
    val directory = File(root, "session-" + UUID.randomUUID()).apply { mkdirs() }
    private val queue = ArrayBlockingQueue<(VoiceTraceFile) -> Unit>(K.VOICE_TRACE_QUEUE)
    private val accepting = AtomicBoolean(true)
    @Volatile var stopReason = "recording"
        private set
    @Volatile var complete = false
        private set
    private var sequence = 0L
    val recording get() = accepting.get()
    init {
        active = this
        Thread({
            var writer: VoiceTraceFile? = null
            try {
                writer = VoiceTraceFile(directory, K.VOICE_SAMPLE_RATE)
                writer.event(mapOf("type" to "session_start", "schema" to 1, "t_ns" to 0,
                    "wall_time_ms" to System.currentTimeMillis(), "sample_rate" to K.VOICE_SAMPLE_RATE,
                    "android_sdk" to Build.VERSION.SDK_INT, "device" to Build.MODEL,
                    "app_version" to context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                    "metadata" to metadata))
                while (accepting.get() || queue.isNotEmpty()) {
                    queue.poll(100, TimeUnit.MILLISECONDS)?.invoke(writer)
                    if (writer.audioBytes + writer.eventBytes >= K.VOICE_TRACE_MAX_BYTES) { stop("size_limit"); queue.clear() }
                    if (SystemClock.elapsedRealtimeNanos() - start >= K.VOICE_TRACE_MAX_MS * 1_000_000) stop("duration_limit")
                }
                writer.event(mapOf("type" to "session_end", "t_ns" to elapsed(), "reason" to stopReason,
                    "audio_samples" to writer.audioBytes / 2))
            } catch (_: Exception) { stopReason = "storage_error"; accepting.set(false) }
            finally {
                runCatching { writer?.close() }.onFailure { stopReason = "storage_error" }
                runCatching { File(directory, "manifest.json").writeText(VoiceTraceFile.json(mapOf(
                    "schema" to 1, "stop_reason" to stopReason, "sample_rate" to K.VOICE_SAMPLE_RATE,
                    "audio_format" to "PCM signed 16-bit little-endian mono", "events" to "events.jsonl",
                    "audio" to "audio.wav", "audio_samples" to (writer?.audioBytes ?: 0L) / 2,
                    "note" to "Audio chunks are concatenated. Use audio_chunk timestamps and sample offsets for gaps. Editor readback covers the session-owned suffix, not a screenshot."))) }
                complete = true
                if (active === this) active = null
            }
        }, "Kinetica-voice-diagnostic").start()
    }
    private fun elapsed() = SystemClock.elapsedRealtimeNanos() - start
    @Synchronized fun event(type: String, vararg fields: Pair<String, Any?>) {
        if (!accepting.get()) return
        val record = linkedMapOf<String, Any?>("seq" to ++sequence, "t_ns" to elapsed(), "type" to type)
        fields.forEach { record[it.first] = it.second }
        if (!queue.offer { it.event(record) }) stop("queue_overflow")
    }
    @Synchronized fun audio(source: Int, chunk: Long, bytes: ByteArray, count: Int, readEndNanos: Long) {
        if (!accepting.get()) return
        val copy = bytes.copyOf(count)
        val seq = ++sequence
        if (!queue.offer { writer ->
            val offset = writer.pcm(copy)
            writer.event(mapOf("seq" to seq, "t_ns" to readEndNanos - start, "type" to "audio_chunk",
                "source" to source, "chunk" to chunk, "sample_offset" to offset, "samples" to count / 2))
        }) stop("queue_overflow")
    }
    @Synchronized fun stop(reason: String = "user_stop") {
        if (accepting.get()) { stopReason = reason; accepting.set(false) }
    }
    companion object {
        const val ARM = "diagnostic_next_session"
        @Volatile private var active: VoiceDiagnostics? = null
        fun root(context: Context) = File(context.noBackupFilesDir, "voice-diagnostics").apply { mkdirs() }
        fun sessions(context: Context) = root(context).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }.orEmpty()
        fun busy() = active != null
        fun startIfArmed(context: Context, metadata: Map<String, Any?>): VoiceDiagnostics? {
            val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(ARM, false) || busy()) return null
            prefs.edit().putBoolean(ARM, false).apply()
            // Bounded retention, visible in setup. The active session is never deleted.
            sessions(context).drop(K.VOICE_TRACE_KEEP - 1).forEach { it.deleteRecursively() }
            return VoiceDiagnostics(context.applicationContext, metadata)
        }
        fun delete(context: Context) { if (!busy()) sessions(context).forEach { it.deleteRecursively() } }
        fun export(directory: File, output: java.io.OutputStream) {
            ZipOutputStream(output).use { zip ->
                directory.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write("Voice diagnostic report. Contains private audio and text. events.jsonl records monotonic nanoseconds relative to session start, recognition callbacks, exact model requests/results, editor writes and readback. audio.wav concatenates captured PCM chunks; audio_chunk events map sample_offset/samples to read-end t_ns and source. audio_delivery records how many bytes were sent to the recognition pipe; it does not confirm service consumption. Compatibility mode has no captured audio. Editor readback is a bounded suffix and is not a screenshot. This report was exported explicitly by the user.\n".toByteArray())
                zip.closeEntry()
            }
        }
    }
}
