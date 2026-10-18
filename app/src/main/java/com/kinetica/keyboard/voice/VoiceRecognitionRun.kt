package com.kinetica.keyboard.voice

/** One recognizer request. Segments are final independently; a full-session final must not duplicate them. */
class VoiceRecognitionRun(val streaming: Boolean) {
    var sawSegment = false
        private set
    private var terminal = false
    fun segment(text: String): String? {
        if (terminal) return null
        sawSegment = true
        return text
    }
    fun complete(text: String): String? {
        if (terminal) return null
        terminal = true
        return if (sawSegment) "" else text
    }
    // End-of-speech is just a pause in a streamed session, not the end of the audio source.
    val waitForFinalOnSilence: Boolean get() = !streaming && !terminal
}
