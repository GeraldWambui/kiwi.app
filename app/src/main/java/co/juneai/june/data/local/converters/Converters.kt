package co.juneai.june.data.local.converters

import androidx.room.TypeConverter
import co.juneai.june.data.model.VaultMediaType

class Converters {
    @TypeConverter
    fun fromMediaType(type: VaultMediaType): String = type.name
    
    @TypeConverter
    fun toMediaType(type: String): VaultMediaType = try {
        VaultMediaType.valueOf(type)
    } catch (_: Exception) {
        VaultMediaType.IMAGE
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String {
        if (array == null || array.isEmpty()) return ""
        return array.joinToString(separator = ",")
    }

    @TypeConverter
    fun toFloatArray(data: String?): FloatArray? {
        if (data.isNullOrBlank()) return null
        return try {
            data.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
        } catch (_: Exception) {
            null
        }
    }
}