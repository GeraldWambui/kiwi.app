package co.juneai.june.ml.clip

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Collections
import java.util.regex.Pattern

/**
 * BPE tokenizer matching OpenAI's CLIP implementation.
 *
 * Designed with lazy on-demand asset loading so that constructing [ClipTokenizer]
 * takes 0ms on the main thread and never causes an ANR.
 */
class ClipTokenizer(private val context: Context) {

    companion object {
        const val MAX_LEN = 77

        private const val SOT           = 49406
        private const val EOT           = 49407
        private const val CACHE_MAX_SIZE = 8_192

        private val PAT = Pattern.compile(
            """<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""",
            Pattern.CASE_INSENSITIVE,
        )
    }

    /** Combined output from [encode] — avoids a second array scan for the attention mask. */
    data class TokenizerOutput(
        val inputIds:      IntArray,
        val attentionMask: IntArray,
    )

    private val byteToUnicode: Map<Int, Char> = buildByteToUnicode()

    @Volatile
    private var encoder: Map<String, Int>? = null

    @Volatile
    private var bpeRanks: Map<Pair<String, String>, Int>? = null

    private val initLock = Any()

    // Bounded thread-safe LRU cache: fast lookup for repeated words (common in photo searches)
    private val cache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > CACHE_MAX_SIZE
        },
    )

    private fun ensureLoaded() {
        if (encoder == null || bpeRanks == null) {
            synchronized(initLock) {
                if (encoder == null || bpeRanks == null) {
                    val vocabType = object : TypeToken<Map<String, Int>>() {}.type
                    val loadedEncoder: Map<String, Int> = Gson().fromJson(
                        context.assets.open("vocab.json").bufferedReader().readText(),
                        vocabType,
                    )
                    val loadedBpeRanks: Map<Pair<String, String>, Int> = context.assets.open("merges.txt")
                        .bufferedReader()
                        .lineSequence()
                        .drop(1)
                        .filter { it.isNotBlank() }
                        .mapIndexed { i, line ->
                            val parts = line.trim().split(" ")
                            (parts[0] to parts[1]) to i
                        }
                        .toMap()

                    encoder = loadedEncoder
                    bpeRanks = loadedBpeRanks
                }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tokenizes [text] and returns padded input IDs + attention mask, both of length [MAX_LEN].
     * Text is lowercased and trimmed before tokenization.
     */
    fun encode(text: String): TokenizerOutput {
        ensureLoaded()
        val enc = encoder ?: emptyMap()
        val tokens  = ArrayList<Int>(MAX_LEN).also { it.add(SOT) }
        val matcher = PAT.matcher(text.lowercase().trim())

        while (matcher.find()) {
            val word = matcher.group()
                .toByteArray(Charsets.UTF_8)
                .joinToString("") { byte ->
                    byteToUnicode[byte.toInt() and 0xFF]?.toString() ?: "?"
                }
            bpe(word).split(" ").forEach { tok -> enc[tok]?.let { tokens.add(it) } }
        }
        tokens.add(EOT)

        // Truncate to MAX_LEN, keeping SOT at [0] and EOT at the end
        if (tokens.size > MAX_LEN) {
            tokens[MAX_LEN - 1] = EOT
            tokens.subList(MAX_LEN, tokens.size).clear()
        }

        val actualLen     = tokens.size
        val inputIds      = IntArray(MAX_LEN)
        val attentionMask = IntArray(MAX_LEN)

        for (i in 0 until actualLen) {
            inputIds[i]      = tokens[i]
            attentionMask[i] = 1
        }
        // Remaining slots are 0 (PAD) and 0 (masked out) by default in IntArray

        return TokenizerOutput(inputIds, attentionMask)
    }

    // ── Private BPE implementation ────────────────────────────────────────────

    private fun bpe(token: String): String {
        cache[token]?.let { return it }
        ensureLoaded()
        val ranks = bpeRanks ?: return token

        // Special case: single character tokens need no merging
        if (token.length <= 1) {
            cache[token] = token
            return token
        }

        // Represent word as a list of sub-tokens
        var word = token.map { it.toString() }
        var pairs = getPairs(word)
        if (pairs.isEmpty()) return token

        while (true) {
            // Find the bigram with the lowest rank (highest priority merge)
            val minPair = pairs.minByOrNull { ranks[it] ?: Int.MAX_VALUE } ?: break
            if (minPair !in ranks) break

            val first = minPair.first
            val second = minPair.second

            // Single left-to-right pass to merge ALL occurrences of this best bigram.
            val newWord = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i++
                }
            }

            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }

        val result = word.joinToString(" ")
        cache[token] = result
        return result
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = HashSet<Pair<String, String>>(word.size - 1)
        for (i in 0 until word.size - 1) {
            pairs.add(word[i] to word[i + 1])
        }
        return pairs
    }

    private fun buildByteToUnicode(): Map<Int, Char> {
        val bs = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toMutableList()
        val cs = bs.toMutableList()
        var n  = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        return bs.zip(cs.map { it.toChar() }).toMap()
    }
}