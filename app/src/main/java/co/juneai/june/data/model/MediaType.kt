package co.juneai.june.data.model

enum class VaultMediaType {
    IMAGE, VIDEO;

    fun getMimeType(): String {
        return when (this) {
            IMAGE -> "image/*"
            VIDEO -> "video/*"
        }
    }
}