package com.kinetica.keyboard.voice

/** Smallest changed suffix in UTF-16, without splitting a surrogate pair. */
data class VoiceEdit(val deleteCount: Int, val insert: String, val expectedTail: String) {
    fun matches(beforeCursor: String?) = beforeCursor != null && beforeCursor.endsWith(expectedTail)
    companion object {
        fun between(old: String, next: String): VoiceEdit {
            var prefix = old.commonPrefixWith(next).length
            if (prefix > 0 && prefix < old.length && old[prefix - 1].isHighSurrogate()) prefix--
            // Include an anchor before the replacement, so edits to nearby text stop updates.
            return VoiceEdit(old.length - prefix, next.substring(prefix), old.takeLast(old.length - prefix + 32))
        }
    }
}
