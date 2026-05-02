package co.juneai.june.data.local.converters

import androidx.room.TypeConverter
import co.juneai.june.data.model.VaultMediaType

class Converters {
    @TypeConverter
    fun fromMediaType(type: VaultMediaType): String = type.name
    
    @TypeConverter
    fun toMediaType(type: String): VaultMediaType = VaultMediaType.valueOf(type)
}