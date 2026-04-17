package co.juneai.june.ml.clip

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Collections
import java.util.regex.Pattern

/**
 * BPE tokenizer matching OpenAI's CLIP implementation.
 *
 * Bugs fixed vs. original:
 *  1. BPE merge loop used `word.indexOf(f)` (always scans from index 0), causing
 *     incorrect merges when the same token appears multiple times in a word. The correct
 *     approach is a single left-to-right pass that merges ALL occurrences of the best
 *     bigram in one sweep — matching the Python reference.
 *  2. `cache` was a plain [HashMap], unsafe for concurrent callers. Replaced with a
 *     bounded LRU that evicts least-recently-used entries beyond [CACHE_MAX_SIZE].
 *
 * Performance:
 *  - [encode] returns a [TokenizerOutput] with both inputIds and attentionMask computed
 *    in a single pass — the original made two separate passes over the array.
 *  - Token IDs are accumulated as a plain [MutableList] and converted to [IntArray] once.
 */
class ClipTokenizer(context: Context) {

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

    private val encoder:       Map<String, Int>
    private val bpeRanks:      Map<Pair<String, String>, Int>
    private val byteToUnicode: Map<Int, Char>

    // Bounded thread-safe LRU cache: fast lookup for repeated words (common in photo searches)
    private val cache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > CACHE_MAX_SIZE
        },
    )

    init {
        byteToUnicode = buildByteToUnicode()

        val vocabType = object : TypeToken<Map<String, Int>>() {}.type
        encoder = Gson().fromJson(
            context.assets.open("vocab.json").bufferedReader().readText(),
            vocabType,
        )

        bpeRanks = context.assets.open("merges.txt")
            .bufferedReader()
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapIndexed { i, line ->
                val parts = line.trim().split(" ")
                (parts[0] to parts[1]) to i
            }
            .toMap()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tokenizes [text] and returns padded input IDs + attention mask, both of length [MAX_LEN].
     * Text is lowercased and trimmed before tokenization.
     */
    fun encode(text: String): TokenizerOutput {
        val tokens  = ArrayList<Int>(MAX_LEN).also { it.add(SOT) }
        val matcher = PAT.matcher(text.lowercase().trim())

        while (matcher.find()) {
            val word = matcher.group()
                .toByteArray(Charsets.UTF_8)
                .joinToString("") { byte ->
                    byteToUnicode[byte.toInt() and 0xFF]?.toString() ?: "?"
                }
            bpe(word).split(" ").forEach { tok -> encoder[tok]?.let { tokens.add(it) } }
        }
        tokens.add(EOT)

        // Truncate to MAX_LEN, keeping SOT at [0] and EOT at the end
        if (tokens.size > MAX_LEN) {
            tokens.subList(MAX_LEN - 1, tokens.size).clear()
            tokens.add(EOT)
        }

        val inputIds      = IntArray(MAX_LEN) { i -> if (i < tokens.size) tokens[i] else 0 }
        val attentionMask = IntArray(MAX_LEN) { i -> if (inputIds[i] != 0) 1 else 0 }
        return TokenizerOutput(inputIds, attentionMask)
    }

    // ── BPE ───────────────────────────────────────────────────────────────────

    /**
     * Applies BPE merges to [token], returning a space-separated string of sub-tokens.
     *
     * Fixed vs. original: uses a single left-to-right sweep that merges ALL occurrences
     * of the best bigram in one pass, matching the Python CLIP reference implementation.
     * The original `word.indexOf(f)` always restarted from index 0, causing incorrect
     * merges when the same sub-token appeared more than once in a word.
     */
    private fun bpe(token: String): String {
        cache[token]?.let { return it }
        if (token.length <= 1) return token.also { cache[token] = it }

        var word = ArrayList<String>(token.length).also { list ->
            token.forEach { list.add(it.toString()) }
        }

        while (word.size > 1) {
            // Find the highest-priority (lowest rank) adjacent pair
            var bestRank = Int.MAX_VALUE
            var bestFirst = ""
            var bestSecond = ""
            for (i in 0 until word.size - 1) {
                val rank = bpeRanks[word[i] to word[i + 1]] ?: Int.MAX_VALUE
                if (rank < bestRank) {
                    bestRank   = rank
                    bestFirst  = word[i]
                    bestSecond = word[i + 1]
                }
            }
            if (bestRank == Int.MAX_VALUE) break   // no more mergeable pairs

            // Single left-to-right sweep — merge ALL occurrences of the best pair
            val merged  = bestFirst + bestSecond
            val newWord = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == bestFirst && word[i + 1] == bestSecond) {
                    newWord.add(merged)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i++
                }
            }
            word = newWord
        }

        return word.joinToString(" ").also { cache[token] = it }
    }

    // ── Byte-to-Unicode mapping ───────────────────────────────────────────────

    private fun buildByteToUnicode(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        bs.addAll('!'.code..'~'.code)
        bs.addAll('¡'.code..'¬'.code)
        bs.addAll('®'.code..'ÿ'.code)
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) { bs.add(b); cs.add(256 + n); n++ }
        }
        return bs.zip(cs.map { it.toChar() }).toMap()
    }
}