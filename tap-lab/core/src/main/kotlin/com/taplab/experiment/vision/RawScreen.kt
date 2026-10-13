package com.taplab.experiment.vision

import com.taplab.experiment.model.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RawScreen {
    data class Header(val width: Int, val height: Int, val format: Int, val bytesPerPixel: Int)

    /**
     * 解析 `screencap` 不带 `-p` 时的原始输出。
     * 文件头是小端的宽、高、像素格式，后面是逐行像素。
     * Android 10 上常见格式是 RGBA_8888（值为 1）。
     */
    fun parse(bytes: ByteArray): Frame? {
        val header = header(bytes) ?: return null
        val pixels = header.width.toLong() * header.height * header.bytesPerPixel
        if (pixels > Int.MAX_VALUE - 12) return null
        val end = 12 + pixels.toInt()
        if (bytes.size < end) return null
        return Frame(header.width, header.height, header.format, bytes.copyOfRange(12, end))
    }

    fun header(bytes: ByteArray): Header? {
        if (bytes.size < 12) return null
        val view = ByteBuffer.wrap(bytes, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
        val width = view.int
        val height = view.int
        val format = view.int
        if (width !in 1..8192 || height !in 1..8192) return null
        val bytesPerPixel = when (format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> return null
        }
        return Header(width, height, format, bytesPerPixel)
    }

    /**
     * 把若干条完整扫描行收成窗口里的紧凑像素。
     * [fullWidthRows] 从窗口的第一行开始，每行长度是整屏宽度。
     */
    fun cropInto(
        header: Header,
        fullWidthRows: ByteArray,
        window: ScanWindow,
        dest: ByteArray,
    ): Frame {
        val bytesPerPixel = header.bytesPerPixel
        val stride = header.width * bytesPerPixel
        val rowBytes = window.width * bytesPerPixel
        val needed = rowBytes.toLong() * window.height
        require(needed <= dest.size) { "裁剪缓冲区不够" }
        require(fullWidthRows.size >= stride.toLong() * window.height) { "扫描行不完整" }
        var destination = 0
        val sourceX = window.x * bytesPerPixel
        for (row in 0 until window.height) {
            val source = row * stride + sourceX
            System.arraycopy(fullWidthRows, source, dest, destination, rowBytes)
            destination += rowBytes
        }
        return Frame(
            width = header.width,
            height = header.height,
            format = header.format,
            pixels = dest,
            originX = window.x,
            originY = window.y,
            bufferWidth = window.width,
            bufferHeight = window.height,
        )
    }
}
