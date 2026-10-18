package com.kinetica.keyboard.voice

import java.util.Locale

/** Final utterances and the changing partial are separate: late cleanup never eats new speech. */
class VoiceDraft @JvmOverloads constructor(private val precedingText: String = "", fastFormatting: Boolean = false,
                                           private val language: String = "en",
                                           private val protectedWords: Set<String> = FastVoiceFormatting.defaultProtectedWords,
                                           punctuation: Boolean = false) {
    private data class Chunk(val id: Long, val raw: String, var text: String = raw, val cleaned: Boolean = false)
    data class PunctuationSnapshot(val request: PunctuationText.Request, val generation: Long)
    private var punctuationGeneration = 0L
    var punctuationEnabled = punctuation
        private set
    var punctuationComplete = false
    private var punctuationResult: Pair<PunctuationSnapshot, String>? = null
    data class Snapshot(val ids: List<Long>, val original: String, val generation: Long, val context: CleanupContext = CleanupContext(), val input: String = original)
    private val chunks = mutableListOf<Chunk>()
    private var nextId = 0L
    private var generation = 0L
    var fastFormatting = fastFormatting
        private set
    var partial = ""
        private set
    val original: String get() = join(chunks.map { it.raw } + partial)
    val textWithoutFastFormatting: String get() = render(includePartial = true, fast = false)
    val text: String get() = render(includePartial = true)
    val canUndo: Boolean get() = text != original

    private fun render(includePartial: Boolean, fast: Boolean = fastFormatting): String {
        if (punctuationEnabled) {
            val (prefix, source) = punctuationParts(includePartial)
            val before = prefix.ifEmpty { precedingText }
            val cached = punctuationResult
            val oldSource = cached?.first?.request?.source.orEmpty()
            val compatible = cached != null && source.startsWith(oldSource) &&
                (source.length == oldSource.length || source.getOrNull(oldSource.length)?.isWhitespace() == true)
            val candidate = if (compatible) {
                val rest = source.drop(oldSource.length)
                PunctuationText.display(cached!!.second, punctuationComplete && rest.isEmpty()) + rest
            } else source
            val suffix = if (fast) FastVoiceFormatting.format(candidate, before, language, protectedWords) else candidate
            val displayedPrefix = if (fast) FastVoiceFormatting.format(prefix, precedingText, language, protectedWords) else prefix
            return join(listOf(displayedPrefix, suffix))
        }
        val result = StringBuilder()
        fun append(value: String) {
            if (value.isEmpty()) return
            val previous = if (result.isEmpty()) precedingText else result.toString()
            val formatted = if (fast) FastVoiceFormatting.format(value, previous, language, protectedWords) else value
            if (result.isNotEmpty()) result.append(' ')
            result.append(formatted)
        }
        chunks.forEach { append(it.text) }
        if (includePartial) append(partial)
        return result.toString()
    }

    private fun punctuationParts(includePartial: Boolean): Pair<String, String> {
        // Accepted rich cleanup owns its prefix (including lists). The small model
        // revisits ALL uncleaned utterances together, with that prefix as context.
        val boundary = chunks.indexOfLast { it.cleaned } + 1
        val prefix = join(chunks.take(boundary).map { it.text })
        val source = join(chunks.drop(boundary).map { it.text } + if (includePartial) listOf(partial) else emptyList())
        return prefix to source
    }

    fun punctuationSnapshot(): PunctuationSnapshot? {
        if (!punctuationEnabled) return null
        val (prefix, source) = punctuationParts(true)
        val request = PunctuationText.prepare(source, prefix.ifEmpty { precedingText }) ?: return null
        return PunctuationSnapshot(request, punctuationGeneration)
    }
    val punctuationPendingText: String get() = punctuationParts(true).second

    fun applyPunctuation(snapshot: PunctuationSnapshot, output: String): Boolean {
        if (snapshot != punctuationSnapshot()) return false
        val candidate = PunctuationText.restore(snapshot.request, output, protectedWords) ?: return false
        punctuationResult = snapshot to candidate
        return true
    }

    fun partial(text: String) { partial = text.trim() }
    fun finish(text: String = partial) {
        val final = text.trim().ifEmpty { partial }
        if (final.isNotEmpty()) chunks.add(Chunk(++nextId, final))
        partial = ""
    }

    /** All finalized speech stays editable. Never silently fall back to the newest chunk. */
    fun snapshot(limit: Int = com.kinetica.keyboard.engine.KineticaConstants.VOICE_CLEANUP_CHARS): Snapshot? {
        if (chunks.isEmpty()) return null
        val input = render(includePartial = false)
        val raw = join(chunks.map { it.raw })
        if (input.length > limit || raw.length > limit) return null
        return Snapshot(chunks.map { it.id }, raw, generation,
            CleanupContext(precedingText.takeLast(com.kinetica.keyboard.engine.KineticaConstants.VOICE_CONTEXT_CHARS),
                partial.take(com.kinetica.keyboard.engine.KineticaConstants.VOICE_FOLLOWING_CHARS)), input)
    }

    fun apply(snapshot: Snapshot, candidate: String): Boolean {
        if (snapshot.generation != generation || !CleanupGuard.accepts(snapshot.original, candidate) ||
            !CleanupGuard.respectsContext(snapshot.original, candidate, snapshot.context)) return false
        val start = chunks.indexOfFirst { it.id == snapshot.ids.first() }
        if (start < 0 || chunks.drop(start).take(snapshot.ids.size).map { it.id } != snapshot.ids) return false
        repeat(snapshot.ids.size) { chunks.removeAt(start) }
        chunks.add(start, Chunk(++nextId, snapshot.original, candidate.trim(), cleaned = true))
        punctuationGeneration++
        punctuationResult = null
        return true
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        generation++
        punctuationGeneration++
        punctuationEnabled = false
        punctuationResult = null
        fastFormatting = false
        chunks.forEach { it.text = it.raw }
        return true
    }

    fun clear() { generation++; punctuationGeneration++; punctuationResult = null; chunks.clear(); partial = "" }
    private fun join(parts: List<String>) = parts.filter { it.isNotEmpty() }.joinToString(" ")
}

/** Allows editing, including deletions and spoken corrections, but rejects obvious model answers. */
object CleanupGuard {
    private val word = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*")
    private val quantities = listOf("zero", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen", "twenty", "hundred", "thousand", "million")
    private val quantityValues = quantities.take(21).mapIndexed { index, word -> word to index.toString() }.toMap()
    private fun numericForm(word: String) = quantityValues[word] ?: word
    /** Never insert a word found only in surrounding, unowned speech. This rejects edits, not formats them. */
    fun respectsContext(original: String, output: String, context: CleanupContext): Boolean {
        val outsideOnly = words(context.before + " " + context.after).toSet() - words(original).toSet()
        return words(output).none { it in outsideOnly }
    }
    private val dates = setOf("today", "tomorrow", "yesterday", "monday", "tuesday", "wednesday",
        "thursday", "friday", "saturday", "sunday")
    private fun words(text: String) = word.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()
    fun accepts(original: String, output: String): Boolean {
        val candidate = output.trim()
        if (candidate.isEmpty() || candidate.length > original.length * 2 + 80 ||
            candidate.contains("```") || candidate.any { it.isISOControl() && it != '\n' && it != '\t' }) return false
        val source = words(original)
        val result = words(candidate)
        if (source.isEmpty() || result.isEmpty() || (source.size >= 12 && result.size < source.size / 4)) return false
        // A small model was observed retaining "today" in "today sorry tomorrow".
        // Reject that known failure rather than silently choose the abandoned date.
        for (i in 1 until source.lastIndex) {
            if (source[i] in setOf("sorry", "actually") && source[i - 1] in dates &&
                source[i + 1] in dates && source[i - 1] != source[i + 1] &&
                source[i - 1] in result && source[i + 1] !in result) return false
        }
        // A selected correction may remove an earlier number; inventing a new number is refused.
        val sourceNumbers = source.filter { token -> token.any { it.isDigit() } || token in quantities }.map(::numericForm)
        val resultNumbers = result.filter { token -> token.any { it.isDigit() } || token in quantities }.map(::numericForm)
        if (sourceNumbers.size == resultNumbers.size && sourceNumbers != resultNumbers) return false
        val numbers = sourceNumbers.toMutableList()
        for (token in resultNumbers) if (!numbers.remove(token)) return false
        val remaining = source.map(::numericForm).toMutableList()
        val retained = result.count { remaining.remove(numericForm(it)) }
        // Explicit spoken corrections intentionally remove the abandoned value and marker.
        val superseded = mutableSetOf<Int>()
        fun quantity(token: String) = token.any { it.isDigit() } || token in quantities
        for (i in 1 until source.lastIndex) {
            if (source[i] in setOf("actually", "sorry") &&
                ((quantity(source[i - 1]) && quantity(source[i + 1])) ||
                    (source[i - 1] in dates && source[i + 1] in dates))) {
                superseded.add(i - 1); superseded.add(i)
            }
        }
        val meaningful = source.filterIndexed { index, _ -> index !in superseded }
            .map(::numericForm).toSet() - setOf("um", "uh", "erm", "hmm")
        val resultWords = result.map(::numericForm).toSet()
        val coverage = com.kinetica.keyboard.engine.KineticaConstants.VOICE_CLEANUP_WORD_COVERAGE
        return retained.toDouble() / result.size >= coverage &&
            (meaningful.isEmpty() || meaningful.count { it in resultWords }.toDouble() / meaningful.size >= coverage)
    }
}
