package co.juneai.june.ml.text

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

class BPETokenizer(
    context: Context,
    tokenizerConfigPath: String = "tokenizer_config.json",
    tokenizerPath: String = "tokenizer.json"
) : Tokenizer() {
    companion object {
        private const val START_TOKEN_DEFAULT = "<|startoftext|>"
        private const val END_TOKEN_DEFAULT = "<|endoftext|>"
        private const val WORD_END = "</w>"

        // Standard regex used by CLIP
        private val PATTERN = Pattern.compile(
            "('s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+)",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val byteEncoder = createCharDict()
    private val encoder: Map<String, Int>
    private val decoder: Map<Int, String>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val cache: MutableMap<String, String> = mutableMapOf()

    private val startToken: String
    private val endToken: String

    init {
        // Parse config
        val configStr = readAsset(context, tokenizerConfigPath)
        val configJson = JSONObject(configStr)
        startToken = configJson.optJSONObject("bos_token")?.optString("content") ?: START_TOKEN_DEFAULT
        endToken = configJson.optJSONObject("eos_token")?.optString("content") ?: END_TOKEN_DEFAULT
        
        if (configJson.has("model_max_length")) {
            contextLength = configJson.getInt("model_max_length")
        }

        // Parse tokenizer details
        val tokenizerStr = readAsset(context, tokenizerPath)
        val tokenizerJson = JSONObject(tokenizerStr)
        val modelJson = tokenizerJson.getJSONObject("model")
        
        // Vocab
        val vocabJson = modelJson.getJSONObject("vocab")
        val parsedEncoder = mutableMapOf<String, Int>()
        val iter = vocabJson.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            parsedEncoder[key] = vocabJson.getInt(key)
        }
        encoder = parsedEncoder
        decoder = encoder.entries.associateBy({ it.value }) { it.key }

        // Merges
        val mergesArr = modelJson.getJSONArray("merges")
        val parsedMerges = mutableMapOf<Pair<String, String>, Int>()
        for (i in 0 until mergesArr.length()) {
            val mergeStr = mergesArr.getString(i)
            val parts = mergeStr.split(" ")
            if (parts.size == 2) {
                parsedMerges[Pair(parts[0], parts[1])] = i
            }
        }
        bpeRanks = parsedMerges

        cache[startToken] = startToken
        cache[endToken] = endToken
    }

    private fun createCharDict(): Map<Int, Char> {
        val bytesList = mutableListOf<Int>()
        bytesList.addAll(33..126)
        bytesList.addAll(161..172)
        bytesList.addAll(174..255)
        val charList = bytesList.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bytesList) {
                bytesList.add(b)
                charList.add(256 + n)
                n++
            }
        }
        return bytesList.zip(charList.map { it.toChar() }).toMap()
    }

    private fun readAsset(context: Context, assetName: String): String {
        return context.assets.open(assetName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.readText()
            }
        }
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        return word.zipWithNext().map { it.first to it.second }.toSet()
    }

    private fun whitespaceClean(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun bpe(token: String): String {
        cache[token]?.let { return it }

        var word = token.map { it.toString() }.toMutableList()
        if (word.isNotEmpty()) {
            word[word.size - 1] = word.last() + WORD_END
        }
        var pairs = getPairs(word)

        if (pairs.isEmpty()) return token + WORD_END

        while (true) {
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break

            val (first, second) = bigram
            val newWord = mutableListOf<String>()
            var i = 0

            while (i < word.size) {
                val j = word.subList(i, word.size).indexOf(first).takeIf { it != -1 }?.plus(i) ?: word.size
                newWord.addAll(word.subList(i, j))
                i = j

                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else if (i < word.size) {
                    newWord.add(word[i])
                    i++
                }
            }
            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }

        return word.joinToString(" ").also { cache[token] = it }
    }

    private fun encode(text: String): List<Int> {
        val cleanedText = whitespaceClean(text).lowercase()
        val matcher = PATTERN.matcher(cleanedText)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches.add(matcher.group())
        }
        
        val bpeTokens = mutableListOf<Int>()
        for (token in matches) {
            val encodedToken = token.toByteArray(Charsets.UTF_8).map { byteEncoder[it.toInt() and 0xFF] ?: '?' }.joinToString("")
            for (bpeToken in bpe(encodedToken).split(" ")) {
                encoder[bpeToken]?.let { bpeTokens.add(it) }
            }
        }
        return bpeTokens
    }

    override fun tokenize(text: String): Pair<IntArray, LongArray> {
        val sotToken = encoder[startToken] ?: 49406
        val eotToken = encoder[endToken] ?: 49407
        val tokens = mutableListOf<Int>()
        tokens.add(sotToken)
        tokens.addAll(encode(text))
        tokens.add(eotToken)

        if (tokens.size > contextLength) {
            if (truncate) {
                val truncatedTokens = tokens.subList(0, contextLength)
                truncatedTokens[contextLength - 1] = eotToken
                return Pair(truncatedTokens.toIntArray(), longArrayOf(1, contextLength.toLong()))
            } else {
                throw RuntimeException("Input text is too long for context length $contextLength")
            }
        }
        
        val result = IntArray(contextLength) {
            if (it < tokens.size) tokens[it] else 0
        }
        val shape = longArrayOf(1, contextLength.toLong())
        return Pair(result, shape)
    }
}
