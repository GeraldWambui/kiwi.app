package co.juneai.june.domain.repository

import android.content.Context
import android.graphics.Bitmap
import co.juneai.june.data.model.Detection

interface NudeDetectorRepository{
    fun detect(bitmap: Bitmap): List<Detection>
    fun containsNsfw(bitmap: Bitmap): Boolean
    fun close()
}