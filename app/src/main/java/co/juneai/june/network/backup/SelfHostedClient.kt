package co.juneai.june.network.backup

import android.util.Base64
import co.juneai.june.data.local.prefs.SelfHostedConfig
import co.juneai.june.data.local.prefs.SelfHostedProvider
import co.juneai.june.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ConnectionResult(
    val success: Boolean,
    val serverVersion: String = "",
    val latencyMs: Long = 0,
    val errorMessage: String? = null
)

data class UploadResult(
    val success: Boolean,
    val remoteAssetId: String = "",
    val isDuplicate: Boolean = false,
    val errorMessage: String? = null
)

interface SelfHostedClient {
    suspend fun testConnection(): ConnectionResult
    suspend fun checkDuplicates(items: List<Pair<Long, String>>): Set<Long>
    suspend fun uploadMedia(
        media: MediaItem,
        inputStream: InputStream,
        contentLength: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): UploadResult
}

class SelfHostedClientFactory {
    companion object {
        fun create(config: SelfHostedConfig): SelfHostedClient {
            return when (config.provider) {
                SelfHostedProvider.IMMICH -> ImmichBackupClient(config)
                SelfHostedProvider.NEXTCLOUD, SelfHostedProvider.WEBDAV -> WebDavBackupClient(config)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// IMMICH CLIENT
// ─────────────────────────────────────────────────────────────
class ImmichBackupClient(private val config: SelfHostedConfig) : SelfHostedClient {

    private val baseUrl = config.serverUrl.removeSuffix("/")
    private val apiKey = config.apiKeyOrToken

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val pingUrl = URL("$baseUrl/api/server-info/ping")
            val conn = (pingUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startMs

            if (code in 200..299) {
                // Fetch version info
                var versionStr = "Immich Server"
                try {
                    val verConn = (URL("$baseUrl/api/server-info/version").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("x-api-key", apiKey)
                        setRequestProperty("Accept", "application/json")
                    }
                    if (verConn.responseCode == 200) {
                        val body = verConn.inputStream.bufferedReader().readText()
                        val json = JSONObject(body)
                        val major = json.optInt("major", 1)
                        val minor = json.optInt("minor", 0)
                        val patch = json.optInt("patch", 0)
                        versionStr = "v$major.$minor.$patch"
                    }
                } catch (_: Exception) {}

                ConnectionResult(success = true, serverVersion = versionStr, latencyMs = latency)
            } else if (code == 401 || code == 403) {
                ConnectionResult(success = false, errorMessage = "Authentication failed. Check your API Key (HTTP $code).")
            } else {
                ConnectionResult(success = false, errorMessage = "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            Timber.e(e, "Immich connection test failed")
            ConnectionResult(success = false, errorMessage = e.message ?: "Failed to connect to Immich server")
        }
    }

    override suspend fun checkDuplicates(items: List<Pair<Long, String>>): Set<Long> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptySet()
        try {
            val checkUrl = URL("$baseUrl/api/assets/bulk-upload-check")
            val conn = (checkUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val assetsArray = JSONArray()
            items.forEach { (id, checksum) ->
                assetsArray.put(JSONObject().apply {
                    put("id", id.toString())
                    put("checksum", checksum)
                })
            }
            val requestJson = JSONObject().apply {
                put("assets", assetsArray)
            }

            conn.outputStream.bufferedWriter().use { it.write(requestJson.toString()) }

            if (conn.responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(responseStr)
                val resultsArray = responseJson.optJSONArray("results") ?: JSONArray()
                val duplicates = mutableSetOf<Long>()
                for (i in 0 until resultsArray.length()) {
                    val obj = resultsArray.getJSONObject(i)
                    val action = obj.optString("action")
                    val idStr = obj.optString("id")
                    if (action == "reject" || obj.optString("reason") == "duplicate") {
                        idStr.toLongOrNull()?.let { duplicates.add(it) }
                    }
                }
                duplicates
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Timber.w(e, "Bulk upload check failed, proceeding without server deduplication")
            emptySet()
        }
    }

    override suspend fun uploadMedia(
        media: MediaItem,
        inputStream: InputStream,
        contentLength: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        val boundary = "KiwiBoundary" + System.currentTimeMillis()
        val LINE_END = "\r\n"
        val TWO_HYPHENS = "--"

        try {
            val uploadUrl = URL("$baseUrl/api/assets")
            val conn = (uploadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                useCaches = false
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setChunkedStreamingMode(64 * 1024) // 64 KB streaming chunks
            }

            val out = BufferedOutputStream(conn.outputStream)

            fun writeFormField(name: String, value: String) {
                out.write("$TWO_HYPHENS$boundary$LINE_END".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"$LINE_END$LINE_END".toByteArray())
                out.write("$value$LINE_END".toByteArray())
            }

            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(if (media.dateTaken > 0) media.dateTaken else System.currentTimeMillis()))

            val ext = media.name.substringAfterLast(".", if (media.isVideo) "mp4" else "jpg")
            val mime = if (media.isVideo) "video/$ext" else "image/$ext"

            writeFormField("deviceAssetId", media.id.toString())
            writeFormField("deviceId", "kiwi_android")
            writeFormField("fileCreatedAt", isoDate)
            writeFormField("fileModifiedAt", isoDate)
            writeFormField("isFavorite", "false")
            writeFormField("fileExtension", ".$ext")

            // File binary part header
            out.write("$TWO_HYPHENS$boundary$LINE_END".toByteArray())
            out.write("Content-Disposition: form-data; name=\"assetData\"; filename=\"${media.name}\"$LINE_END".toByteArray())
            out.write("Content-Type: $mime$LINE_END$LINE_END".toByteArray())

            // Stream binary data
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalBytesWritten = 0L
            val inStream = BufferedInputStream(inputStream)

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
                onProgress(totalBytesWritten, contentLength)
            }

            out.write(LINE_END.toByteArray())
            out.write("$TWO_HYPHENS$boundary$TWO_HYPHENS$LINE_END".toByteArray())
            out.flush()

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(resp)
                val id = json.optString("id", "")
                val status = json.optString("status", "")
                val isDuplicate = json.optBoolean("duplicate", false) || status == "duplicate"
                UploadResult(success = true, remoteAssetId = id, isDuplicate = isDuplicate)
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                UploadResult(success = false, errorMessage = "HTTP $responseCode: $err")
            }
        } catch (e: Exception) {
            Timber.e(e, "Immich asset upload failed for media ${media.id}")
            UploadResult(success = false, errorMessage = e.message)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// WEBDAV / NEXTCLOUD CLIENT
// ─────────────────────────────────────────────────────────────
class WebDavBackupClient(private val config: SelfHostedConfig) : SelfHostedClient {

    private val baseUrl = config.serverUrl.removeSuffix("/")
    private val targetDir = config.targetDirectory.trim().trim('/')
    private val authHeader: String by lazy {
        if (config.apiKeyOrToken.isNotBlank() && config.username.isBlank()) {
            "Bearer ${config.apiKeyOrToken}"
        } else {
            val credentials = "${config.username}:${config.password}"
            "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        }
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = URL(baseUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Depth", "0")
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startMs

            if (code in 200..299 || code == 207) { // 207 Multi-Status
                ConnectionResult(success = true, serverVersion = "WebDAV Connected", latencyMs = latency)
            } else if (code == 401 || code == 403) {
                ConnectionResult(success = false, errorMessage = "Authentication failed. Check credentials (HTTP $code).")
            } else {
                ConnectionResult(success = false, errorMessage = "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            Timber.e(e, "WebDAV connection test failed")
            ConnectionResult(success = false, errorMessage = e.message ?: "Failed to connect to WebDAV server")
        }
    }

    override suspend fun checkDuplicates(items: List<Pair<Long, String>>): Set<Long> {
        // WebDAV checks file presence via PROPFIND
        return emptySet()
    }

    override suspend fun uploadMedia(
        media: MediaItem,
        inputStream: InputStream,
        contentLength: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure target directory exists (MKCOL)
            ensureDirectoryExists(targetDir)

            // 2. Upload file via PUT
            val encodedFileName = URLEncoder.encode(media.name, "UTF-8").replace("+", "%20")
            val fileUrl = if (targetDir.isNotBlank()) {
                URL("$baseUrl/$targetDir/$encodedFileName")
            } else {
                URL("$baseUrl/$encodedFileName")
            }

            val conn = (fileUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Authorization", authHeader)
                if (contentLength > 0) {
                    setFixedLengthStreamingMode(contentLength)
                } else {
                    setChunkedStreamingMode(64 * 1024)
                }
            }

            val out = BufferedOutputStream(conn.outputStream)
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            var totalWritten = 0L
            val inStream = BufferedInputStream(inputStream)

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
                onProgress(totalWritten, contentLength)
            }
            out.flush()

            val code = conn.responseCode
            if (code in 200..299 || code == 201 || code == 204) {
                UploadResult(success = true, remoteAssetId = "$targetDir/${media.name}")
            } else {
                UploadResult(success = false, errorMessage = "WebDAV returned HTTP $code")
            }
        } catch (e: Exception) {
            Timber.e(e, "WebDAV upload failed for media ${media.id}")
            UploadResult(success = false, errorMessage = e.message)
        }
    }

    private fun ensureDirectoryExists(dir: String) {
        if (dir.isBlank()) return
        try {
            val dirUrl = URL("$baseUrl/$dir")
            val conn = (dirUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "MKCOL"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", authHeader)
            }
            conn.responseCode // Triggers network call (201 Created or 405 Method Not Allowed if exists)
        } catch (_: Exception) {}
    }
}
