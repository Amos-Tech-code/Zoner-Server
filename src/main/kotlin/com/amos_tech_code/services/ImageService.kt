package com.amos_tech_code.services

import com.amos_tech_code.configs.SupabaseConfig
import com.amos_tech_code.configs.SupabaseConfig.STORAGE_BUCKET
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import java.awt.Image
import java.util.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

object ImageService {
    private val client = HttpClient(CIO)
    private val STORAGE_URL = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"

    // Defaults for generic images
    private const val TARGET_SIZE_KB = 100
    private const val MAX_DIMENSION = 1024

    // Profile picture specific settings (higher quality)
    private const val PROFILE_TARGET_SIZE_KB = 500  // Increased from 100KB to 500KB
    private const val PROFILE_MAX_DIMENSION = 2048  // Increased from 1024 to 2048px
    private const val PROFILE_MIN_QUALITY = 0.8f    // Don't go below 80% quality

    suspend fun uploadImage(multipart: MultiPartData, folder: String): String {
        try {
            val imageData = multipart.readAllParts().first { it is PartData.FileItem }
            val originalBytes = (imageData as PartData.FileItem).streamProvider().readBytes()
            
            // Compress image
            val compressedBytes = compressImage(originalBytes)
            
            // Get file extension from original filename
            val originalFileName = imageData.originalFileName ?: "image.jpg"
            val fileExtension = originalFileName.substringAfterLast(".", "jpg")
            
            // Generate new filename with timestamp and random UUID
            val timestamp = System.currentTimeMillis()
            val randomUUID = UUID.randomUUID().toString().take(8)
            val newFileName = "${folder}/image_${timestamp}_${randomUUID}.$fileExtension"
            
            // Upload to Supabase Storage
            val response = client.put("$STORAGE_URL/${STORAGE_BUCKET}/$newFileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                    append("Content-Type", "image/jpeg")
                }
                setBody(compressedBytes)
            }

            if (response.status.isSuccess()) {
                return "$STORAGE_URL/public/${STORAGE_BUCKET}/$newFileName"
            } else {
                throw IllegalStateException("Failed to upload image: ${response.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to upload image: ${e.message}")
        }
    }

    suspend fun uploadProfileImage(multipart: MultiPartData): String {
        try {
            val imageData = multipart.readAllParts().first { it is PartData.FileItem }
            val originalBytes = (imageData as PartData.FileItem).streamProvider().readBytes()

            // Compress with profile-specific settings
            val compressedBytes = compressProfileImage(originalBytes)

            // Generate filename
            val originalFileName = imageData.originalFileName ?: "profile.jpg"
            val fileExtension = originalFileName.substringAfterLast(".", "jpg")
            val timestamp = System.currentTimeMillis()
            val randomUUID = UUID.randomUUID().toString().take(8)
            val newFileName = "profile-pics/profile_${timestamp}_${randomUUID}.$fileExtension"

            // Upload to Supabase
            val response = client.put("$STORAGE_URL/${STORAGE_BUCKET}/$newFileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                    append("Content-Type", "image/jpeg")
                }
                setBody(compressedBytes)
            }

            if (response.status.isSuccess()) {
                return "$STORAGE_URL/public/${STORAGE_BUCKET}/$newFileName"
            } else {
                throw IllegalStateException("Failed to upload profile image: ${response.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to upload profile image: ${e.message}")
        }
    }

    private fun compressProfileImage(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)

        // Scale only if significantly larger than our max dimension
        val scaledImage = if (originalImage.width > PROFILE_MAX_DIMENSION ||
            originalImage.height > PROFILE_MAX_DIMENSION) {
            val scale = PROFILE_MAX_DIMENSION.toFloat() / maxOf(originalImage.width, originalImage.height)
            val newWidth = (originalImage.width * scale).roundToInt()
            val newHeight = (originalImage.height * scale).roundToInt()

            BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().run {
                    drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
                    dispose()
                }
            }
        } else {
            originalImage
        }

        // Use progressive compression with higher minimum quality
        val outputStream = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()

        try {
            val writeParam = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.95f  // Start with high quality
            }

            val ios = ImageIO.createImageOutputStream(outputStream)
            writer.output = ios
            writer.write(null, IIOImage(scaledImage, null, null), writeParam)

            // Only reduce quality if absolutely necessary
            var result = outputStream.toByteArray()
            if (result.size > PROFILE_TARGET_SIZE_KB * 1024) {
                writeParam.compressionQuality = PROFILE_MIN_QUALITY
                outputStream.reset()
                writer.write(null, IIOImage(scaledImage, null, null), writeParam)
                result = outputStream.toByteArray()
            }

            return result
        } finally {
            writer.dispose()
            outputStream.close()
        }
    }


    private fun compressImage(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)
        
        // Scale image if needed
        val scaledImage = if (originalImage.width > MAX_DIMENSION || originalImage.height > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(originalImage.width, originalImage.height)
            val newWidth = (originalImage.width * scale).roundToInt()
            val newHeight = (originalImage.height * scale).roundToInt()
            
            val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = scaledImage.createGraphics()
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
            g2d.dispose()
            scaledImage
        } else originalImage

        // Compress with different quality settings until target size is reached
        var quality = 1.0f
        var outputBytes: ByteArray
        val outputStream = ByteArrayOutputStream()
        
        do {
            outputStream.reset()
            val iter = ImageIO.getImageWritersByFormatName("jpeg").next()
            val writeParam = iter.defaultWriteParam
            writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            writeParam.compressionQuality = quality
            
            val ios = ImageIO.createImageOutputStream(outputStream)
            iter.output = ios
            iter.write(null, javax.imageio.IIOImage(scaledImage, null, null), writeParam)
            iter.dispose()
            ios.close()
            
            outputBytes = outputStream.toByteArray()
            quality -= 0.1f
        } while (outputBytes.size > TARGET_SIZE_KB * 1024 && quality > 0.1f)

        return outputBytes
    }

    suspend fun deleteImage(imageUrl: String) {
        try {
            val fileName = imageUrl.substringAfterLast("/${STORAGE_BUCKET}/")
            
            val response = client.delete("$STORAGE_URL/${STORAGE_BUCKET}/$fileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("Failed to delete image: ${response.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to delete image: ${e.message}")
        }
    }
} 