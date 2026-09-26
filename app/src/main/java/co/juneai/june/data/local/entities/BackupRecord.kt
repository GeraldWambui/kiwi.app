package co.juneai.june.data.local.entities

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class BackupRecord(
    @Id var id: Long = 0,
    @Index var mediaStoreId: Long = 0,
    var remoteAssetId: String = "",
    var checksumSha256: String = "",
    var providerName: String = "", // IMMICH, NEXTCLOUD, WEBDAV
    var backedUpAt: Long = 0L,
    var fileSize: Long = 0L,
    var isSuccess: Boolean = true
)
