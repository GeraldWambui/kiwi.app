package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.juneai.june.data.local.entities.ObjectDetectionEntity

@Dao
interface ObjectDetectionDao {
    @Insert
    suspend fun insert(detection: ObjectDetectionEntity)

    @Insert
    suspend fun insertAll(detections: List<ObjectDetectionEntity>)

    @Query("SELECT * FROM object_detections WHERE mediaId = :mediaId")
    suspend fun getDetectionsByMedia(mediaId: Long): List<ObjectDetectionEntity>

    @Query("SELECT DISTINCT label FROM object_detections")
    suspend fun getUniqueLabels(): List<String>

    @Query("SELECT * FROM object_detections WHERE label = :label")
    suspend fun getDetectionsByLabel(label: String): List<ObjectDetectionEntity>

    @Query("DELETE FROM object_detections WHERE mediaId = :mediaId")
    suspend fun deleteDetectionsByMedia(mediaId: Long)
}