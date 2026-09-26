package co.juneai.june.ui.screen.mediadetail

import android.content.Intent
import android.graphics.Bitmap
import co.juneai.june.domain.model.MediaDetail
import co.juneai.june.ml.portrait.PortraitLightingStyle

data class MediaAiMetadata(
    val ocrText: String? = null,
    val textSnippet: String? = null,
    val primaryObject: String? = null,
    val sharpnessScore: Float = 0f,
    val isBlurry: Boolean = false,
    val aestheticScore: Float = 0f,
    val faceCount: Int = 0,
    val isNsfw: Boolean = false,
    val nsfwScore: Float = 0f,
    val personNames: List<String> = emptyList()
)

data class MediaDetailState(
    val media: MediaDetail? = null,
    val aiMetadata: MediaAiMetadata? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showInfoSheet: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val showMoreOptionsDialog: Boolean = false,
    val shareIntent: Intent? = null,
    val wallaperIntent: Intent? = null,
    val mapIntent: Intent? = null,
    val isRotating: Boolean = false,
    val mediaIds: List<Long> = emptyList(),
    val currentIndex: Int = 0,
    val cachedMediaMap: Map<Long, MediaDetail> = emptyMap(),
    
    // Computational Photography State
    val isSubjectLiftActive: Boolean = false,
    val subjectCutoutBitmap: Bitmap? = null,
    val isSubjectLiftLoading: Boolean = false,
    val isPortraitModeActive: Boolean = false,
    val portraitBlurIntensity: Float = 0.65f,
    val portraitLightingStyle: PortraitLightingStyle = PortraitLightingStyle.NATURAL_BOKEH,
    val portraitRenderedBitmap: Bitmap? = null,
    val isPortraitRendering: Boolean = false
)