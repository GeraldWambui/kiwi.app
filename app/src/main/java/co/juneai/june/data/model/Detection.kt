package co.juneai.june.data.model

import co.juneai.june.data.repository.nsfw.NudeLabel

data class Detection(
    val label: NudeLabel,
    val confidence: Float,
    val box: FloatArray // [x1, y1, x2, y2] normalized
) {
    val isSensitive: Boolean get() = label.isSensitive

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Detection) return false
        return label == other.label && confidence == other.confidence && box.contentEquals(other.box)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + box.contentHashCode()
        return result
    }
}