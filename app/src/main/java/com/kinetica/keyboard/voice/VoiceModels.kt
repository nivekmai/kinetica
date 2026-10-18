package com.kinetica.keyboard.voice

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Pinned publisher artifacts: downloads cannot silently change when a repository is updated. */
data class VoiceModel(val id: String, val name: String, val size: Long, val sha256: String,
                      val repository: String, val revision: String, val filename: String) {
    val url get() = "https://huggingface.co/$repository/resolve/$revision/$filename"
    val page get() = "https://huggingface.co/$repository"
}
object VoiceModels {
    val all = listOf(
        VoiceModel("qwen-1.5b-q4", "Qwen 2.5 · 1.5B", 1117320736,
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF", "91cad51170dc346986eccefdc2dd33a9da36ead9",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
        VoiceModel("qwen-0.5b-q4", "Qwen 2.5 · 0.5B", 491400032,
            "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            "Qwen/Qwen2.5-0.5B-Instruct-GGUF", "9217f5db79a29953eb74d5343926648285ec7e67",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf"),
    )

    /** Streaming verification also bounds corrupt/malicious imports. Caller publishes only on success. */
    fun copyVerified(input: InputStream, output: OutputStream, model: VoiceModel? = null,
                     cancelled: () -> Boolean = { false }) {
        val hash = MessageDigest.getInstance("SHA-256")
        val magic = ByteArray(4)
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            check(!cancelled()) { "Cancelled" }
            val count = input.read(buffer)
            if (count < 0) break
            for (i in 0 until minOf(count, (4 - total).coerceAtLeast(0).toInt())) magic[total.toInt() + i] = buffer[i]
            total += count
            require(total <= (model?.size ?: 2L * 1024 * 1024 * 1024)) { "File too large" }
            hash.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
        require(total >= 24 && magic.contentEquals(byteArrayOf(71, 71, 85, 70))) { "Not a GGUF file" }
        if (model != null) {
            require(total == model.size) { "Incomplete download" }
            val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(actual == model.sha256) { "Checksum mismatch" }
        }
    }
}
