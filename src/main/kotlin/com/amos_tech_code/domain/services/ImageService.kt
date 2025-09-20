package com.amos_tech_code.domain.services

import com.amos_tech_code.application.configs.SupabaseConfig
import com.amos_tech_code.application.configs.SupabaseConfig.STORAGE_BUCKET
import com.amos_tech_code.domain.model.UploadFolder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

object ImageService {

    private val client = HttpClient(CIO)
    private val STORAGE_URL = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"
    private val logger = LoggerFactory.getLogger(ImageService::class.java)
    private val tika = Tika()

    // Compression settings
    private const val TARGET_SIZE_KB = 100
    private const val MAX_DIMENSION = 1024
    private const val PROFILE_TARGET_SIZE_KB = 500
    private const val PROFILE_MAX_DIMENSION = 2048
    private const val PROFILE_MIN_QUALITY = 0.8f
    private const val MAX_FILE_SIZE_MB = 20
    private const val MAX_IMAGE_DIMENSION = 4096

    // Supported MIME types
    private val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
        "image/bmp", "image/tiff", "image/svg+xml"
    )

    suspend fun uploadImage(multipart: MultiPartData, folder: String): String {
        val tempFile = Files.createTempFile("image_upload_", ".tmp")

        try {
            val (imageData, originalFileName) = extractImageData(multipart)

            // Save to temporary file for processing
            withContext(Dispatchers.IO) {
                imageData.streamProvider().use { input ->
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Validate image using content-based detection
            val detectedFormat = validateImageAndDetectFormat(tempFile, originalFileName)

            // Determine compression settings based on folder type
            val isProfile = folder.contains("profile", ignoreCase = true)
            val compressedBytes = if (isProfile) {
                compressProfileImage(tempFile, detectedFormat)
            } else {
                compressImage(tempFile, detectedFormat)
            }

            // Upload to storage with detected format
            return uploadToStorage(compressedBytes, folder, detectedFormat)
        } catch (e: Exception) {
            logger.error("Failed to upload image to folder $folder", e)
            throw when (e) {
                is ImageValidationException -> e
                is ImageProcessingException -> e
                else -> ImageUploadException("Failed to upload image.")
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    suspend fun uploadProfileImage(multipart: MultiPartData): String {
        return uploadImage(multipart, UploadFolder.PROFILE_PIC.folderName)
    }

    private suspend fun extractImageData(multipart: MultiPartData): Pair<PartData.FileItem, String> {
        val imagePart = multipart.readAllParts().filterIsInstance<PartData.FileItem>().firstOrNull()
            ?: throw ImageValidationException("No image file found in request")

        val originalFileName = imagePart.originalFileName ?: "image.jpg"

        return Pair(imagePart, originalFileName)
    }

    private fun validateImageAndDetectFormat(file: java.nio.file.Path, fileName: String): ImageFormat {
        val fileSize = Files.size(file)
        val sizeMB = fileSize / (1024.0 * 1024.0)

        if (sizeMB > MAX_FILE_SIZE_MB) {
            throw ImageValidationException("Image size ${"%.2f".format(sizeMB)}MB exceeds maximum allowed size of ${MAX_FILE_SIZE_MB}MB")
        }

        // Use Tika for content-based format detection
        val mimeType = try {
            tika.detect(file.toFile())
        } catch (e: Exception) {
            throw ImageValidationException("Failed to detect image format.")
        }

        logger.debug("Detected MIME type: $mimeType for file: $fileName")

        if (mimeType !in SUPPORTED_MIME_TYPES) {
            throw ImageValidationException("Unsupported image format: $mimeType. Supported: ${SUPPORTED_MIME_TYPES.joinToString()}")
        }

        val image = ImageIO.read(file.toFile()) ?: throw ImageValidationException("Invalid or corrupted image file")

        if (image.width > MAX_IMAGE_DIMENSION || image.height > MAX_IMAGE_DIMENSION) {
            throw ImageValidationException("Image dimensions too large. Maximum allowed: ${MAX_IMAGE_DIMENSION}x${MAX_IMAGE_DIMENSION}")
        }

        // Additional validation for problematic formats
        if (mimeType == "image/png") {
            validatePngImage(image)
        }

        return ImageFormat.fromMimeType(mimeType)
    }

    private fun validatePngImage(image: BufferedImage) {
        // Check for problematic color formats
        val problematicTypes = setOf(
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_INT_ARGB,
            BufferedImage.TYPE_4BYTE_ABGR_PRE,
            BufferedImage.TYPE_INT_ARGB_PRE
        )

        if (image.type in problematicTypes) {
            logger.warn("PNG image has potentially problematic color format: ${image.type}")
        }
    }

    private fun compressImage(file: java.nio.file.Path, format: ImageFormat): ByteArray {
        return try {
            val originalImage = ImageIO.read(file.toFile()) ?: throw ImageProcessingException("Failed to read image")

            // Scale image if needed
            val scaledImage = scaleImage(originalImage, MAX_DIMENSION)

            // Compress based on format
            if (format.isLossy) {
                compressJpegImage(scaledImage, TARGET_SIZE_KB * 1024)
            } else {
                compressLosslessImage(scaledImage, format, TARGET_SIZE_KB * 1024)
            }
        } catch (e: Exception) {
            logger.warn("Image compression failed, attempting fallback: ${e.message}")
            // Fallback: read original file and convert to JPEG
            Files.readAllBytes(file).let { originalBytes ->
                try {
                    val image = ImageIO.read(ByteArrayInputStream(originalBytes))
                    compressJpegImage(image ?: throw e, TARGET_SIZE_KB * 1024)
                } catch (fallbackError: Exception) {
                    throw ImageProcessingException("Failed to process image: ${fallbackError.message}")
                }
            }
        }
    }

    private fun compressProfileImage(file: java.nio.file.Path, format: ImageFormat): ByteArray {
        val originalImage = ImageIO.read(file.toFile())
        require(originalImage != null) { "Failed to read image" }

        // Scale image if needed
        val scaledImage = scaleImage(originalImage, PROFILE_MAX_DIMENSION)

        // Compress with profile-specific settings
        return if (format.isLossy) {
            compressJpegImage(scaledImage, PROFILE_TARGET_SIZE_KB * 1024, PROFILE_MIN_QUALITY)
        } else {
            compressLosslessImage(scaledImage, format, PROFILE_TARGET_SIZE_KB * 1024)
        }
    }

    private fun scaleImage(originalImage: BufferedImage, maxDimension: Int): BufferedImage {
        if (originalImage.width <= maxDimension && originalImage.height <= maxDimension) {
            return originalImage
        }

        val scale = maxDimension.toFloat() / maxOf(originalImage.width, originalImage.height)
        val newWidth = (originalImage.width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (originalImage.height * scale).roundToInt().coerceAtLeast(1)

        val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaledImage.createGraphics()
        g2d.drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        g2d.dispose()

        return scaledImage
    }

    private fun compressJpegImage(image: BufferedImage, targetSize: Int, minQuality: Float = 0.1f): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpeg")

        if (!writers.hasNext()) {
            throw ImageProcessingException("No JPEG writer available")
        }

        val writer = writers.next()
        try {
            var quality = 0.9f
            var compressedBytes: ByteArray

            do {
                outputStream.reset()
                val writeParam = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }

                val ios = ImageIO.createImageOutputStream(outputStream)
                writer.output = ios
                writer.write(null, IIOImage(image, null, null), writeParam)
                ios.close()

                compressedBytes = outputStream.toByteArray()
                quality -= 0.1f
            } while (compressedBytes.size > targetSize && quality >= minQuality)

            return compressedBytes
        } finally {
            writer.dispose()
        }
    }

    private fun compressLosslessImage(image: BufferedImage, format: ImageFormat, targetSize: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()

        try {
            // Convert image to a compatible format if needed
            val compatibleImage = if (format == ImageFormat.PNG &&
                (image.type == BufferedImage.TYPE_4BYTE_ABGR ||
                        image.type == BufferedImage.TYPE_INT_ARGB)) {
                // Convert ARGB to RGB for better compatibility
                val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                val g2d = rgbImage.createGraphics()
                g2d.drawImage(image, 0, 0, null)
                g2d.dispose()
                rgbImage
            } else {
                image
            }

            ImageIO.write(compatibleImage, format.extension, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // For lossless formats, if still too large, convert to JPEG
            if (compressedBytes.size > targetSize) {
                logger.warn("Lossless image too large (${compressedBytes.size} bytes), converting to JPEG")
                return compressJpegImage(compatibleImage, targetSize)
            }

            return compressedBytes
        } catch (e: Exception) {
            logger.warn("Failed to compress ${format.extension} image, converting to JPEG: ${e.message}")
            return compressJpegImage(image, targetSize)
        }
    }

    private suspend fun uploadToStorage(imageBytes: ByteArray, folder: String, format: ImageFormat): String {
        val timestamp = System.currentTimeMillis()
        val randomUUID = UUID.randomUUID().toString().take(8)
        val newFileName = "${folder}/image_${timestamp}_${randomUUID}.${format.extension}"

        val response = client.put("$STORAGE_URL/${STORAGE_BUCKET}/$newFileName") {
            headers {
                append("apikey", SupabaseConfig.SUPABASE_KEY)
                append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                append("Content-Type", format.mimeType)
            }
            setBody(imageBytes)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            logger.error("Supabase upload failed: ${response.status} - $errorBody")
            throw ImageUploadException("Failed to upload image to storage.")
        }

        return "$STORAGE_URL/public/${STORAGE_BUCKET}/$newFileName"
    }

    suspend fun deleteImage(imageUrl: String): Boolean {
        return try {
            val fileName = extractFileNameFromUrl(imageUrl)

            val response = client.delete("$STORAGE_URL/${STORAGE_BUCKET}/$fileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                }
            }

            if (!response.status.isSuccess()) {
                logger.warn("Failed to delete image: ${response.status} - ${response.bodyAsText()}")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to delete image: $imageUrl", e)
            false
        }
    }

    private fun extractFileNameFromUrl(imageUrl: String): String {
        return try {
            imageUrl.substringAfterLast("/${STORAGE_BUCKET}/")
        } catch (e: Exception) {
            throw ImageValidationException("Invalid image URL format")
        }
    }

    /**
     * Generates a BlurHash string from image bytes
     */
    fun generateBlurHash(imageBytes: ByteArray): String {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes))
                ?: throw ImageProcessingException("Failed to read image for blur hash generation")

            generateBlurHashFromImage(image)
        } catch (e: Exception) {
            logger.error("Failed to generate blur hash", e)
            throw ImageProcessingException("Blur hash generation failed: ${e.message}")
        }
    }

    /**
     * Generates a BlurHash string from a BufferedImage
     */
    fun generateBlurHashFromImage(image: BufferedImage, componentsX: Int = 4, componentsY: Int = 3): String {
        validateBlurHashComponents(componentsX, componentsY)

        // Resize image to optimal size for blur hash generation
        val optimizedImage = resizeForBlurHash(image, maxWidth = 64, maxHeight = 64)

        // Generate blur hash using our pure Kotlin implementation
        return BlurHashEncoder.encode(optimizedImage, componentsX, componentsY)
    }

    private fun validateBlurHashComponents(componentsX: Int, componentsY: Int) {
        require(componentsX in 1..9) { "componentsX must be between 1 and 9" }
        require(componentsY in 1..9) { "componentsY must be between 1 and 9" }
        require(componentsX * componentsY <= 81) { "Total components (x*y) must not exceed 81" }
    }

    private fun resizeForBlurHash(originalImage: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
        if (originalImage.width <= maxWidth && originalImage.height <= maxHeight) {
            return originalImage
        }

        val scale = minOf(
            maxWidth.toFloat() / originalImage.width,
            maxHeight.toFloat() / originalImage.height
        )

        val newWidth = (originalImage.width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (originalImage.height * scale).roundToInt().coerceAtLeast(1)

        val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = resizedImage.createGraphics()
        g2d.drawImage(
            originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH),
            0, 0, null
        )
        g2d.dispose()

        return resizedImage
    }

    /**
     * Generates blur hash with optimized components based on image aspect ratio
     */
    fun generateOptimizedBlurHash(imageBytes: ByteArray): String {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw ImageProcessingException("Failed to read image")

        val aspectRatio = image.width.toFloat() / image.height.toFloat()

        // Adjust components based on aspect ratio
        val (componentsX, componentsY) = when {
            aspectRatio > 1.5 -> Pair(5, 3)  // Wide image
            aspectRatio < 0.67 -> Pair(3, 5) // Tall image
            else -> Pair(4, 3)               // Square-ish image
        }

        return generateBlurHashFromImage(image, componentsX, componentsY)
    }

    /**
     * Enhanced upload method that includes blur hash generation
     */
    suspend fun uploadImageWithBlurHash(multipart: MultiPartData, folder: String): Pair<String, String> {
        val tempFile = Files.createTempFile("image_upload_", ".tmp")

        try {
            val (imageData, originalFileName) = extractImageData(multipart)

            // Save to temporary file
            withContext(Dispatchers.IO) {
                imageData.streamProvider().use { input ->
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }

            // Validate and detect format
            val detectedFormat = validateImageAndDetectFormat(tempFile, originalFileName)

            // Read image bytes for blur hash generation
            val imageBytes = Files.readAllBytes(tempFile)

            // Generate blur hash before compression (better quality)
            val blurHash = generateOptimizedBlurHash(imageBytes)

            // Compress and upload image
            val isProfile = folder.contains("profile", ignoreCase = true)
            val compressedBytes = if (isProfile) {
                compressProfileImage(tempFile, detectedFormat)
            } else {
                compressImage(tempFile, detectedFormat)
            }

            val imageUrl = uploadToStorage(compressedBytes, folder, detectedFormat)

            return Pair(imageUrl, blurHash)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}

// Image format data class
enum class ImageFormat(val mimeType: String, val extension: String, val isLossy: Boolean) {
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", false),
    WEBP("image/webp", "webp", false),
    GIF("image/gif", "gif", false),
    BMP("image/bmp", "bmp", false),
    TIFF("image/tiff", "tiff", false);

    companion object {
        fun fromMimeType(mimeType: String): ImageFormat {
            return when (mimeType.lowercase()) {
                "image/jpeg", "image/jpg" -> JPEG
                "image/png" -> PNG
                "image/webp" -> WEBP
                "image/gif" -> GIF
                "image/bmp" -> BMP
                "image/tiff" -> TIFF
                else -> throw ImageValidationException("Unsupported image MIME type: $mimeType")
            }
        }
    }
}

// Extension functions
private operator fun CharRange.plus(other: CharRange): Set<Char> = this.toSet() + other.toSet()
private operator fun Set<Char>.plus(other: CharRange): Set<Char> = this + other.toSet()
private operator fun Set<Char>.plus(string: String): Set<Char> = this + string.toSet()

// Custom exceptions
class ImageUploadException(message: String) : IllegalStateException(message)
class ImageValidationException(message: String) : IllegalArgumentException(message)
class ImageProcessingException(message: String) : IllegalStateException(message)