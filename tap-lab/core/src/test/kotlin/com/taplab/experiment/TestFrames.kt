package com.taplab.experiment

import com.taplab.experiment.model.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun solidFrame(width: Int, height: Int, red: Int, green: Int, blue: Int, format: Int = 1): Frame {
    val bytesPerPixel = if (format == 3) 3 else 4
    val pixels = ByteArray(width * height * bytesPerPixel)
    for (index in 0 until width * height) {
        val offset = index * bytesPerPixel
        when (format) {
            5 -> {
                pixels[offset] = blue.toByte()
                pixels[offset + 1] = green.toByte()
                pixels[offset + 2] = red.toByte()
                pixels[offset + 3] = 255.toByte()
            }
            else -> {
                pixels[offset] = red.toByte()
                pixels[offset + 1] = green.toByte()
                pixels[offset + 2] = blue.toByte()
                if (bytesPerPixel == 4) pixels[offset + 3] = 255.toByte()
            }
        }
    }
    return Frame(width, height, format, pixels)
}

fun rawScreen(width: Int, height: Int, format: Int, pixels: ByteArray): ByteArray {
    val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
    header.putInt(width)
    header.putInt(height)
    header.putInt(format)
    return header.array() + pixels
}
