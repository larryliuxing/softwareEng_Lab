package com.taplab.experiment.vision

import com.taplab.experiment.model.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RawScreen {
    /**
     * 解析 `screencap` 不带 `-p` 时的原始输出。
     * 文件头是小端的宽、高、像素格式，后面是逐行像素。
     * Android 10 上常见格式是 RGBA_8888（值为 1）。
     */
    fun parse(bytes: ByteArray): Frame? {
        if (bytes.size < 12) return null
        val header = ByteBuffer.wrap(bytes, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
        val width = header.int
        val height = header.int
        val format = header.int
        if (width !in 1..8192 || height !in 1..8192) return null
        val bytesPerPixel = when (format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> return null
        }
        val pixels = width.toLong() * height * bytesPerPixel
        if (pixels > Int.MAX_VALUE - 12) return null
        val end = 12 + pixels.toInt()
        if (bytes.size < end) return null
        return Frame(width, height, format, bytes.copyOfRange(12, end))
    }
}
