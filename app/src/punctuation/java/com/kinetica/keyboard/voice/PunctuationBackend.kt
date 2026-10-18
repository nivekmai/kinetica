package com.kinetica.keyboard.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlinePunctuation
import com.k2fsa.sherpa.onnx.OnlinePunctuationConfig
import com.k2fsa.sherpa.onnx.OnlinePunctuationModelConfig
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Pinned CPU runtime and bundled English weights; no network or microphone access. */
class PunctuationBackend(context: Context) {
    private val engine = OnlinePunctuation(context.assets, OnlinePunctuationConfig(
        OnlinePunctuationModelConfig(cnnBilstm = "punctuation/model.int8.onnx",
            bpeVocab = "punctuation/bpe.vocab", numThreads = K.VOICE_PUNCTUATION_THREADS, debug = false, provider = "cpu")))
    fun punctuate(text: String): String = engine.addPunctuation(text)
    fun close() = engine.release()
    companion object { const val bundled = true }
}
