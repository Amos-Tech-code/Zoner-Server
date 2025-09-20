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

    // Compression settings
    private const val TARGET_SIZE_KB = 100
    private const val MAX_DIMENSION = 1024
    private const val PROFILE_TARGET_SIZE_KB = 500
    private const val PROFILE_MAX_DIMENSION = 2048
    private const val PROFILE_MIN_QUALITY = 0.8f
    private const val MAX_FILE_SIZE_MB = 20
    private const val MAX_IMAGE_DIMENSION = 4096

    // Supported formats
    private val SUPPORTED_FORMATS = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val LOSSY_FORMATS = setOf("jpg", "jpeg")
    private val LOSSLESS_FORMATS = setOf("png", "webp", "gif")

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

            // Validate image
            validateImage(tempFile, originalFileName)

            // Determine compression settings based on folder type
            val isProfile = folder.contains("profile", ignoreCase = true)
            val compressedBytes = if (isProfile) {
                compressProfileImage(tempFile, originalFileName)
            } else {
                compressImage(tempFile, originalFileName)
            }

            // Upload to storage
            return uploadToStorage(compressedBytes, folder, originalFileName)
        } catch (e: Exception) {
            logger.error("Failed to upload image to folder $folder", e)
            throw when (e) {
                is ImageValidationException -> e
                is ImageProcessingException -> e
                else -> ImageUploadException("Failed to upload image: ${e.message}")
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
        val fileExtension = originalFileName.substringAfterLast(".", "").lowercase()

        if (fileExtension !in SUPPORTED_FORMATS) {
            throw ImageValidationException("Unsupported image format: $fileExtension. Supported: ${SUPPORTED_FORMATS.joinToString()}")
        }

        return Pair(imagePart, originalFileName)
    }

    private fun validateImage(file: java.nio.file.Path, fileName: String) {
        val fileSize = Files.size(file)
        val sizeMB = fileSize / (1024.0 * 1024.0)

        if (sizeMB > MAX_FILE_SIZE_MB) {
            throw ImageValidationException("Image size ${"%.2f".format(sizeMB)}MB exceeds maximum allowed size of ${MAX_FILE_SIZE_MB}MB")
        }

        val image = ImageIO.read(file.toFile()) ?: throw ImageValidationException("Invalid or corrupted image file")

        if (image.width > MAX_IMAGE_DIMENSION || image.height > MAX_IMAGE_DIMENSION) {
            throw ImageValidationException("Image dimensions too large. Maximum allowed: ${MAX_IMAGE_DIMENSION}x${MAX_IMAGE_DIMENSION}")
        }
    }

    private fun compressImage(file: java.nio.file.Path, fileName: String): ByteArray {
        val originalImage = ImageIO.read(file.toFile())
        require(originalImage != null) { "Failed to read image" }

        val fileExtension = fileName.substringAfterLast(".", "").lowercase()
        val format = if (fileExtension in LOSSY_FORMATS) "jpeg" else fileExtension

        // Scale image if needed
        val scaledImage = scaleImage(originalImage, MAX_DIMENSION)

        // Compress based on format
        return if (format in LOSSY_FORMATS) {
            compressJpegImage(scaledImage, TARGET_SIZE_KB * 1024)
        } else {
            compressLosslessImage(scaledImage, format, TARGET_SIZE_KB * 1024)
        }
    }

    private fun compressProfileImage(file: java.nio.file.Path, fileName: String): ByteArray {
        val originalImage = ImageIO.read(file.toFile())
        require(originalImage != null) { "Failed to read image" }

        val fileExtension = fileName.substringAfterLast(".", "").lowercase()
        val format = if (fileExtension in LOSSY_FORMATS) "jpeg" else fileExtension

        // Scale image if needed
        val scaledImage = scaleImage(originalImage, PROFILE_MAX_DIMENSION)

        // Compress with profile-specific settings
        return if (format in LOSSY_FORMATS) {
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

    private fun compressLosslessImage(image: BufferedImage, format: String, targetSize: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()

        try {
            ImageIO.write(image, format, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // For lossless formats, if still too large, convert to JPEG
            if (compressedBytes.size > targetSize) {
                logger.warn("Lossless image too large (${compressedBytes.size} bytes), converting to JPEG")
                return compressJpegImage(image, targetSize)
            }

            return compressedBytes
        } catch (e: Exception) {
            throw ImageProcessingException("Failed to compress $format image: ${e.message}")
        }
    }

    private suspend fun uploadToStorage(imageBytes: ByteArray, folder: String, originalFileName: String): String {
        val fileExtension = originalFileName.substringAfterLast(".", "jpg")
        val timestamp = System.currentTimeMillis()
        val randomUUID = UUID.randomUUID().toString().take(8)
        val newFileName = "${folder}/image_${timestamp}_${randomUUID}.$fileExtension"

        val contentType = when (fileExtension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

        val response = client.put("$STORAGE_URL/${STORAGE_BUCKET}/$newFileName") {
            headers {
                append("apikey", SupabaseConfig.SUPABASE_KEY)
                append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                append("Content-Type", contentType)
            }
            setBody(imageBytes)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            logger.error("Supabase upload failed: ${response.status} - $errorBody")
            throw ImageUploadException("Failed to upload image to storage: ${response.status}")
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

    suspend fun getImageDimensions(imageUrl: String): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.get(imageUrl)
                if (response.status.isSuccess()) {
                    val bytes = response.readBytes()
                    val image = ImageIO.read(ByteArrayInputStream(bytes))
                    if (image != null) {
                        Pair(image.width, image.height)
                    } else {
                        throw ImageProcessingException("Failed to read image dimensions")
                    }
                } else {
                    throw ImageUploadException("Failed to fetch image: ${response.status}")
                }
            } catch (e: Exception) {
                throw ImageUploadException("Failed to get image dimensions: ${e.message}")
            }
        }
    }


    /**
     * Generates a BlurHash string from image bytes
     * @param imageBytes the image bytes to generate blurhash from
     * @return BlurHash string representation
     * @throws ImageProcessingException if blurhash generation fails
     */
    fun generateBlurHash(imageBytes: ByteArray): String {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            if (image == null) {
                throw ImageProcessingException("Failed to read image for blurhash generation")
            }

            generateBlurHashFromImage(image)
        } catch (e: Exception) {
            logger.error("Failed to generate blurhash", e)
            throw ImageProcessingException("Blurhash generation failed: ${e.message}")
        }
    }

    /**
     * Generates a BlurHash string from a BufferedImage
     * @param image the image to generate blurhash from
     * @param componentsX number of components in X direction (default: 4)
     * @param componentsY number of components in Y direction (default: 3)
     * @return BlurHash string representation
     */
    fun generateBlurHashFromImage(image: BufferedImage, componentsX: Int = 4, componentsY: Int = 3): String {
        validateBlurHashComponents(componentsX, componentsY)

        // Resize image to optimal size for blurhash generation (smaller is faster)
        val optimizedImage = resizeForBlurHash(image, maxWidth = 64, maxHeight = 64)

        // Generate blurhash using our pure Kotlin implementation
        return BlurHashEncoder.encode(optimizedImage, componentsX, componentsY)
    }

    /**
     * Validates blurhash component parameters
     * @throws IllegalArgumentException if components are invalid
     */
    private fun validateBlurHashComponents(componentsX: Int, componentsY: Int) {
        require(componentsX in 1..9) { "componentsX must be between 1 and 9" }
        require(componentsY in 1..9) { "componentsY must be between 1 and 9" }
        require(componentsX * componentsY <= 81) { "Total components (x*y) must not exceed 81" }
    }

    /**
     * Resizes image for optimal blurhash generation performance
     */
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
            originalImage.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH),
            0, 0, null
        )
        g2d.dispose()

        return resizedImage
    }

    /**
     * Extracts RGB pixels from BufferedImage
     */
    private fun extractPixels(image: BufferedImage): IntArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)

        image.getRGB(0, 0, width, height, pixels, 0, width)
        return pixels
    }

    /**
     * Decodes a BlurHash string back to an image (for verification/testing)
     */
    fun decodeBlurHash(blurHash: String, width: Int = 32, height: Int = 32): BufferedImage? {
        return try {
            // Note: Decoding is not implemented in this version
            // You can implement it if needed using the same algorithm
            null
        } catch (e: Exception) {
            logger.warn("Failed to decode blurhash: $blurHash", e)
            null
        }
    }

    /**
     * Validates if a string is a valid BlurHash
     */
    fun isValidBlurHash(blurHash: String): Boolean {
        return try {
            // Basic validation: check length and character set
            if (blurHash.length < 6) return false

            // BlurHash uses Base83 encoding (0-9, A-Z, a-z, #$%*+,-.:;=?@[]^_{|}~)
            val validChars = CharRange('0', '9') +
                    CharRange('A', 'Z') +
                    CharRange('a', 'z') +
                    "#\$%*+,-.:;=?@[]^_{|}~".toSet()

            blurHash.all { it in validChars }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates blurhash with optimized components based on image aspect ratio
     */
    fun generateOptimizedBlurHash(imageBytes: ByteArray): String {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw ImageProcessingException("Failed to read image")

        val aspectRatio = image.width.toFloat() / image.height.toFloat()

        // Adjust components based on aspect ratio for better visual results
        val (componentsX, componentsY) = when {
            aspectRatio > 1.5 -> Pair(5, 3)  // Wide image
            aspectRatio < 0.67 -> Pair(3, 5) // Tall image
            else -> Pair(4, 3)               // Square-ish image
        }

        return generateBlurHashFromImage(image, componentsX, componentsY)
    }

    /**
     * Enhanced upload method that includes blurhash generation
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

            validateImage(tempFile, originalFileName)

            // Read image bytes for blurhash generation
            val imageBytes = Files.readAllBytes(tempFile)

            // Generate blurhash before compression (better quality)
            val blurHash = generateOptimizedBlurHash(imageBytes)

            // Compress and upload image
            val isProfile = folder.contains("profile", ignoreCase = true)
            val compressedBytes = if (isProfile) {
                compressProfileImage(tempFile, originalFileName)
            } else {
                compressImage(tempFile, originalFileName)
            }

            val imageUrl = uploadToStorage(compressedBytes, folder, originalFileName)

            return Pair(imageUrl, blurHash)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}


// Extension function for character ranges
private operator fun CharRange.plus(other: CharRange): Set<Char> {
    return this.toSet() + other.toSet()
}

private operator fun Set<Char>.plus(other: CharRange): Set<Char> {
    return this + other.toSet()
}

private operator fun Set<Char>.plus(string: String): Set<Char> {
    return this + string.toSet()
}

    // Custom exceptions
class ImageUploadException(message: String) : IllegalStateException(message)
class ImageValidationException(message: String) : IllegalArgumentException(message)
class ImageProcessingException(message: String) : IllegalStateException(message)