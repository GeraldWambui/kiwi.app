package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.juneai.june.data.local.entities.FaceEmbeddingEntity

@Dao
interface FaceEmbeddingDao {
    @Insert
    suspend fun insert(face: FaceEmbeddingEntity): Long

    @Insert
    suspend fun insertAll(faces: List<FaceEmbeddingEntity>)

    @Query("SELECT * FROM face_embeddings WHERE mediaId = :mediaId")
    suspend fun getFacesByMedia(mediaId: Long): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings WHERE clusterId = :clusterId")
    suspend fun getFacesByCluster(clusterId: Long): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings")
    suspend fun getAllFaces(): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_embeddings WHERE mediaId = :mediaId")
    suspend fun deleteFacesByMedia(mediaId: Long)

    @Query("UPDATE face_embeddings SET clusterId = :clusterId WHERE id = :faceId")
    suspend fun updateClusterId(faceId: Long, clusterId: Long)
}