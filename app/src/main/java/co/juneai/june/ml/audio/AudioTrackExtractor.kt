package co.juneai.june.ml.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ExtractedAudio(
    val sampleRate: Int = 16000,
    val samples: FloatArray,
    val durationMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtractedAudio
        if (sampleRate != other.sampleRate) return false
        if (durationMs != other.durationMs) return false
        if (!samples.contentEquals(other.samples)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

/**
 * Hardware-accelerated audio extraction and resampling pipeline.
 *
 * Demuxes only the audio track from video files using [MediaExtractor],
 * decodes compressed frames (AAC, Opus, MP3, AMR) via [MediaCodec],
 * downmixes stereo/surround to mono, and resamples to 16,000 Hz Linear PCM.
 */
@Singleton
class AudioTrackExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        const val TARGET_SAMPLE_RATE = 16000
        private const val TIMEOUT_US = 10000L
        // Cap max extraction at 10 minutes (9.6 million floats ≈ 38 MB) to prevent OOM
        private const val MAX_DURATION_MS = 600_000L
    }

    /**
     * Extracts 16kHz mono audio samples from [videoUriString].
     */
    suspend fun extractAudio(videoUriString: String): ExtractedAudio? = extractAudio(Uri.parse(videoUriString))

    /**
     * Extracts 16kHz mono audio samples from [videoUri].
     * Returns null if no audio track exists or decoding fails.
     */
    suspend fun extractAudio(videoUri: Uri): ExtractedAudio? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, videoUri, null)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { idx ->
                val format = extractor.getTrackFormat(idx)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            } ?: run {
                Timber.d("No audio track found in video: $videoUri")
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val durationMs = (durationUs / 1000L).coerceAtMost(MAX_DURATION_MS)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmShortList = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                // Feed input buffers
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain output buffers
                var outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val rawShorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(rawShorts)

                        // Downmix to mono
                        if (channelCount > 1) {
                            for (i in 0 until rawShorts.size step channelCount) {
                                var sum = 0
                                for (ch in 0 until channelCount) {
                                    if (i + ch < rawShorts.size) {
                                        sum += rawShorts[i + ch]
                                    }
                                }
                                pcmShortList.add((sum / channelCount).toShort())
                            }
                        } else {
                            for (s in rawShorts) {
                                pcmShortList.add(s)
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            if (pcmShortList.isEmpty()) {
                return@withContext null
            }

            // Resample from sourceSampleRate to 16,000 Hz mono
            val resampledFloats = resampleTo16k(pcmShortList, sourceSampleRate)
            val finalDurationMs = (resampledFloats.size * 1000L) / TARGET_SAMPLE_RATE

            ExtractedAudio(
                sampleRate = TARGET_SAMPLE_RATE,
                samples = resampledFloats,
                durationMs = if (durationMs > 0) durationMs else finalDurationMs
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract audio track from video: $videoUri")
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Resamples 16-bit PCM shorts to 16,000 Hz and normalizes to Float in [-1.0f, 1.0f].
     * Uses linear interpolation for fast and accurate audio sample reconstruction.
     */
    private fun resampleTo16k(sourceShorts: List<Short>, sourceSampleRate: Int): FloatArray {
        if (sourceSampleRate == TARGET_SAMPLE_RATE) {
            val result = FloatArray(sourceShorts.size)
            for (i in sourceShorts.indices) {
                result[i] = (sourceShorts[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
            }
            return result
        }

        val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val targetLength = (sourceShorts.size / ratio).roundToInt().coerceAtLeast(0)
        val result = FloatArray(targetLength)

        for (i in 0 until targetLength) {
            val srcIndex = i * ratio
            val indexFloor = srcIndex.toInt()
            val frac = (srcIndex - indexFloor).toFloat()

            val s1 = if (indexFloor < sourceShorts.size) sourceShorts[indexFloor] else 0
            val s2 = if (indexFloor + 1 < sourceShorts.size) sourceShorts[indexFloor + 1] else s1

            val interpolated = s1 + frac * (s2 - s1)
            result[i] = (interpolated / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        return result
    }
}
