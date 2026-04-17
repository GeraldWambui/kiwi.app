package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.FaceClusterEntity
import co.juneai.june.data.local.entities.FaceClusterMappingEntity
import co.juneai.june.data.local.entities.FaceEmbeddingEntity

@Dao
interface FaceClusterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCluster(cluster: FaceClusterEntity): Long

    @Insert
    suspend fun insertMapping(mapping: FaceClusterMappingEntity)

    @Insert
    suspend fun insertMappings(mappings: List<FaceClusterMappingEntity>)

    @Query("SELECT * FROM face_clusters WHERE isMainUser = 1")
    suspend fun getMainUserCluster(): FaceClusterEntity?

    @Query("SELECT * FROM face_clusters WHERE personName = :personName")
    suspend fun getClusterByName(personName: String): FaceClusterEntity?

    @Query("SELECT * FROM face_clusters ORDER BY mediaCount DESC")
    suspend fun getAllClusters(): List<FaceClusterEntity>

    @Query("""
        SELECT fe.* FROM face_embeddings fe
        INNER JOIN face_cluster_mapping fcm ON fe.id = fcm.faceId
        WHERE fcm.clusterId = :clusterId
    """)
    suspend fun getMediaInCluster(clusterId: Long): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun deleteCluster(clusterId: Long)

    @Query("UPDATE face_clusters SET personName = :personName WHERE clusterId = :clusterId")
    suspend fun updatePersonName(clusterId: Long, personName: String)

    @Query("UPDATE face_clusters SET isMainUser = 1 WHERE clusterId = :clusterId")
    suspend fun markAsMainUser(clusterId: Long)
}