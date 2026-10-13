package com.taplab.experiment.engine

import com.taplab.experiment.model.Frame
import com.taplab.experiment.model.Limits
import com.taplab.experiment.vision.RawScreen
import com.taplab.experiment.vision.ScanWindow
import com.taplab.experiment.vision.ScanWindows
import java.io.File
import java.io.RandomAccessFile

/**
 * 每次扫描只启动一次 screencap，把原始画面写进应用私有目录，再按分支窗口读取。
 * 不把整屏拷进堆，避免在 4GB 内存里反复分配 8MB。
 * 窗口像素只在本次 [use] 返回前有效。
 */
class ScreenGrabber(
    private val shell: RootShell,
    private val rawFile: File,
) : Capture {
    private val path: String = rawFile.absolutePath.also { absolute ->
        require(absolute.matches(SAFE_PATH)) { "截屏路径无法传给 shell" }
    }
    private var rowBuffer = ByteArray(0)
    private var tightBuffer = ByteArray(0)
    private var readFile = true
    private var sliceByShell = true
    private var fallbackImage: ByteArray? = null

    override fun <T> use(block: (ScreenCropper) -> T): T {
        synchronized(this) {
            dump()
            fallbackImage = null
            val header = readHeader()
            val cropper = object : ScreenCropper {
                override val screenWidth = header.width
                override val screenHeight = header.height

                override fun crop(window: ScanWindow): Frame {
                    val clamped = ScanWindows.clamp(window, header.width, header.height)
                        ?: return Frame(header.width, header.height, header.format, EMPTY, bufferWidth = 0, bufferHeight = 0)
                    return readCrop(header, clamped)
                }
            }
            return try {
                block(cropper)
            } finally {
                fallbackImage = null
            }
        }
    }

    fun release() {
        synchronized(this) {
            rawFile.delete()
        }
    }

    private fun dump() {
        rawFile.parentFile?.mkdirs()
        val result = shell.exec("screencap $path && chmod 644 $path")
        if (result.exitCode != 0) {
            throw IllegalStateException(result.text.ifBlank { result.stderr.ifBlank { "截屏失败" } })
        }
    }

    private fun readHeader(): RawScreen.Header {
        val bytes = readAt(0, 12)
        return RawScreen.header(bytes) ?: throw IllegalStateException("无法解析截屏")
    }

    private fun readCrop(header: RawScreen.Header, window: ScanWindow): Frame {
        val stride = header.width * header.bytesPerPixel
        val offset = 12L + window.y.toLong() * stride
        val rowBytes = window.height.toLong() * stride
        if (rowBytes <= 0L || rowBytes > Limits.MAX_FRAME_BYTES) {
            throw IllegalStateException("扫描区域太大，缩小这个分支的矩形")
        }
        val rows = readAt(offset, rowBytes.toInt())
        val tightBytes = window.width * window.height * header.bytesPerPixel
        if (tightBytes > Limits.MAX_FRAME_BYTES) {
            throw IllegalStateException("扫描区域太大，缩小这个分支的矩形")
        }
        if (tightBuffer.size < tightBytes) tightBuffer = ByteArray(tightBytes)
        return RawScreen.cropInto(header, rows, window, tightBuffer)
    }

    private fun readAt(offset: Long, length: Int): ByteArray {
        if (readFile) {
            try {
                val buffer = ensureRow(length)
                RandomAccessFile(rawFile, "r").use { file ->
                    file.seek(offset)
                    file.readFully(buffer, 0, length)
                }
                return buffer
            } catch (error: Exception) {
                readFile = false
            }
        }
        val bytes = readByShell(offset, length)
        val buffer = ensureRow(length)
        System.arraycopy(bytes, 0, buffer, 0, length)
        return buffer
    }

    private fun readByShell(offset: Long, length: Int): ByteArray {
        if (sliceByShell && fallbackImage == null) {
            val sliced = try {
                shell.execBytes("dd if=$path bs=65536 iflag=skip_bytes,count_bytes skip=$offset count=$length")
            } catch (error: Exception) {
                null
            }
            if (sliced != null && sliced.exitCode == 0 && sliced.stdout.size >= length) {
                return if (sliced.stdout.size == length) sliced.stdout else sliced.stdout.copyOf(length)
            }
            sliceByShell = false
        }
        val whole = fallbackImage ?: loadWholeFile()
        val start = offset.toInt()
        if (start < 0 || length < 0 || start.toLong() + length > whole.size) {
            throw IllegalStateException("截屏不完整")
        }
        return whole.copyOfRange(start, start + length)
    }

    private fun loadWholeFile(): ByteArray {
        val whole = shell.execBytes("cat $path")
        if (whole.exitCode != 0 || whole.stdout.isEmpty()) {
            throw IllegalStateException(whole.stderr.ifBlank { "读取截屏失败" })
        }
        fallbackImage = whole.stdout
        return whole.stdout
    }

    private fun ensureRow(length: Int): ByteArray {
        if (rowBuffer.size < length) rowBuffer = ByteArray(length)
        return rowBuffer
    }

    private companion object {
        val SAFE_PATH = Regex("[A-Za-z0-9._/=-]+")
        val EMPTY = ByteArray(0)
    }
}
