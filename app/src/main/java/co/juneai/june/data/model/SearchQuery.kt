package co.juneai.june.data.model

import android.graphics.Rect

data class SearchQuery(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filters: SearchFilters = SearchFilters()
)

data class SearchFilters(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minWidth: Int? = null,
    val excludeNSFW: Boolean = false,
    val colorFilter: Int? = null,
    val location: String? = null,
    val person: String? = null
)

data class SearchResultItem(
    val mediaId: Long,
    val uri: String,
    val similarity: Float,
    val matchType: MatchType = MatchType.SEMANTIC,
    val confidence: Float = 0.8f
)

enum class MatchType {
    SEMANTIC, TEXT_IN_IMAGE, FACE, OBJECT, TEMPORAL, LOCATION, COLOR
}

// Commands
data class CommandIntent(
    val type: CommandType,
    val target: String,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 0f,
    val requiresConfirmation: Boolean = true
)

enum class CommandType {
    DELETE, SHARE, MOVE, ORGANIZE, VAULT, SELECT, SEARCH, NONE
}

data class CommandResult(
    val success: Boolean,
    val affectedItemsCount: Int = 0,
    val message: String = "",
    val itemsPreview: List<Long> = emptyList(),
    val undoable: Boolean = true
)

// Face Recognition
data class FaceDetectionResult(
    val faceId: String,
    val embeddings: FloatArray,
    val boundingBox: Rect,
    val confidence: Float,
    val mediaId: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceDetectionResult) return false
        return faceId == other.faceId && mediaId == other.mediaId
    }

    override fun hashCode(): Int = faceId.hashCode()
}

data class FaceClusterData(
    val clusterId: Long,
    val personName: String? = null,
    val mediaIds: List<Long>,
    val confidence: Float,
    val isMainUser: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

// OCR Results
data class TextDetectionResult(
    val text: String,
    val confidence: Float,
    val boundingBoxes: List<Rect> = emptyList(),
    val mediaId: Long
)

// NSFW Detection
data class NSFWScore(
    val score: Float, // 0.0 to 1.0
    val category: NSFWCategory,
    val mediaId: Long
)

enum class NSFWCategory {
    SAFE, SUGGESTIVE, EXPLICIT
}

// Voice Command
data class VoiceCommandResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean = false,
    val error: String? = null
)

// Object Detection
data class ObjectDetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect,
    val areaRatio: Float,
    val isPrimary: Boolean,
    val mediaId: Long
)


// Color Analysis
data class ColorAnalysis(
    val dominantColor: Int,
    val colorPalette: List<Int> = emptyList(),
    val mediaId: Long
)

// Undo/Redo
data class UndoRedoAction(
    val id: String,
    val type: String,
    val originalState: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis(),
    val canUndo: Boolean = true
)