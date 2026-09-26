package co.juneai.june.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.juneai.june.data.local.entities.FaceClusterEntity

data class FaceClusterSummary(
    val clusterId: String,
    val personName: String,
    val photoCount: Int,
    val coverMediaStoreId: Long
)

@Dao
interface FaceClusterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FaceClusterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FaceClusterEntity>)

    @Query("SELECT * FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun getFacesForCluster(clusterId: String): List<FaceClusterEntity>

    @Query("SELECT DISTINCT mediaStoreId FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun getMediaIdsForCluster(clusterId: String): List<Long>

    @Query("SELECT * FROM face_clusters WHERE mediaStoreId = :mediaStoreId")
    suspend fun getFacesForMedia(mediaStoreId: Long): List<FaceClusterEntity>

    @Query("""
        SELECT clusterId, 
               MAX(personName) as personName, 
               COUNT(DISTINCT mediaStoreId) as photoCount, 
               MAX(mediaStoreId) as coverMediaStoreId 
        FROM face_clusters 
        GROUP BY clusterId 
        HAVING photoCount > 0 
        ORDER BY photoCount DESC
    """)
    suspend fun getAllClusterSummaries(): List<FaceClusterSummary>

    @Query("SELECT * FROM face_clusters")
    suspend fun getAllFaces(): List<FaceClusterEntity>

    @Query("UPDATE face_clusters SET personName = :name WHERE clusterId = :clusterId")
    suspend fun updatePersonName(clusterId: String, name: String)

    @Query("UPDATE face_clusters SET clusterId = :targetClusterId WHERE clusterId = :sourceClusterId")
    suspend fun mergeClusters(sourceClusterId: String, targetClusterId: String)

    @Query("DELETE FROM face_clusters WHERE clusterId = :clusterId")
    suspend fun deleteCluster(clusterId: String)

    @Query("""
        SELECT clusterId, 
               MAX(personName) as personName, 
               COUNT(DISTINCT mediaStoreId) as photoCount, 
               MAX(mediaStoreId) as coverMediaStoreId 
        FROM face_clusters 
        WHERE clusterId = :clusterId
        GROUP BY clusterId
    """)
    suspend fun getClusterSummary(clusterId: String): FaceClusterSummary?

    @Query("DELETE FROM face_clusters WHERE mediaStoreId = :mediaStoreId")
    suspend fun deleteByMediaId(mediaStoreId: Long)

    @Query("DELETE FROM face_clusters")
    suspend fun clearAll()
}
