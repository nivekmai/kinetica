package com.kinetica.keyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Live PCM pipe with an optional explicitly armed diagnostic tee. The microphone stays open between recognized segments. */
@SuppressLint("MissingPermission") // VoiceSession checks the runtime grant before constructing this.
class VoiceAudioSource(private val diagnostic: VoiceDiagnostics? = null, private val source: Int = 0, private val onFailure: () -> Unit) : AutoCloseable {
    private val pipe = ParcelFileDescriptor.createPipe()
    val descriptor: ParcelFileDescriptor get() = pipe[0]
    private var recorder: AudioRecord? = null
    @Volatile private var alive = true
    @Volatile private var capturing = true

    init {
        try {
            val minimum = AudioRecord.getMinBufferSize(K.VOICE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            val audio = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(K.VOICE_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum * 2, K.VOICE_AUDIO_BUFFER_BYTES)).build()
            recorder = audio
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            // Nonblocking writes make cancellation reliable even if the service stops reading.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
            } else error("Continuous audio requires Android 13")
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            Thread({ pump(audio) }, "Kinetica-voice-audio").start()
        } catch (e: Exception) {
            close()
            recorder?.release()
            throw e
        }
    }

    private fun pump(audio: AudioRecord) {
        val buffer = ByteArray(K.VOICE_AUDIO_CHUNK_BYTES)
        var chunk = 0L
        var delivered = 0
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (alive && capturing) {
                val count = audio.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    if (capturing && alive) error("Audio capture ended")
                    break
                }
                chunk++
                delivered = 0
                diagnostic?.audio(source, chunk, buffer, count, android.os.SystemClock.elapsedRealtimeNanos())
                var offset = 0
                while (alive && offset < count) {
                    try {
                        offset += Os.write(pipe[1].fileDescriptor, buffer, offset, count - offset)
                        delivered = offset
                    } catch (e: ErrnoException) {
                        if (e.errno != OsConstants.EAGAIN && e.errno != OsConstants.EINTR) throw e
                        Thread.sleep(K.VOICE_AUDIO_RETRY_MS)
                    }
                }
                diagnostic?.event("audio_delivery", "source" to source, "chunk" to chunk, "bytes" to offset, "captured_bytes" to count)
            }
        } catch (_: Exception) {
            diagnostic?.event("audio_error", "source" to source, "chunk" to chunk, "delivered_bytes" to delivered)
            if (alive && capturing) onFailure()
        } finally {
            diagnostic?.event("audio_closed", "source" to source)
            buffer.fill(0)
            runCatching { audio.stop() }
            audio.release()
            runCatching { pipe[1].close() } // EOF asks the recognizer for its last segment.
        }
    }

    /** Pause: stop capturing, flush the current chunk, then deliver EOF without cancelling results. */
    fun finish() {
        capturing = false
        runCatching { recorder?.stop() }
    }

    /** Field/key/lifecycle exit: abandon audio and unblock the pump immediately. */
    override fun close() {
        alive = false
        capturing = false
        runCatching { recorder?.stop() }
        pipe.forEach { runCatching { it.close() } }
    }
}
