package com.amos_tech_code.domain.services

import com.amos_tech_code.application.configs.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.readAllParts
import io.ktor.http.content.streamProvider
import io.ktor.http.headers
import io.ktor.http.isSuccess
import org.apache.tika.Tika
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.imageio.ImageIO

object VideoService {
    private val client = HttpClient(CIO)
    private val STORAGE_URL = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"
    private const val STORAGE_BUCKET = "videos"
    private val tika = Tika()

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

            // Validate & compress
            val metadata = validateAndGetMetadata(tempFile, originalFileName)
            val compressedFile = compressVideo(tempFile, metadata)

            // Generate thumbnail (as file + bytes)
            generateThumbnail(tempFile, tempThumbnail)
            val thumbnailBytes = Files.readAllBytes(tempThumbnail)

            // Upload video only (statuses don’t need thumbnail upload)
            val videoUrl = uploadToStorage(
                compressedFile,
                folder,
                originalFileName,
                "video"
            )

            return Pair(videoUrl, thumbnailBytes)
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

            // Validate & compress
            val metadata = validateAndGetMetadata(tempFile, originalFileName)
            val compressedFile = compressVideo(tempFile, metadata)

            // Generate thumbnail (file only)
            generateThumbnail(tempFile, tempThumbnail)

            // Upload both
            val videoUrl = uploadToStorage(compressedFile, folder, originalFileName, "video")
            val thumbnailUrl = uploadToStorage(
                tempThumbnail,
                "$folder/thumbnails",
                "thumbnail_${originalFileName.substringBeforeLast(".")}.jpg",
                "image"
            )

            return Pair(videoUrl, thumbnailUrl)
        } finally {
            Files.deleteIfExists(tempFile)
            Files.deleteIfExists(tempThumbnail)
        }
    }

    private fun validateAndGetMetadata(file: java.nio.file.Path, fileName: String): VideoMetadata {
        FFmpegFrameGrabber(file.toFile()).use { grabber ->
            grabber.start()

            val duration = grabber.lengthInTime / 1_000_000 // Convert to seconds
            val fileSize = Files.size(file).toInt()
            val bitrate = grabber.getVideoBitrate()
            val frameRate = grabber.getFrameRate()
            val width = grabber.imageWidth
            val height = grabber.imageHeight

            if (duration > MAX_DURATION_SECONDS) {
                throw VideoValidationException("Video duration ${duration}s exceeds maximum allowed duration of ${MAX_DURATION_SECONDS}s")
            }

            val sizeMB = fileSize / (1024.0 * 1024.0)
            if (sizeMB > TARGET_MAX_SIZE_MB) {
                throw VideoValidationException("Video size ${"%.2f".format(sizeMB)}MB exceeds maximum allowed size of ${TARGET_MAX_SIZE_MB}MB")
            }

            val format = getVideoFormat(fileName)

            return VideoMetadata(
                duration = duration.toInt(),
                format = format,
                fileSize = fileSize,
                resolution = "${width}x${height}",
                bitrate = bitrate,
                frameRate = frameRate,
                width = width,
                height = height
            )
        }
    }

    private fun compressVideo(inputFile: java.nio.file.Path, metadata: VideoMetadata): java.nio.file.Path {
        val outputFile = Files.createTempFile("compressed_video_", ".mp4")

        FFmpegFrameGrabber(inputFile.toFile()).use { grabber ->
            grabber.start()

            FFmpegFrameRecorder(outputFile.toFile(), grabber.imageWidth, grabber.imageHeight).use { recorder ->
                recorder.format = "mp4"
                recorder.videoCodec = avcodec.AV_CODEC_ID_H264
                recorder.videoBitrate = TARGET_BITRATE_KBPS * 1000
                recorder.frameRate = grabber.frameRate.coerceAtMost(TARGET_FRAMERATE.toDouble())
                recorder.pixelFormat = avutil.AV_PIX_FMT_YUV420P
                recorder.videoQuality = 0.0 // Auto quality
                recorder.setOption("preset", "fast")
                recorder.setOption("crf", "23")

                recorder.start()

                var frame = grabber.grab()
                while (frame != null) {
                    recorder.record(frame)
                    frame = grabber.grab()
                }

                recorder.stop()
            }
            grabber.stop()
        }

        return outputFile
    }

    fun generateThumbnail(videoFile: java.nio.file.Path, outputThumbnail: java.nio.file.Path) {
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
        originalFileName: String,
        fileType: String
    ): String {
        val fileExtension = originalFileName.substringAfterLast(".", "mp4")
        val timestamp = System.currentTimeMillis()
        val randomUUID = UUID.randomUUID().toString().take(8)
        val newFileName = "${folder}/${fileType}_${timestamp}_${randomUUID}.$fileExtension"

        val fileBytes = Files.readAllBytes(file)
        val contentType = tika.detect(file.toFile())

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
            throw VideoUploadException("Failed to upload $fileType: ${response.status} - $errorBody")
        }

        return "$STORAGE_URL/public/${STORAGE_BUCKET}/$newFileName"
    }

    private fun getVideoFormat(fileName: String): VideoFormat {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "mp4" -> VideoFormat.MP4
            "webm" -> VideoFormat.WEBM
            "mov" -> VideoFormat.MOV
            "avi" -> VideoFormat.AVI
            "mkv" -> VideoFormat.MKV
            else -> throw VideoValidationException("Unsupported video format: $extension")
        }
    }

    suspend fun extractVideoMetadata(videoBytes: ByteArray, fileName: String): VideoMetadata {
        val tempFile = Files.createTempFile("video_meta_", ".tmp")
        try {
            Files.write(tempFile, videoBytes)
            return validateAndGetMetadata(tempFile, fileName)
        } finally {
            Files.deleteIfExists(tempFile)
        }
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
    val height: Int
)

enum class VideoFormat { MP4, WEBM, MOV, AVI, MKV }

// Exceptions
class VideoUploadException(message: String) : IllegalStateException(message)
class VideoValidationException(message: String) : IllegalArgumentException(message)