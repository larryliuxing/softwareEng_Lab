package com.taplab.studio

import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.vision.TemplateImage
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ScreenImage {
    fun fromFile(file: File): ByteArray {
        val image = ImageIO.read(file) ?: throw IllegalArgumentException("打不开这张图片，请换 png 或 jpg")
        return toRgba(image)
    }

    fun toRgba(source: BufferedImage): ByteArray {
        val scaled = BufferedImage(ScreenSpec.WIDTH, ScreenSpec.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(source, 0, 0, ScreenSpec.WIDTH, ScreenSpec.HEIGHT, null)
        graphics.dispose()
        val out = ByteArray(ScreenSpec.WIDTH * ScreenSpec.HEIGHT * 4)
        var index = 0
        for (y in 0 until ScreenSpec.HEIGHT) {
            for (x in 0 until ScreenSpec.WIDTH) {
                val argb = scaled.getRGB(x, y)
                out[index++] = (argb shr 16 and 0xFF).toByte()
                out[index++] = (argb shr 8 and 0xFF).toByte()
                out[index++] = (argb and 0xFF).toByte()
                out[index++] = (argb ushr 24 and 0xFF).toByte()
            }
        }
        return out
    }

    fun preview(template: TemplateImage): BufferedImage {
        val image = BufferedImage(template.width, template.height, BufferedImage.TYPE_INT_ARGB)
        val bytesPerPixel = if (template.format == 3) 3 else 4
        for (y in 0 until template.height) {
            for (x in 0 until template.width) {
                val index = (y * template.width + x) * bytesPerPixel
                if (index + 2 >= template.pixels.size) continue
                val red: Int
                val green: Int
                val blue: Int
                if (template.format == 5) {
                    blue = template.pixels[index].toInt() and 0xFF
                    green = template.pixels[index + 1].toInt() and 0xFF
                    red = template.pixels[index + 2].toInt() and 0xFF
                } else {
                    red = template.pixels[index].toInt() and 0xFF
                    green = template.pixels[index + 1].toInt() and 0xFF
                    blue = template.pixels[index + 2].toInt() and 0xFF
                }
                val alpha = if (bytesPerPixel == 4 && index + 3 < template.pixels.size) {
                    template.pixels[index + 3].toInt() and 0xFF
                } else {
                    255
                }
                image.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
        return image
    }

    fun previewScreen(rgba: ByteArray): BufferedImage {
        return preview(TemplateImage(ScreenSpec.WIDTH, ScreenSpec.HEIGHT, 1, rgba))
    }
}
