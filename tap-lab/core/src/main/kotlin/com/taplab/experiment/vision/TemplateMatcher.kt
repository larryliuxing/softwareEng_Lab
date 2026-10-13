package com.taplab.experiment.vision

import com.taplab.experiment.model.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class TemplateImage(
    val width: Int,
    val height: Int,
    val format: Int,
    val pixels: ByteArray,
)

object TemplateFile {
    fun encode(image: TemplateImage): ByteArray {
        val bytesPerPixel = bytesPerPixel(image.format)
        val body = image.width * image.height * bytesPerPixel
        val out = ByteArray(16 + body)
        out[0] = 'T'.code.toByte()
        out[1] = 'P'.code.toByte()
        out[2] = 'L'.code.toByte()
        out[3] = '1'.code.toByte()
        ByteBuffer.wrap(out, 4, 12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(image.width)
            putInt(image.height)
            putInt(image.format)
        }
        System.arraycopy(image.pixels, 0, out, 16, body.coerceAtMost(image.pixels.size))
        return out
    }

    fun decode(bytes: ByteArray): TemplateImage? {
        if (bytes.size < 16) return null
        if (bytes[0] != 'T'.code.toByte() || bytes[1] != 'P'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != '1'.code.toByte()
        ) {
            return null
        }
        val header = ByteBuffer.wrap(bytes, 4, 12).order(ByteOrder.LITTLE_ENDIAN)
        val width = header.int
        val height = header.int
        val format = header.int
        val bytesPerPixel = bytesPerPixel(format)
        if (width !in 1..320 || height !in 1..320 || bytesPerPixel == 0) return null
        val body = width * height * bytesPerPixel
        if (bytes.size < 16 + body) return null
        return TemplateImage(width, height, format, bytes.copyOfRange(16, 16 + body))
    }

    private fun bytesPerPixel(format: Int): Int {
        return when (format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> 0
        }
    }
}

object TemplateMatcher {
    fun matches(frame: Frame, template: TemplateImage, tolerance: Int, matchPercent: Int): Boolean {
        if (frame.bufferWidth != template.width || frame.bufferHeight != template.height) return false
        val bytesPerPixel = sharedBytesPerPixel(frame.format, template.format) ?: return false
        val total = template.width.toLong() * template.height
        if (total <= 0L) return false
        val percent = matchPercent.coerceIn(1, 100).toLong()
        val slack = tolerance.coerceIn(0, 255)
        var hits = 0L
        var seen = 0L
        for (y in 0 until template.height) {
            for (x in 0 until template.width) {
                seen++
                if (samePixel(frame, template, x, y, bytesPerPixel, slack)) hits++
                if (hits * 100L >= percent * total) return true
                val remaining = total - seen
                if ((hits + remaining) * 100L < percent * total) return false
            }
        }
        return hits * 100L >= percent * total
    }

    fun fromFrame(frame: Frame): TemplateImage? {
        val bytesPerPixel = when (frame.format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> return null
        }
        if (frame.bufferWidth !in 1..320 || frame.bufferHeight !in 1..320) return null
        val body = frame.bufferWidth * frame.bufferHeight * bytesPerPixel
        if (frame.pixels.size < body) return null
        return TemplateImage(frame.bufferWidth, frame.bufferHeight, frame.format, frame.pixels.copyOf(body))
    }

    private fun samePixel(
        frame: Frame,
        template: TemplateImage,
        x: Int,
        y: Int,
        bytesPerPixel: Int,
        tolerance: Int,
    ): Boolean {
        val frameIndex = (y * frame.bufferWidth + x) * bytesPerPixel
        val templateIndex = (y * template.width + x) * bytesPerPixel
        if (frameIndex + bytesPerPixel > frame.pixels.size || templateIndex + bytesPerPixel > template.pixels.size) {
            return false
        }
        val channels = if (bytesPerPixel == 3) 3 else 3
        for (channel in 0 until channels) {
            val frameChannel = channelAt(frame.format, frame.pixels, frameIndex, channel)
            val templateChannel = channelAt(template.format, template.pixels, templateIndex, channel)
            if (abs(frameChannel - templateChannel) > tolerance) return false
        }
        return true
    }

    private fun sharedBytesPerPixel(frameFormat: Int, templateFormat: Int): Int? {
        val frameBytes = bytesPerPixel(frameFormat)
        val templateBytes = bytesPerPixel(templateFormat)
        if (frameBytes == 0 || frameBytes != templateBytes) return null
        return frameBytes
    }

    private fun bytesPerPixel(format: Int): Int {
        return when (format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> 0
        }
    }

    private fun channelAt(format: Int, pixels: ByteArray, pixelIndex: Int, channel: Int): Int {
        val offset = if (format == 5) 2 - channel else channel
        return pixels[pixelIndex + offset].toInt() and 0xFF
    }
}
