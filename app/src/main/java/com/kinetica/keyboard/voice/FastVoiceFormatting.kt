package com.kinetica.keyboard.voice

import java.util.Locale

/** Reversible English casing/spacing only; never guesses words or sentence punctuation. */
object FastVoiceFormatting {
    const val PREFERENCE = "fast_formatting"
    const val PROTECTED_WORDS_PREFERENCE = "voice_protected_capitals"
    // Editable starter denylist, not a claim to recognize every proper name.
    const val DEFAULT_PROTECTED_WORDS = "Monday\nTuesday\nWednesday\nThursday\nFriday\nSaturday\nSunday\n" +
        "January\nFebruary\nMarch\nApril\nMay\nJune\nJuly\nAugust\nSeptember\nOctober\nNovember\nDecember\n" +
        "English\nItalian\nFrench\nGerman\nSpanish\nPortuguese\nDutch\nPolish\nCzech\nNorwegian\n" +
        "Arabic\nHebrew\nRussian\nUkrainian\nChinese\nJapanese\nKorean\nHindi\n" +
        "Mr\nMrs\nMs\nDr\nProf\nSr\nJr\nAlice\nWill\nLondon\nParis\n" +
        "Android\nGoogle\nPixel\nKinetica"
    private val word = Regex("[\\p{L}]+(?:['’][\\p{L}]+)*")
    private val sentenceGap = Regex("[.!?:…][\"'”’)\\]]*\\s+$")
    private val beforeComma = Regex("(?<=[\\p{L}\\p{N}])[ \\t]+,")
    private val afterComma = Regex(",(?=\\p{L})")
    // A period after a title, initial or time may not end the sentence. Preserve
    // the next token's case rather than asserting a boundary either way.
    private val abbreviation = Regex("(?i)(?:\\b(?:mr|mrs|ms|dr|prof|sr|jr|vs|etc)|\\b[ap]\\.m|\\b[a-z])\\.\\s*$")
    private val pronouns = setOf("i", "i'm", "i'd", "i'll", "i've")
    val defaultProtectedWords: Set<String> = protectedWords(null)

    fun protectedWords(saved: String?): Set<String> = word.findAll(saved ?: DEFAULT_PROTECTED_WORDS)
        .map { it.value.lowercase(Locale.ROOT).replace('’', '\'') }.toSet()

    fun supports(language: String) = language.substringBefore('-').substringBefore('_').equals("en", true)

    private fun quoteState(gap: String, initial: Boolean): Boolean {
        var quoted = initial
        for (c in gap) when (c) {
            '"', '\'' -> quoted = !quoted
            '“', '‘' -> quoted = true
            '”', '’' -> quoted = false
        }
        return quoted
    }

    fun format(raw: String, before: String, language: String,
               protectedWords: Set<String> = defaultProtectedWords): String {
        if (!supports(language)) return raw
        val text = afterComma.replace(beforeComma.replace(raw, ","), ", ")
        val end = before.trimEnd().trimEnd('"', '\'', '”', '’', ')', ']')
        val lineBreak = before.takeLastWhile { it.isWhitespace() }.contains('\n')
        val startsSentence = end.isEmpty() || lineBreak || end.last() in ".!?:…"
        var cursor = 0
        // Ignore apostrophes within words when carrying an open quote across chunks.
        var quoted = quoteState(word.replace(before, ""), false)
        return buildString {
            for (match in word.findAll(text)) {
                val gap = text.substring(cursor, match.range.first)
                quoted = quoteState(gap, quoted)
                val initial = (cursor == 0 && startsSentence && gap.none { it.isLetterOrDigit() }) ||
                    gap.contains('\n') || sentenceGap.containsMatchIn(gap)
                val token = match.value
                val lower = token.lowercase(Locale.ROOT)
                val key = lower.replace('’', '\'')
                // Only inspect the prefix at a period; avoid copying/scanning the
                // growing transcript for every ordinary word of each partial.
                val followsPeriod = if (cursor == 0) end.endsWith('.') else gap.startsWith('.')
                val ambiguousPeriod = followsPeriod && !gap.contains('\n') && !(cursor == 0 && lineBreak) &&
                    abbreviation.containsMatchIn(if (cursor == 0) before + gap else text.substring(0, match.range.first))
                val protected = key in protectedWords || key.removeSuffix("'s") in protectedWords
                val formatted = when {
                    quoted -> token
                    key in pronouns -> token.replaceFirstChar { it.uppercaseChar() }
                    ambiguousPeriod -> token
                    initial && token == lower -> token.replaceFirstChar { it.uppercaseChar() }
                    protected -> token
                    // A recognizer callback is not a sentence boundary. This also
                    // covers new utterance openings, using the preceding draft text.
                    !initial && token.first().isUpperCase() && token.drop(1) == lower.drop(1) &&
                        (token.length > 1 || token == "A") -> lower
                    else -> token
                }
                append(gap); append(formatted)
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
}
