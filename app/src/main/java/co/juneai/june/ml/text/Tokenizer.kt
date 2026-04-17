package co.juneai.june.ml.text

abstract class Tokenizer(
    protected var contextLength: Int = 77,
    protected var truncate: Boolean = true
) {
    abstract fun tokenize(text: String): Pair<IntArray, LongArray>
}
