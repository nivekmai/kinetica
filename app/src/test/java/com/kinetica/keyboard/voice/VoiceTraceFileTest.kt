package com.kinetica.keyboard.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VoiceTraceFileTest {
    @Test fun exportedAudioHasCorrectHeaderSamplesAndEscapedEventText() {
        val folder = Files.createTempDirectory("voice-test").toFile()
        try {
            VoiceTraceFile(folder, 16000).use { trace ->
                assertEquals(0L, trace.pcm(byteArrayOf(0, 0, -1, 127)))
                assertEquals(2L, trace.pcm(byteArrayOf(0, -128)))
                trace.event(mapOf("type" to "asr_partial", "text" to "a\"b\\c\n\u0001 é"))
            }
            val data = folder.resolve("audio.wav").readBytes()
            assertEquals("RIFF", data.take(4).toByteArray().toString(Charsets.US_ASCII))
            val bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(42, bytes.getInt(4)); assertEquals(16000, bytes.getInt(24)); assertEquals(6, bytes.getInt(40))
            assertArrayEquals(byteArrayOf(0,0,-1,127,0,-128), data.drop(44).toByteArray())
            assertEquals("{\"type\":\"asr_partial\",\"text\":\"a\\\"b\\\\c\\n\\u0001 é\"}\n", folder.resolve("events.jsonl").readText())
        } finally { folder.deleteRecursively() }
    }
    @Test fun unknownNonfiniteConfidenceCannotCorruptTheJsonLog() {
        assertEquals("[null,null,-1.0,0.5]", VoiceTraceFile.json(listOf(Float.NaN, Double.POSITIVE_INFINITY, -1f, 0.5f)))
    }
    @Test fun nativeFailuresRemainDistinctAndNeverBecomeInsertedText() {
        for (code in listOf("TIMEOUT", "INPUT_LIMIT", "OUTPUT_LIMIT", "CANCELLED", "MODEL_ERROR", "ENGINE_ERROR")) {
            val result = CleanupResult.decode("$code\nprefill".toByteArray())
            assertEquals(code, result.code); assertNull(result.text)
        }
        assertEquals("hello\nworld", CleanupResult.decode("SUCCESS\nhello\nworld".toByteArray()).text)
        assertNull(CleanupResult.decode(null).text)
        assertNull(CleanupResult.decode("unexpected".toByteArray()).text)
    }
}
