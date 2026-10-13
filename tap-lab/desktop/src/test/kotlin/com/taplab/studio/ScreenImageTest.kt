package com.taplab.studio

import com.taplab.experiment.model.ScreenSpec
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenImageTest {
    @Test
    fun anyPictureIsPlacedOn720x1080() {
        val source = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        val color = 0x112233
        source.setRGB(0, 0, color)
        source.setRGB(1, 0, color)
        source.setRGB(0, 1, color)
        source.setRGB(1, 1, color)
        val rgba = ScreenImage.toRgba(source)
        assertEquals(ScreenSpec.WIDTH * ScreenSpec.HEIGHT * 4, rgba.size)
        assertEquals(0x11, rgba[0].toInt() and 0xFF)
        assertEquals(0x22, rgba[1].toInt() and 0xFF)
        assertEquals(0x33, rgba[2].toInt() and 0xFF)
    }

    @Test
    fun checkFlagDoesNotOpenAWindow() {
        main(arrayOf("--check"))
    }
}
