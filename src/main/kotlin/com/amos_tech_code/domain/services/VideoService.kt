package com.amos_tech_code.domain.services

import com.amos_tech_code.application.configs.SupabaseConfig
import com.amos_tech_code.application.configs.SupabaseConfig.STORAGE_BUCKET
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import org.apache.tika.Tika
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import javax.imageio.ImageIO

object VideoService {
    private val client = HttpClient(CIO)
    private val STORAGE_URL = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"
    private val tika = Tika()

    private val logger = LoggerFactory.getLogger(VideoService::class.java)

    // Video compression settings
    private const val TARGET_MAX_SIZE_MB = 10
    private const val TARGET_BITRATE_KBPS = 1500
    private const val MAX_DURATION_SECONDS = 60
    private const val TARGET_WIDTH = 720
    private const val TARGET_HEIGHT = 1280
    private const val TARGET_FRAMERATE = 30

    /**
     * Used by statuses → returns videoUrl + raw thumbnail bytes for blurhash
     */
    suspend fun processStatusVideo(multipart: MultiPartData, folder: String): Pair<String, ByteArray> {
        val tempFile = Files.createTempFile("video_upload_", ".tmp")
        val tempThumbnail = Files.createTempFile("thumbnail_", ".jpg")

        try {
            val videoData = multipart.readAllParts().first { it is PartData.FileItem }
            val originalFileName = videoData.name ?: "video.mp4"

            (videoData as PartData.FileItem).streamProvider().use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            //logger.info("Processing video file: $originalFileName, size: ${Files.size(tempFile)} bytes")

            // Validate & compress
            val metadata = validateAndGetMetadata(tempFile)
            //logger.debug("Video metadata: $metadata")

            val compressedFile = compressVideo(tempFile, metadata)
            //logger.debug("Video compressed successfully, new size: ${Files.size(compressedFile)} bytes")

            // Generate thumbnail (as file + bytes)
            generateThumbnail(tempFile, tempThumbnail)
            val thumbnailBytes = Files.readAllBytes(tempThumbnail)

            // Upload video only (statuses don't need thumbnail upload)
            val videoUrl = uploadToStorage(
                compressedFile,
                folder,
                getOutputFilename(originalFileName, "video"),
                "video/mp4" // Always output as MP4 after compression
            )

            //logger.info("Video uploaded successfully: $videoUrl")

            return Pair(videoUrl, thumbnailBytes)

        } catch (e: VideoValidationException) {
            logger.error("Video validation failed: ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during video processing", e)
            throw VideoUploadException("Failed to process video.")
        } finally {
            Files.deleteIfExists(tempFile)
            Files.deleteIfExists(tempThumbnail)
        }
    }

    /**
     * General uploads (non-status) → returns videoUrl + uploaded thumbnailUrl
     */
    suspend fun uploadVideo(multipart: MultiPartData, folder: String): Pair<String, String> {
        val tempFile = Files.createTempFile("video_upload_", ".tmp")
        val tempThumbnail = Files.createTempFile("thumbnail_", ".jpg")

        try {
            val videoData = multipart.readAllParts().first { it is PartData.FileItem }
            val originalFileName = videoData.name ?: "video.mp4"

            (videoData as PartData.FileItem).streamProvider().use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            //logger.info("Processing video file: $originalFileName, size: ${Files.size(tempFile)} bytes")

            // Validate & compress
            val metadata = validateAndGetMetadata(tempFile)
            //logger.debug("Video metadata: $metadata")

            val compressedFile = compressVideo(tempFile, metadata)
            //logger.debug("Video compressed successfully, new size: ${Files.size(compressedFile)} bytes")

            // Generate thumbnail (file only)
            generateThumbnail(tempFile, tempThumbnail)

            // Upload both
            val videoUrl = uploadToStorage(
                compressedFile,
                folder,
                getOutputFilename(originalFileName, "video"),
                "video/mp4"
            )

            val thumbnailUrl = uploadToStorage(
                tempThumbnail,
                "$folder/thumbnails",
                getOutputFilename(originalFileName, "thumbnail"),
                "image/jpeg"
            )

            return Pair(videoUrl, thumbnailUrl)
        } catch (e: VideoValidationException) {
            logger.error("Video validation failed.")
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during video processing", e)
            throw VideoUploadException("Failed to process video.")
        } finally {
            Files.deleteIfExists(tempFile)
            Files.deleteIfExists(tempThumbnail)
        }
    }

    private fun validateAndGetMetadata(file: java.nio.file.Path): VideoMetadata {
        FFmpegFrameGrabber(file.toFile()).use { grabber ->
            grabber.start()

            val duration = grabber.lengthInTime / 1_000_000 // Convert to seconds
            val fileSize = Files.size(file).toInt()
            val bitrate = grabber.videoBitrate
            val frameRate = grabber.frameRate
            val width = grabber.imageWidth
            val height = grabber.imageHeight
            val hasAudio = grabber.audioChannels > 0

            // Use Tika to detect the actual format from content
            val format = detectVideoFormat(file)

            if (duration > MAX_DURATION_SECONDS) {
                throw VideoValidationException("Video duration ${duration}s exceeds maximum allowed duration of ${MAX_DURATION_SECONDS}s")
            }

            val sizeMB = fileSize / (1024.0 * 1024.0)
            if (sizeMB > TARGET_MAX_SIZE_MB) {
                throw VideoValidationException("Video size ${"%.2f".format(sizeMB)}MB exceeds maximum allowed size of ${TARGET_MAX_SIZE_MB}MB")
            }

            return VideoMetadata(
                duration = duration.toInt(),
                format = format,
                fileSize = fileSize,
                resolution = "${width}x${height}",
                bitrate = bitrate,
                frameRate = frameRate,
                width = width,
                height = height,
                hasAudio = hasAudio
            )
        }
    }

    private fun detectVideoFormat(file: java.nio.file.Path): VideoFormat {
        return try {
            val mimeType = tika.detect(file.toFile())
            logger.debug("Tika detected MIME type: $mimeType")

            when {
                mimeType.contains("mp4") -> VideoFormat.MP4
                mimeType.contains("webm") -> VideoFormat.WEBM
                mimeType.contains("quicktime") || mimeType.contains("mov") -> VideoFormat.MOV
                mimeType.contains("avi") -> VideoFormat.AVI
                mimeType.contains("matroska") || mimeType.contains("mkv") -> VideoFormat.MKV
                else -> {
                    logger.warn("Unsupported MIME type: $mimeType, defaulting to MP4")
                    VideoFormat.MP4
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to detect video format with Tika: ${e.message}, defaulting to MP4")
            VideoFormat.MP4
        }
    }

    private fun compressVideo(inputFile: java.nio.file.Path, metadata: VideoMetadata): java.nio.file.Path {
        val outputFile = Files.createTempFile("compressed_video_", ".mp4")

        FFmpegFrameGrabber(inputFile.toFile()).use { grabber ->
            grabber.start()

            val hasAudio = metadata.hasAudio

            FFmpegFrameRecorder(outputFile.toFile(), grabber.imageWidth, grabber.imageHeight).use { recorder ->
                recorder.format = "mp4"
                recorder.videoCodec = avcodec.AV_CODEC_ID_H264
                recorder.videoBitrate = TARGET_BITRATE_KBPS * 1000
                recorder.frameRate = grabber.frameRate.coerceAtMost(TARGET_FRAMERATE.toDouble())
                recorder.pixelFormat = avutil.AV_PIX_FMT_YUV420P
                recorder.setOption("preset", "fast")
                recorder.setOption("crf", "23")

                // Only set up audio if the source has audio
                if (hasAudio) {
                    recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
                    recorder.audioChannels = grabber.audioChannels
                    recorder.audioBitrate = 128000
                    recorder.sampleRate = grabber.sampleRate
                }

                recorder.start()

                var frame = grabber.grab()
                while (frame != null) {
                    if (frame.image != null) {
                        recorder.record(frame)
                    } else if (hasAudio && frame.samples != null) {
                        recorder.record(frame)
                    }
                    frame = grabber.grab()
                }

                recorder.stop()
            }
            grabber.stop()
        }

        return outputFile
    }

    private fun generateThumbnail(videoFile: java.nio.file.Path, outputThumbnail: java.nio.file.Path) {
        FFmpegFrameGrabber(videoFile.toFile()).use { grabber ->
            grabber.start()

            // Seek to 10% of video duration for thumbnail
            val thumbnailPosition = (grabber.lengthInTime * 0.1).toLong()
            grabber.timestamp = thumbnailPosition

            val frame = grabber.grabImage()
            if (frame != null) {
                val converter = Java2DFrameConverter()
                val bufferedImage = converter.convert(frame)
                converter.close()

                ImageIO.write(bufferedImage, "jpg", outputThumbnail.toFile())
            }

            grabber.stop()
        }
    }

    private suspend fun uploadToStorage(
        file: java.nio.file.Path,
        folder: String,
        fileName: String,
        contentType: String
    ): String {
        val newFileName = "${folder}/$fileName"

        val fileBytes = Files.readAllBytes(file)

        val response = client.put("$STORAGE_URL/${STORAGE_BUCKET}/$newFileName") {
            headers {
                append("apikey", SupabaseConfig.SUPABASE_KEY)
                append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                append("Content-Type", contentType)
            }
            setBody(fileBytes)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw VideoUploadException("Failed to upload file: ${response.status} - $errorBody")
        }

        return "$STORAGE_URL/public/${STORAGE_BUCKET}/$newFileName"
    }

    private fun getOutputFilename(originalFileName: String, prefix: String): String {
        val timestamp = System.currentTimeMillis()
        val randomUUID = UUID.randomUUID().toString().take(8)
        val baseName = originalFileName.substringBeforeLast(".").takeIf { it.isNotEmpty() } ?: "file"
        return "${prefix}_${baseName}_${timestamp}_${randomUUID}.mp4"
    }

}

// Data classes
data class VideoMetadata(
    val duration: Int,
    val format: VideoFormat,
    val fileSize: Int,
    val resolution: String,
    val bitrate: Int,
    val frameRate: Double,
    val width: Int,
    val height: Int,
    val hasAudio: Boolean
)

enum class VideoFormat { MP4, WEBM, MOV, AVI, MKV }

// Exceptions
class VideoUploadException(message: String) : IllegalStateException(message)
class VideoValidationException(message: String) : IllegalArgumentException(message)