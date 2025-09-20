package com.amos_tech_code.application.plugins

import io.ktor.server.application.Application
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.file.Files

fun Application.configureMediaServices() {
    // Initialize FFmpeg (required for JavaCV)
    FFmpegFrameGrabber.tryLoad()

    // Create necessary directories
    val tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "media_uploads")
    Files.createDirectories(tempDir)
}