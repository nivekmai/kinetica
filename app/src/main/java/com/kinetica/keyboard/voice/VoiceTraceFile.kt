package com.kinetica.keyboard.voice

import java.io.File
import java.io.RandomAccessFile

/** Single-writer, portable diagnostic format. WAV samples and JSON events share explicit offsets. */
class VoiceTraceFile(val directory: File, private val sampleRate: Int) : AutoCloseable {
    private val events = File(directory, "events.jsonl").bufferedWriter()
    private val audio = RandomAccessFile(File(directory, "audio.wav"), "rw")
    var audioBytes = 0L
        private set
    var eventBytes = 0L
        private set
    init { audio.write(ByteArray(44)) }
    fun event(fields: Map<String, Any?>) {
        val line = json(fields) + "\n"
        events.write(line); events.flush()
        eventBytes += line.toByteArray(Charsets.UTF_8).size
    }
    fun pcm(bytes: ByteArray): Long {
        val offset = audioBytes / 2
        audio.write(bytes); audioBytes += bytes.size
        return offset
    }
    override fun close() {
        try {
            audio.seek(0)
            fun int(value: Long) { repeat(4) { audio.write((value shr (8 * it)).toInt() and 255) } }
            fun short(value: Int) { audio.write(value and 255); audio.write(value shr 8 and 255) }
            audio.writeBytes("RIFF"); int(36 + audioBytes); audio.writeBytes("WAVEfmt "); int(16)
            short(1); short(1); int(sampleRate.toLong()); int(sampleRate * 2L); short(2); short(16)
            audio.writeBytes("data"); int(audioBytes)
        } finally { audio.close(); events.close() }
    }
    companion object {
        fun json(value: Any?): String = when (value) {
            null -> "null"
            is Double -> if (value.isFinite()) value.toString() else "null"
            is Float -> if (value.isFinite()) value.toString() else "null"
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
            is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
            else -> buildString {
                append('"')
                for (c in value.toString()) when (c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
                }
                append('"')
            }
        }
    }
}
