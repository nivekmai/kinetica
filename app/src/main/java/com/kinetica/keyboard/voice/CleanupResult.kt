package com.kinetica.keyboard.voice

/** Stable native wire result: status on the first line, output or failure stage after it. */
data class CleanupResult(val code: String, val payload: String) {
    val text: String? get() = payload.takeIf { code == "SUCCESS" }
    companion object {
        private val codes = setOf("SUCCESS", "TIMEOUT", "CANCELLED", "INPUT_LIMIT", "OUTPUT_LIMIT", "MODEL_ERROR", "ENGINE_ERROR")
        fun decode(bytes: ByteArray?): CleanupResult {
            val value = bytes?.toString(Charsets.UTF_8) ?: return CleanupResult("ENGINE_ERROR", "native result unavailable")
            val code = value.substringBefore('\n')
            return if (code in codes && value.contains('\n')) CleanupResult(code, value.substringAfter('\n'))
                else CleanupResult("ENGINE_ERROR", "invalid native result")
        }
    }
}
