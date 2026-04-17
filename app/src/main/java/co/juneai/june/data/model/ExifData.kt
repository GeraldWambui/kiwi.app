package co.juneai.june.data.model

data class ExifData(
    val dateTaken: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraModel: String? = null,
    val isoValue: Int? = null,
    val exposureTime: Float? = null,
    val focalLength: Float? = null,
    val aperture: Float? = null
)