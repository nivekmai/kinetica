package com.kinetica.keyboard.voice

import java.util.Locale
import com.kinetica.keyboard.engine.KineticaConstants as K

/** Word-aligned punctuation only. The native model never owns or replaces editor words. */
object PunctuationText {
    const val PREFERENCE = "punctuation_mode"
    const val SMALL = "small"
    const val ANDROID = "android"
    const val OFF = "off"
    private val word = Regex("(?:[A-Za-z]\\.){2,}|\\p{N}+(?:[.:,]\\p{N}+)+|[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*")
    private val softGap = Regex("[\\s,.?!]*")
    private val abbreviationEnd = Regex("(?:\\b[A-Za-z]\\.){2,}$")
    data class Request(val source: String, val before: String, val input: String, val contextWords: Int)

    fun prepare(source: String, before: String): Request? {
        if (source.length > K.VOICE_PUNCTUATION_CHARS) return null
        val context = word.findAll(before).toList().takeLast(K.VOICE_PUNCTUATION_CONTEXT_WORDS)
        val speech = word.findAll(source).toList()
        if (speech.isEmpty()) return null
        val tokens = (context + speech).map { it.value.lowercase(Locale.ROOT).replace('’', '\'') }
        // The upstream tokenizer batches 200 subword tokens. A single pathological
        // word must not exceed its batch (including arbitrary editor context).
        if (tokens.any { it.toByteArray(Charsets.UTF_8).size > K.VOICE_PUNCTUATION_WORD_BYTES }) return null
        return Request(source, before, tokens.joinToString(" "), context.size)
    }

    fun restore(request: Request, output: String, protectedWords: Set<String>): String? {
        val input = request.input.split(' ')
        val predicted = output.trim().split(Regex("\\s+"))
        if (predicted.size != input.size) return null
        val punctuation = mutableListOf<String>()
        val cases = mutableListOf<Boolean>()
        for ((i, token) in predicted.withIndex()) {
            val original = input[i]
            if (token.length < original.length || !token.take(original.length).equals(original, true)) return null
            val mark = token.drop(original.length)
            if (mark !in listOf("", ",", ".", "?")) return null
            punctuation.add(mark)
            cases.add(token.firstOrNull()?.isUpperCase() == true)
        }
        val words = word.findAll(request.source).toList()
        return buildString {
            append(request.source.substring(0, words.first().range.first))
            for ((i, match) in words.withIndex()) {
                val value = match.value
                val lower = value.lowercase(Locale.ROOT)
                val key = lower.replace('’', '\'')
                val protected = key in protectedWords || key.removeSuffix("'s") in protectedWords ||
                    value.drop(1).any { it.isUpperCase() } || value.contains('.')
                append(if (protected) value else if (cases[request.contextWords + i])
                    lower.replaceFirstChar { it.uppercaseChar() } else lower)
                val end = if (i == words.lastIndex) request.source.length else words[i + 1].range.first
                val gap = request.source.substring(match.range.last + 1, end)
                if (softGap.matches(gap) && !gap.contains('\n')) {
                    val mark = punctuation[request.contextWords + i]
                    if (!(value.endsWith('.') && mark == ".")) append(mark)
                    if (i != words.lastIndex) append(' ')
                } else append(gap) // Keep quotes, line breaks, list markers and other structure.
            }
        }
    }

    /** A live recognition endpoint is not a sentence boundary. Do not pin its prediction. */
    fun display(text: String, complete: Boolean): String {
        var result = text.trimEnd()
        val provisional = if (complete) "," else ",.?!"
        while (result.isNotEmpty() && result.last() in provisional && !abbreviationEnd.containsMatchIn(result)) {
            result = result.dropLast(1)
        }
        return result
    }
}
