package co.juneai.june.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class SearchTagEntity(
    @Id var id: Long = 0,
    var name: String = "",
    var confidence: Float = 0f
) {
    lateinit var image: ToOne<SearchImageEntity>
}
