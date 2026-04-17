package co.juneai.june.data.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import java.util.Date

@Entity
data class SearchImageEntity(
    @Id var id: Long = 0,
    
    @Index
    var filePath: String = "",
    
    var fileName: String = "",
    var fileSize: Long = 0,
    var mimeType: String = "",
    var dateAdded: Date = Date(),
    var dateModified: Date = Date(),
    var width: Int = 0,
    var height: Int = 0,
    
    // CLIP embedding vector - 512 dimensions for ViT-B/32
    @HnswIndex(
        dimensions = 512,
        distanceType = io.objectbox.annotation.VectorDistanceType.COSINE
    )
    var embedding: FloatArray? = null,
    
    // Metadata for filtering
    var hasFace: Boolean = false,
    var locationLat: Double? = null,
    var locationLng: Double? = null
) {
    @Backlink(to = "image")
    lateinit var tags: ToMany<SearchTagEntity>
}
