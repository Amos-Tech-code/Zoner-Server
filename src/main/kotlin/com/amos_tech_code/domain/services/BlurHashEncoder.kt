package com.amos_tech_code.domain.services

import java.awt.image.BufferedImage
import kotlin.math.*

/**
 * Pure Kotlin BlurHash implementation based on the original algorithm
 * Reference: https://github.com/woltapp/blurhash
 */

object BlurHashEncoder {

    private const val BASE83_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    fun encode(image: BufferedImage, componentsX: Int = 4, componentsY: Int = 3): String {
        require(componentsX in 1..9) { "componentsX must be between 1 and 9" }
        require(componentsY in 1..9) { "componentsY must be between 1 and 9" }

        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val factors = Array(componentsX * componentsY) { DoubleArray(3) }

        for (j in 0 until componentsY) {
            for (i in 0 until componentsX) {
                val factor = factors[j * componentsX + i]
                var norm = 0.0

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val basis = cos(Math.PI * i * x / width) * cos(Math.PI * j * y / height)
                        val pixel = pixels[y * width + x]

                        val r = ((pixel shr 16) and 0xFF) / 255.0
                        val g = ((pixel shr 8) and 0xFF) / 255.0
                        val b = (pixel and 0xFF) / 255.0

                        factor[0] += r * basis
                        factor[1] += g * basis
                        factor[2] += b * basis
                        norm += basis * basis
                    }
                }

                val scale = 1.0 / norm
                factor[0] *= scale
                factor[1] *= scale
                factor[2] *= scale
            }
        }

        return encode83(factors, componentsX, componentsY)
    }

    private fun encode83(factors: Array<DoubleArray>, componentsX: Int, componentsY: Int): String {
        val sizeFlag = (componentsX - 1) + (componentsY - 1) * 9
        var hash = encode83(sizeFlag, 1)

        val maxValue = if (factors.isNotEmpty()) {
            var max = 0.0
            for (i in factors.indices) {
                max = maxOf(max, abs(factors[i][0]), abs(factors[i][1]), abs(factors[i][2]))
            }
            val quantisedMax = (maxOf(0.0, minOf(82.0, floor(max * 166 - 0.5)))).toInt()
            encode83(quantisedMax, 1)
        } else {
            encode83(0, 1)
        }

        hash += maxValue

        for (i in factors.indices) {
            val factor = factors[i]
            hash += encode83(encodeDC(factor), 4)
        }

        for (i in factors.indices) {
            val factor = factors[i]
            hash += encode83(encodeAC(factor), 2)
        }

        return hash
    }

    private fun encodeDC(value: DoubleArray): Int {
        val r = linearTosRGB(value[0])
        val g = linearTosRGB(value[1])
        val b = linearTosRGB(value[2])
        return (r shl 16) + (g shl 8) + b
    }

    private fun encodeAC(value: DoubleArray): Int {
        val r = (signPow(value[0] / 0.5, 0.5) * 9 + 9.5).toInt()
        val g = (signPow(value[1] / 0.5, 0.5) * 9 + 9.5).toInt()
        val b = (signPow(value[2] / 0.5, 0.5) * 9 + 9.5).toInt()
        return r * 19 * 19 + g * 19 + b
    }

    private fun linearTosRGB(value: Double): Int {
        val v = maxOf(0.0, minOf(1.0, value))
        return if (v <= 0.0031308) {
            (v * 12.92 * 255 + 0.5).toInt()
        } else {
            ((1.055 * v.pow(1.0 / 2.4) - 0.055) * 255 + 0.5).toInt()
        }
    }

    private fun signPow(value: Double, exp: Double): Double {
        return abs(value).pow(exp) * if (value < 0) -1.0 else 1.0
    }

    private fun encode83(value: Int, length: Int): String {
        var result = ""
        var current = value
        for (i in 1..length) {
            val digit = current % 83
            current /= 83
            result = BASE83_CHARS[digit] + result
        }
        return result
    }

    fun decode(blurHash: String, width: Int, height: Int): IntArray {
        // Implementation for decoding if needed
        throw UnsupportedOperationException("Decode not implemented in this version")
    }
}