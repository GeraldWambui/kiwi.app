package co.juneai.june.ui.screen.mediadetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.widget.Toast
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.juneai.june.data.local.daos.FaceClusterDao
import co.juneai.june.data.local.daos.MediaOcrDao
import co.juneai.june.data.local.daos.VideoAudioDao
import co.juneai.june.data.local.entities.ImageEmbedding
import co.juneai.june.data.local.entities.ImageEmbedding_
import co.juneai.june.data.model.MediaItem
import co.juneai.june.data.repository.MediaStoreRepository
import co.juneai.june.domain.model.FileInfo
import co.juneai.june.domain.model.MediaDetail
import android.graphics.Bitmap
import co.juneai.june.ml.portrait.PortraitBokehRenderer
import co.juneai.june.ml.portrait.PortraitLightingStyle
import co.juneai.june.ml.segmentation.SubjectSegmenter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val repository: MediaStoreRepository,
    private val embeddingBox: Box<ImageEmbedding>,
    private val mediaOcrDao: MediaOcrDao,
    private val videoAudioDao: VideoAudioDao,
    private val faceClusterDao: FaceClusterDao,
    private val subjectSegmenter: SubjectSegmenter,
    private val portraitBokehRenderer: PortraitBokehRenderer,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MediaDetailState())
    val state: StateFlow<MediaDetailState> = _state.asStateFlow()

    // Performance: LRU cache with max 30 items (prevents memory issues)
    private val mediaCache = object : LruCache<Long, MediaDetail>(30) {
        override fun sizeOf(key: Long, value: MediaDetail): Int = 1
        override fun entryRemoved(evicted: Boolean, key: Long, oldValue: MediaDetail, newValue: MediaDetail?) {
            // Clean up any resources if needed
            super.entryRemoved(evicted, key, oldValue, newValue)
        }
    }

    // Performance: Track loading jobs to prevent duplicate loads
    private val loadingJobs = mutableMapOf<Long, Job>()
    private val loadMutex = Mutex()

    // Performance: Debounce timer for page changes
    private var lastPageChangeTime = 0L
    private val PAGE_CHANGE_DEBOUNCE_MS = 150L

    // Performance: Batch loading queue
    private val pendingLoads = mutableSetOf<Long>()
    private var batchLoadJob: Job? = null

    private var originalMedia: MediaDetail? = null
    private var currentMediaId: Long? = null

    // Flow for distinct media updates (performance optimization)
    private val currentMediaFlow = _state.map { it.media }
        .distinctUntilChanged()
        .onEach { media ->
            // Auto-save to cache when media changes
            media?.let { cacheMedia(it) }
        }

    init {
        viewModelScope.launch {
            currentMediaFlow.launchIn(viewModelScope)
        }
    }

    fun loadMedia(mediaId: Long) {
        currentMediaId = mediaId

        // Cancel any existing loading job for this media
        loadingJobs[mediaId]?.cancel()

        val job = viewModelScope.launch(Dispatchers.IO) {
            loadMutex.withLock {
                try {
                    val aiMetadata = loadAiMetadata(mediaId)

                    // Check cache first
                    mediaCache.get(mediaId)?.let { cachedMedia ->
                        withContext(Dispatchers.Main) {
                            _state.update {
                                it.copy(
                                    media = cachedMedia,
                                    aiMetadata = aiMetadata,
                                    isLoading = false,
                                    error = null,
                                    cachedMediaMap = getCacheMap()
                                )
                            }
                        }
                        return@launch
                    }

                    val mediaItem = repository.getMediaById(mediaId)
                    val mediaDetail = createMediaDetail(mediaItem)

                    cacheMedia(mediaDetail)

                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                media = mediaDetail,
                                aiMetadata = aiMetadata,
                                isLoading = false,
                                error = null,
                                cachedMediaMap = getCacheMap()
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Cannot load media: ${e.message}",
                                media = null
                            )
                        }
                    }
                } finally {
                    loadingJobs.remove(mediaId)
                }
            }
        }

        loadingJobs[mediaId] = job
    }

    private suspend fun loadAiMetadata(mediaId: Long): MediaAiMetadata? = withContext(Dispatchers.IO) {
        try {
            val embedding = embeddingBox.query(ImageEmbedding_.mediaStoreId.equal(mediaId)).build().findFirst()
            val ocrEntity = mediaOcrDao.getOcrByMediaId(mediaId)
            val faceEntities = faceClusterDao.getFacesForMedia(mediaId)
            val personNames = faceEntities.map { it.personName }.filter { it.isNotBlank() }.distinct()
            val audioMoments = try {
                videoAudioDao.getAudioByMediaId(mediaId).filter { 
                    it.audioEvents.isNotBlank() && it.audioEvents != "Silent" && it.audioEvents != "No Audio Track" ||
                    it.transcript.isNotBlank()
                }
            } catch (_: Exception) {
                emptyList()
            }

            if (embedding != null || ocrEntity != null || faceEntities.isNotEmpty() || audioMoments.isNotEmpty()) {
                MediaAiMetadata(
                    ocrText = ocrEntity?.extractedText?.takeIf { it.isNotBlank() },
                    textSnippet = ocrEntity?.textSnippet?.takeIf { it.isNotBlank() },
                    primaryObject = embedding?.primaryObject?.takeIf { it.isNotBlank() },
                    sharpnessScore = embedding?.sharpnessScore ?: 0f,
                    isBlurry = embedding?.isBlurry ?: false,
                    aestheticScore = embedding?.aestheticScore ?: 0f,
                    faceCount = embedding?.faceCount ?: faceEntities.size,
                    isNsfw = embedding?.isNsfw ?: false,
                    nsfwScore = embedding?.nsfwScore ?: 0f,
                    personNames = personNames,
                    audioMoments = audioMoments
                )
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to load AI metadata for media $mediaId")
            null
        }
    }

    fun copyOcrText(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Extracted Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy OCR text")
        }
    }

    private fun cacheMedia(media: MediaDetail) {
        mediaCache.put(media.id, media)
    }

    private fun getCacheMap(): Map<Long, MediaDetail> {
        return mediaCache.snapshot()
    }

    fun showDetails() {
        _state.update { it.copy(showInfoSheet = true) }
    }

    private suspend fun createMediaDetail(mediaItem: MediaItem): MediaDetail {
        return withContext(Dispatchers.IO) {
            val fileInfo = getFileInfo(mediaItem.uri.toUri())

            MediaDetail(
                id = mediaItem.id,
                uri = mediaItem.uri.toUri(),
                title = fileInfo.name,
                dateTime = mediaItem.dateTaken,
                size = fileInfo.size,
                resolution = fileInfo.resolution,
                isFavorite = false,
                isVideo = mediaItem.isVideo,
                duration = mediaItem.duration,
                albumName = mediaItem.albumName,
                path = mediaItem.relativePath
            )
        }
    }

    private suspend fun getFileInfo(uri: Uri): FileInfo {
        return withContext(Dispatchers.IO) {
            val cursor = repository.context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT
                ),
                null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val sizeBytes = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                    val width = it.getInt(it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
                    val height = it.getInt(it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))

                    FileInfo(
                        name = name,
                        size = formatFileSize(sizeBytes),
                        resolution = if (width > 0 && height > 0) "${width}x${height}" else null
                    )
                } else {
                    getFileInfoFromFile(uri)
                }
            } ?: getFileInfoFromFile(uri)
        }
    }

    private fun getFileInfoFromFile(uri: Uri): FileInfo {
        return try {
            val file = File(uri.path ?: "")
            FileInfo(
                name = file.name,
                size = formatFileSize(file.length()),
                resolution = null
            )
        } catch (_: Exception) {
            FileInfo(name = "Unknown", size = "Unknown", resolution = null)
        }
    }

    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes >= 1_000_000_000 -> "${sizeBytes / 1_000_000_000} GB"
            sizeBytes >= 1_000_000 -> "${sizeBytes / 1_000_000} MB"
            sizeBytes >= 1_000 -> "${sizeBytes / 1_000} KB"
            else -> "$sizeBytes B"
        }
    }

    // Performance: Optimized preloading with batch loading
    fun preloadAdjacentMedia(currentId: Long, albumId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaIds = if (albumId != null && albumId != 0L) {
                    repository.getMediaIdsForAlbum(albumId)
                } else {
                    repository.getAllMediaIds()
                }

                val currentIndex = mediaIds.indexOf(currentId)

                // Update state with full list for HorizontalPager
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            mediaIds = mediaIds,
                            currentIndex = if (currentIndex != -1) currentIndex else 0
                        )
                    }
                }

                if (currentIndex == -1) return@launch

                // Performance: Batch preload instead of individual loads
                val idsToPreload = mutableListOf<Long>()

                // Preload 3 items in each direction for smoother scrolling
                for (offset in 1..3) {
                    if (currentIndex + offset < mediaIds.size) {
                        val nextId = mediaIds[currentIndex + offset]
                        if (mediaCache.get(nextId) == null && !loadingJobs.containsKey(nextId)) {
                            idsToPreload.add(nextId)
                        }
                    }
                    if (currentIndex - offset >= 0) {
                        val prevId = mediaIds[currentIndex - offset]
                        if (mediaCache.get(prevId) == null && !loadingJobs.containsKey(prevId)) {
                            idsToPreload.add(prevId)
                        }
                    }
                }

                // Batch load all pending IDs
                if (idsToPreload.isNotEmpty()) {
                    batchLoadMedia(idsToPreload)
                }

                // Sync cache map to state
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(cachedMediaMap = getCacheMap()) }
                }
            } catch (_: Exception) {
                // Silent fail for preloading
            }
        }
    }

    // Performance: Batch load multiple media items at once
    private suspend fun batchLoadMedia(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Batch query for all media items
                val mediaItems = repository.getMediaByIds(mediaIds) // You'll need to implement this
                mediaItems.forEach { mediaItem ->
                    val mediaDetail = createMediaDetail(mediaItem)
                    cacheMedia(mediaDetail)
                }

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(cachedMediaMap = getCacheMap()) }
                }
            } catch (_: Exception) {
                // Fallback to individual loading if batch fails
                mediaIds.forEach { id ->
                    if (mediaCache.get(id) == null) {
                        loadMediaToCache(id)
                    }
                }
            }
        }
    }

    private suspend fun loadMediaToCache(mediaId: Long) {
        if (mediaCache.get(mediaId) != null) return
        if (loadingJobs.containsKey(mediaId)) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaItem = repository.getMediaById(mediaId)
                val mediaDetail = createMediaDetail(mediaItem)
                cacheMedia(mediaDetail)
            } catch (_: Exception) {
                // Silent fail for cache loading
            } finally {
                loadingJobs.remove(mediaId)
            }
        }
        loadingJobs[mediaId] = job
        job.join()
    }

    // Performance: Debounced page change handler
    fun onPageChanged(pageIndex: Int, albumId: Long? = null) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPageChangeTime < PAGE_CHANGE_DEBOUNCE_MS) {
            return // Skip if too frequent
        }
        lastPageChangeTime = currentTime

        val mediaIds = _state.value.mediaIds
        if (pageIndex !in mediaIds.indices) return

        val newMediaId = mediaIds[pageIndex]

        viewModelScope.launch {
            // Load current media immediately
            loadMedia(newMediaId)
            // Preload neighbors after a small delay to prioritize current media
            delay(50)
            preloadAdjacentMedia(newMediaId, albumId)
        }
    }

    fun clearCache(mediaId: Long? = null) {
        if (mediaId != null) {
            mediaCache.remove(mediaId)
        } else {
            mediaCache.evictAll()
        }
        _state.update { it.copy(cachedMediaMap = getCacheMap()) }
    }

    fun onEvent(event: MediaDetailEvent) {
        when (event) {
            is MediaDetailEvent.ToggleFavorite -> {
                _state.update { current ->
                    current.media?.let { media ->
                        current.copy(
                            media = media.copy(isFavorite = !media.isFavorite)
                        )
                    } ?: current
                }
            }
            MediaDetailEvent.Back -> {
                _state.update { it.copy(
                    showInfoSheet = false,
                    showEditDialog = false,
                    showDeleteDialog = false,
                    showShareSheet = false
                ) }
            }
            MediaDetailEvent.Delete -> {
                _state.update { it.copy(showDeleteDialog = true) }
            }

            MediaDetailEvent.Edit -> {
                originalMedia = _state.value.media
                _state.update { it.copy(showEditDialog = true) }
            }

            is MediaDetailEvent.SetMedia -> {
                _state.update { it.copy(media = event.media) }
                cacheMedia(event.media)
            }

            MediaDetailEvent.Share -> {
                shareMedia()
                _state.update { it.copy(showShareSheet = true) }
            }

            MediaDetailEvent.ToggleInfo -> {
                _state.update { current ->
                    current.copy(showInfoSheet = !current.showInfoSheet)
                }
            }

            MediaDetailEvent.MoreOptions -> {
                showMoreOptionsDialog()
            }
            is MediaDetailEvent.SaveEdit -> {
                // Performance: Use existing saveMediaEdit instead of duplicate logic
                saveMediaEdit(event.newTitle, null)
            }
        }
    }

    fun dismissInfo() {
        _state.update { it.copy(showInfoSheet = false) }
    }

    fun dismissEditDialog() {
        _state.update { it.copy(showEditDialog = false) }
    }

    fun dismissDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun dismissShareSheet() {
        _state.update { it.copy(showShareSheet = false) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            currentMediaId?.let { mediaId ->
                try {
                    val deleted = repository.deleteMedia(mediaId)

                    if (deleted) {
                        clearCache(mediaId)
                        _state.update {
                            it.copy(
                                showDeleteDialog = false,
                                media = null,
                                error = null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(error = "Failed to delete media")
                        }
                    }
                } catch (_: SecurityException) {
                    _state.update {
                        it.copy(error = "Permission denied: Cannot delete media")
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(error = "Error deleting media: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showMoreOptionsDialog() {
        _state.update { it.copy(showMoreOptionsDialog = true) }
    }

    fun shareMedia() {
        viewModelScope.launch {
            _state.value.media?.let { media ->
                try {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, media.uri)
                        type = if (media.isVideo) "video/*" else "image/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    _state.update {
                        it.copy(
                            shareIntent = shareIntent,
                            showShareSheet = true
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(error = "Cannot share media: ${e.message}")
                    }
                }
            }
        }
    }

    fun saveMediaEdit(title: String, description: String?) {
        viewModelScope.launch {
            try {
                currentMediaId?.let { mediaId ->
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, title)
                        description?.let {
                            put(MediaStore.Images.Media.DESCRIPTION, it)
                        }
                    }

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val rowsUpdated = context.contentResolver.update(uri, values, null, null)

                    if (rowsUpdated > 0) {
                        // Update cache
                        val updatedMedia = _state.value.media?.copy(title = title)
                        updatedMedia?.let { cacheMedia(it) }

                        _state.update { current ->
                            current.media?.let { currentMedia ->
                                current.copy(
                                    media = currentMedia.copy(title = title),
                                    showEditDialog = false
                                )
                            } ?: current
                        }
                    } else {
                        _state.update {
                            it.copy(error = "Failed to save changes")
                        }
                    }
                }
            } catch (e: SecurityException) {
                _state.update {
                    it.copy(error = "Permission denied: Cannot edit media")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Error saving: ${e.message}")
                }
            }
        }
    }

    fun setAsWallpaper() {
        viewModelScope.launch {
            _state.value.media?.let { media ->
                try {
                    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                        setDataAndType(media.uri, if (media.isVideo) "video/*" else "image/*")
                        putExtra("mimeType", media.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    _state.update {
                        it.copy(
                            wallaperIntent = intent,
                            showMoreOptionsDialog = false
                        )
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(error = "Cannot set as wallpaper: ${e.message}")
                    }
                }
            }
        }
    }

    fun copyToClipboard() {
        viewModelScope.launch {
            _state.value.media?.let { media ->
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newUri(
                        context.contentResolver,
                        "Image",
                        media.uri
                    )
                    clipboard.setPrimaryClip(clip)

                    _state.update { it.copy(showMoreOptionsDialog = false) }
                    showToast(context, "Copied to clipboard")
                } catch (e: Exception) {
                    _state.update {
                        it.copy(error = "Cannot copy to clipboard: ${e.message}")
                    }
                }
            }
        }
    }

    fun rotateImage(clockwise: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isRotating = true) }

            try {
                // TODO: Implement actual rotation
                showToast(context, "Image rotated ${if (clockwise) "clockwise" else "counter-clockwise"}")

                currentMediaId?.let {
                    clearCache(it)
                    loadMedia(it)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to rotate image: ${e.message}")
                }
            } finally {
                _state.update { it.copy(isRotating = false) }
            }
        }
    }

    fun showOnMap() {
        viewModelScope.launch {
            _state.value.media?.let { media ->
                try {
                    val location = extractLocationFromMedia(media.uri)

                    if (location == null) {
                        _state.update {
                            it.copy(error = "No GPS location data found in this image")
                        }
                        showToast(context, "This image doesn't have location data")
                        return@launch
                    }

                    val (latitude, longitude) = location

                    if (latitude == 0.0 && longitude == 0.0) {
                        _state.update {
                            it.copy(error = "Invalid location data (0,0)")
                        }
                        showToast(context, "Invalid location data")
                        return@launch
                    }

                    val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")

                    val packageManager = context.packageManager
                    val isMapsInstalled = try {
                        packageManager.getPackageInfo("com.google.android.apps.maps", 0) != null
                    } catch (_: PackageManager.NameNotFoundException) {
                        false
                    }

                    if (isMapsInstalled) {
                        _state.update {
                            it.copy(mapIntent = mapIntent)
                        }
                    } else {
                        val genericMapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        _state.update {
                            it.copy(
                                mapIntent = genericMapIntent,
                                error = "Google Maps not installed. Trying other map apps..."
                            )
                        }
                    }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(error = "Cannot show on map: ${e.message}")
                    }
                    e.printStackTrace()
                }
            } ?: run {
                _state.update {
                    it.copy(error = "No media selected")
                }
                showToast(context, "No media selected")
            }
        }
    }

    fun dismissMapIntent() {
        _state.update { currentState ->
            currentState.copy(mapIntent = null)
        }
    }

    fun dismissWallpaperIntent() {
        _state.update { it.copy(wallaperIntent = null) }
    }

    fun clearAllIntents() {
        _state.update { currentState ->
            currentState.copy(
                mapIntent = null,
                shareIntent = null,
            )
        }
    }

    fun dismissAllDialogs() {
        _state.update { currentState ->
            currentState.copy(
                showInfoSheet = false,
                showEditDialog = false,
                showDeleteDialog = false,
                showShareSheet = false,
                showMoreOptionsDialog = false,
                error = null
            )
        }
    }

    private suspend fun extractLocationFromMedia(uri: Uri): Pair<Double, Double>? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exif = ExifInterface(inputStream)

                    val latLong = FloatArray(2)
                    if (exif.latLong != null) {
                        return@withContext Pair(latLong[0].toDouble(), latLong[1].toDouble())
                    }

                    val latitude = getLatitudeFromExif(exif)
                    val longitude = getLongitudeFromExif(exif)

                    if (latitude != null && longitude != null) {
                        return@withContext Pair(latitude, longitude)
                    }

                    return@withContext null
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getLatitudeFromExif(exif: ExifInterface): Double? {
        return try {
            val latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)

            if (latitude != null && latitudeRef != null) {
                convertToDegree(latitude)?.let { lat ->
                    if (latitudeRef == "S") -lat else lat
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getLongitudeFromExif(exif: ExifInterface): Double? {
        return try {
            val longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            val longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)

            if (longitude != null && longitudeRef != null) {
                convertToDegree(longitude)?.let { lon ->
                    if (longitudeRef == "W") -lon else lon
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertToDegree(stringDMS: String): Double? {
        return try {
            val parts = stringDMS.split(",").map { it.trim() }
            if (parts.size != 3) return null

            val degrees = Rational.parseRational(parts[0]).toDouble()
            val minutes = Rational.parseRational(parts[1]).toDouble()
            val seconds = Rational.parseRational(parts[2]).toDouble()

            degrees + (minutes / 60.0) + (seconds / 3600.0)
        } catch (_: Exception) {
            null
        }
    }

    private object Rational {
        fun parseRational(value: String): RationalNumber {
            val parts = value.split("/")
            return if (parts.size == 2) {
                RationalNumber(parts[0].toDouble(), parts[1].toDouble())
            } else {
                RationalNumber(value.toDouble(), 1.0)
            }
        }

        data class RationalNumber(val numerator: Double, val denominator: Double) {
            fun toDouble(): Double = numerator / denominator
        }
    }

    fun showToast(context: Context, message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ═════════════════════════════════════════════════════════════
    // COMPUTATIONAL PHOTOGRAPHY: SUBJECT LIFT (STICKER MAKER)
    // ═════════════════════════════════════════════════════════════

    fun liftSubjectFromBitmap(sourceBitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(isSubjectLiftLoading = true) }
            val segmented = subjectSegmenter.extractSubjectCutout(sourceBitmap)
            _state.update {
                it.copy(
                    isSubjectLiftLoading = false,
                    isSubjectLiftActive = true,
                    subjectCutoutBitmap = segmented.cutoutBitmap
                )
            }
        }
    }

    fun dismissSubjectLift() {
        _state.update {
            it.copy(
                isSubjectLiftActive = false,
                subjectCutoutBitmap = null
            )
        }
    }

    fun saveCutoutToGallery(cutoutBitmap: Bitmap, onSuccess: (Uri) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "sticker_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KiwiStickers")
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        cutoutBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess(uri)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save cutout sticker")
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // COMPUTATIONAL PHOTOGRAPHY: STUDIO PORTRAIT & DEPTH BOKEH
    // ═════════════════════════════════════════════════════════════

    fun openPortraitMode(sourceBitmap: Bitmap) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isPortraitModeActive = true,
                    isPortraitRendering = true
                )
            }
            val rendered = portraitBokehRenderer.renderPortrait(
                sourceBitmap = sourceBitmap,
                blurIntensity = _state.value.portraitBlurIntensity,
                lightingStyle = _state.value.portraitLightingStyle
            )
            _state.update {
                it.copy(
                    isPortraitRendering = false,
                    portraitRenderedBitmap = rendered
                )
            }
        }
    }

    fun updatePortraitSettings(sourceBitmap: Bitmap, blur: Float, style: PortraitLightingStyle) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    portraitBlurIntensity = blur,
                    portraitLightingStyle = style,
                    isPortraitRendering = true
                )
            }
            val rendered = portraitBokehRenderer.renderPortrait(
                sourceBitmap = sourceBitmap,
                blurIntensity = blur,
                lightingStyle = style
            )
            _state.update {
                it.copy(
                    isPortraitRendering = false,
                    portraitRenderedBitmap = rendered
                )
            }
        }
    }

    fun savePortraitEnhancedCopy(portraitBitmap: Bitmap, onSuccess: (Uri) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "portrait_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KiwiPortraits")
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        portraitBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess(uri)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save portrait copy")
            }
        }
    }

    fun closePortraitMode() {
        _state.update {
            it.copy(
                isPortraitModeActive = false,
                portraitRenderedBitmap = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        mediaCache.evictAll()
        batchLoadJob?.cancel()
    }
}