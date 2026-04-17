package co.juneai.june.utils// ByteArrayExtensions.kt
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts ByteArray to FloatArray
 * @param byteOrder ByteOrder.LITTLE_ENDIAN or ByteOrder.BIG_ENDIAN
 * @return FloatArray
 */
fun ByteArray.toFloatArray(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): FloatArray {
    if (this.isEmpty()) return FloatArray(0)
    
    val byteBuffer = ByteBuffer.wrap(this)
    byteBuffer.order(byteOrder)
    
    val floatBuffer = byteBuffer.asFloatBuffer()
    val floatArray = FloatArray(floatBuffer.remaining())
    floatBuffer.get(floatArray)
    
    return floatArray
}

/**
 * Converts FloatArray to ByteArray
 * @param byteOrder ByteOrder.LITTLE_ENDIAN or ByteOrder.BIG_ENDIAN
 * @return ByteArray
 */
fun FloatArray.toByteArray(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray {
    val byteBuffer = ByteBuffer.allocate(this.size * 4)
    byteBuffer.order(byteOrder)
    
    this.forEach { float ->
        byteBuffer.putFloat(float)
    }
    
    return byteBuffer.array()
}

/**
 * Optimized version for 128D embeddings (fixed size)
 */
fun ByteArray.toFloatArray128(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): FloatArray {
    require(this.size == 128 * 4) { "ByteArray size must be 512 bytes for 128D float array" }
    
    val result = FloatArray(128)
    val byteBuffer = ByteBuffer.wrap(this)
    byteBuffer.order(byteOrder)
    
    for (i in 0 until 128) {
        result[i] = byteBuffer.float
    }
    
    return result
}

/**
 * Optimized version for 512D embeddings (fixed size)
 */
fun ByteArray.toFloatArray512(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): FloatArray {
    require(this.size == 512 * 4) { "ByteArray size must be 2048 bytes for 512D float array" }
    
    val result = FloatArray(512)
    val byteBuffer = ByteBuffer.wrap(this)
    byteBuffer.order(byteOrder)
    
    for (i in 0 until 512) {
        result[i] = byteBuffer.float
    }
    
    return result
}