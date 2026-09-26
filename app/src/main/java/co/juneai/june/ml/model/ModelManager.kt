package co.juneai.june.ml.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val description: String,
        val sizeFormatted: String,
        val isBundled: Boolean,
        val isDownloaded: Boolean,
        val downloadUrl: String? = null
    )

    data class ModelPart(
        val name: String,
        val size: Long,
        val offset: Long
    )

    companion object {
        val KEY_ACTIVE_MODEL = stringPreferencesKey("active_ml_model_id")
        const val MODEL_MOBILECLIP_S2 = "mobileclip_s2"
        const val DEFAULT_MODEL_ID = MODEL_MOBILECLIP_S2
        const val MOBILECLIP_S2_SIZE_BYTES = 396881784L
        const val CLOUDFLARE_CDN_BASE = "https://kiwi-model-server.kighurugerald.workers.dev"

        val MOBILECLIP_S2_PARTS = listOf(
            ModelPart("mobileclip_s2.tflite.part_0", 134217728L, 0L),
            ModelPart("mobileclip_s2.tflite.part_1", 134217728L, 134217728L),
            ModelPart("mobileclip_s2.tflite.part_2", 128446328L, 268435456L)
        )
    }

    val activeModelIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MODEL] ?: DEFAULT_MODEL_ID
    }

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun isPrimaryModelDownloaded(): Boolean {
        val target = File(modelsDir, "$MODEL_MOBILECLIP_S2.tflite")
        if (target.exists() && target.length() >= 390_000_000L) {
            return true
        }
        // Fallback: Check if bundled in assets during development
        return try {
            context.assets.openFd("$MODEL_MOBILECLIP_S2.tflite").use { it.declaredLength > 0 }
        } catch (_: Exception) {
            false
        }
    }

    fun isModelFileValid(fileName: String, minBytes: Long): Boolean {
        val f = File(modelsDir, fileName)
        return f.exists() && f.length() >= minBytes
    }

    fun getAvailableModels(): List<ModelInfo> {
        val downloadedMobileClipS2 = isPrimaryModelDownloaded()
        val downloadedMobileClipS0 = isModelFileValid("mobileclip_s0.tflite", 40_000_000L)

        return listOf(
            ModelInfo(
                id = MODEL_MOBILECLIP_S2,
                displayName = "Apple MobileCLIP-S2 (Cloudflare CDN)",
                description = "High-accuracy vision-text embedding model for semantic search & zero-shot detection.",
                sizeFormatted = "396 MB",
                isBundled = false,
                isDownloaded = downloadedMobileClipS2,
                downloadUrl = "$CLOUDFLARE_CDN_BASE/mobileclip_s2.tflite"
            ),
            ModelInfo(
                id = "mobileclip_s0",
                displayName = "MobileCLIP-S0 (Fast On-Device)",
                description = "Lightweight, ultra-fast embedding model optimized for mobile CPUs.",
                sizeFormatted = "48 MB",
                isBundled = false,
                isDownloaded = downloadedMobileClipS0,
                downloadUrl = "https://huggingface.co/AKA-QSH/clip_tflite/resolve/main/clip_image_encoder_float16.tflite"
            ),
            ModelInfo(
                id = "nsfw_classifier_v1",
                displayName = "Zero-Shot Content Guardian",
                description = "On-device explicit and sensitive content classifier with adjustable thresholds.",
                sizeFormatted = "Built-in",
                isBundled = true,
                isDownloaded = true
            )
        )
    }

    suspend fun setActiveModel(modelId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_MODEL] = modelId
        }
    }

    suspend fun downloadModel(
        modelId: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = downloadModel(modelId) { progress, _, _, _ -> onProgress(progress) }

    suspend fun downloadModel(
        modelId: String,
        onProgressUpdate: (progress: Float, downloadedBytes: Long, totalBytes: Long, partIndex: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (modelId == MODEL_MOBILECLIP_S2) {
            return@withContext downloadMultipartModel(
                fileName = "$MODEL_MOBILECLIP_S2.tflite",
                totalExpected = MOBILECLIP_S2_SIZE_BYTES,
                parts = MOBILECLIP_S2_PARTS,
                onProgressUpdate = onProgressUpdate
            )
        }

        // Generic single-file download fallback
        val modelInfo = getAvailableModels().find { it.id == modelId } ?: return@withContext false
        val downloadUrl = modelInfo.downloadUrl ?: return@withContext false
        val targetFile = File(modelsDir, "$modelId.tflite")
        val partFile = File(modelsDir, "$modelId.part")

        return@withContext downloadSingleFile(
            downloadUrl = downloadUrl,
            targetFile = targetFile,
            partFile = partFile,
            onProgress = { progress, dl, total ->
                onProgressUpdate(progress, dl, total, -1)
            }
        )
    }

    private suspend fun downloadMultipartModel(
        fileName: String,
        totalExpected: Long,
        parts: List<ModelPart>,
        onProgressUpdate: (progress: Float, downloadedBytes: Long, totalBytes: Long, partIndex: Int) -> Unit
    ): Boolean {
        val targetFile = File(modelsDir, fileName)
        if (targetFile.exists() && targetFile.length() == totalExpected) {
            onProgressUpdate(1f, totalExpected, totalExpected, parts.size - 1)
            return true
        }

        val downloadedPartFiles = mutableListOf<File>()

        for ((idx, part) in parts.withIndex()) {
            val partFile = File(modelsDir, "${fileName}.part_${idx}")
            val partDownloadUrl = "$CLOUDFLARE_CDN_BASE/${part.name}"

            val partSuccess = downloadSingleFileWithOffset(
                downloadUrl = partDownloadUrl,
                partFile = partFile,
                expectedSize = part.size,
                baseCompletedBytes = part.offset,
                totalModelBytes = totalExpected,
                partIndex = idx,
                onProgressUpdate = onProgressUpdate
            )

            if (!partSuccess || partFile.length() != part.size) {
                Timber.e("Failed to download model chunk: ${part.name}")
                return false
            }

            downloadedPartFiles.add(partFile)
        }

        // Merge downloaded chunks into final .tflite target file
        try {
            val tempMerged = File(modelsDir, "$fileName.tmp")
            if (tempMerged.exists()) tempMerged.delete()

            FileOutputStream(tempMerged).use { output ->
                val buffer = ByteArray(65536)
                for (partFile in downloadedPartFiles) {
                    FileInputStream(partFile).use { input ->
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                        }
                    }
                }
                output.flush()
            }

            if (tempMerged.length() == totalExpected) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempMerged.renameTo(targetFile)
                if (renamed) {
                    // Clean up temporary chunk files
                    downloadedPartFiles.forEach { it.delete() }
                    onProgressUpdate(1f, totalExpected, totalExpected, parts.size - 1)
                    Timber.i("✓ Successfully downloaded and assembled $fileName from Cloudflare CDN")
                    return true
                }
            }
            tempMerged.delete()
        } catch (e: Exception) {
            Timber.e(e, "Error merging downloaded parts into $fileName")
        }

        return false
    }

    private suspend fun downloadSingleFileWithOffset(
        downloadUrl: String,
        partFile: File,
        expectedSize: Long,
        baseCompletedBytes: Long,
        totalModelBytes: Long,
        partIndex: Int,
        onProgressUpdate: (progress: Float, downloadedBytes: Long, totalBytes: Long, partIndex: Int) -> Unit
    ): Boolean {
        if (partFile.exists() && partFile.length() == expectedSize) {
            val totalDl = baseCompletedBytes + expectedSize
            onProgressUpdate(totalDl.toFloat() / totalModelBytes, totalDl, totalModelBytes, partIndex)
            return true
        }

        val maxRetries = 3
        var attempt = 0

        while (attempt < maxRetries) {
            attempt++
            var connection: HttpURLConnection? = null
            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val url = URL(downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 40000
                connection.requestMethod = "GET"

                if (existingBytes > 0L && existingBytes < expectedSize) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                    Timber.d("Resuming chunk $downloadUrl from $existingBytes bytes (attempt $attempt)")
                } else if (existingBytes >= expectedSize) {
                    partFile.delete()
                }

                connection.connect()
                val responseCode = connection.responseCode

                val isAppend = (responseCode == HttpURLConnection.HTTP_PARTIAL)
                val streamStartBytes = if (isAppend) existingBytes else 0L

                var currentPartBytes = streamStartBytes
                connection.inputStream.use { input ->
                    FileOutputStream(partFile, isAppend).use { output ->
                        val buffer = ByteArray(32768)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            currentPartBytes += count

                            val totalDownloaded = baseCompletedBytes + currentPartBytes
                            val progress = (totalDownloaded.toFloat() / totalModelBytes).coerceIn(0f, 1f)
                            onProgressUpdate(progress, totalDownloaded, totalModelBytes, partIndex)
                        }
                        output.flush()
                    }
                }

                if (partFile.length() == expectedSize) {
                    return true
                }
            } catch (e: Exception) {
                Timber.w(e, "Chunk download attempt $attempt failed for $downloadUrl")
                if (attempt < maxRetries) {
                    delay(1500L * attempt)
                }
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }

        return partFile.exists() && partFile.length() == expectedSize
    }

    private suspend fun downloadSingleFile(
        downloadUrl: String,
        targetFile: File,
        partFile: File,
        onProgress: (Float, Long, Long) -> Unit
    ): Boolean {
        val maxRetries = 3
        var attempt = 0

        while (attempt < maxRetries) {
            attempt++
            var connection: HttpURLConnection? = null
            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val url = URL(downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 40000
                connection.requestMethod = "GET"

                if (existingBytes > 0L) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                }

                connection.connect()
                val responseCode = connection.responseCode

                val (isAppend, totalExpectedBytes) = when (responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        val remaining = connection.contentLength.toLong()
                        val total = if (remaining > 0) existingBytes + remaining else -1L
                        Pair(true, total)
                    }
                    HttpURLConnection.HTTP_OK -> {
                        Pair(false, connection.contentLength.toLong())
                    }
                    416 -> {
                        partFile.delete()
                        continue
                    }
                    else -> {
                        if (attempt < maxRetries) {
                            delay(1000L * attempt)
                            continue
                        }
                        return false
                    }
                }

                var currentBytes = if (isAppend) existingBytes else 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partFile, isAppend).use { output ->
                        val buffer = ByteArray(16384)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            currentBytes += count
                            if (totalExpectedBytes > 0) {
                                val progress = (currentBytes.toFloat() / totalExpectedBytes).coerceIn(0f, 1f)
                                onProgress(progress, currentBytes, totalExpectedBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (partFile.exists() && (totalExpectedBytes <= 0 || currentBytes >= totalExpectedBytes)) {
                    if (targetFile.exists()) targetFile.delete()
                    partFile.renameTo(targetFile)
                    onProgress(1f, currentBytes, currentBytes)
                    return true
                }
            } catch (e: Exception) {
                Timber.w(e, "Single file download attempt $attempt failed for $downloadUrl")
                if (attempt < maxRetries) {
                    delay(1500L * attempt)
                }
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }

        return false
    }

    fun deleteModel(modelId: String): Boolean {
        val targetFile = File(modelsDir, "$modelId.tflite")
        return if (targetFile.exists()) targetFile.delete() else true
    }

    fun getModelStorageBytes(): Long {
        return modelsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
