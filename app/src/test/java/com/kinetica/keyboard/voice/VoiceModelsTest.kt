package com.kinetica.keyboard.voice

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class VoiceModelsTest {
    private val data = "GGUFabcdefghijklmnopqrstuvwx".toByteArray()
    private val model = VoiceModels.all.first().copy(size = data.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) })
    @Test fun exactPublisherArtifactPasses() {
        val output = ByteArrayOutputStream()
        VoiceModels.copyVerified(data.inputStream(), output, model)
        assertArrayEquals(data, output.toByteArray())
    }
    @Test fun rejectsTruncatedCorruptAndHtmlDownloads() {
        for (bad in listOf(data.dropLast(1).toByteArray(), data.clone().apply { this[15] = 0 }, "<html>not a model</html>".toByteArray())) {
            assertThrows(IllegalArgumentException::class.java) {
                VoiceModels.copyVerified(bad.inputStream(), ByteArrayOutputStream(), model)
            }
        }
    }
    @Test fun cancellationPreventsPublishing() {
        assertThrows(IllegalStateException::class.java) {
            VoiceModels.copyVerified(data.inputStream(), ByteArrayOutputStream(), model) { true }
        }
    }
    @Test fun catalogIsPinnedToHashesAndImmutableRevisions() {
        for (m in VoiceModels.all) {
            assertTrue(m.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(m.revision.matches(Regex("[a-f0-9]{40}")))
            assertTrue(m.url.startsWith("https://huggingface.co/Qwen/"))
        }
    }
}
