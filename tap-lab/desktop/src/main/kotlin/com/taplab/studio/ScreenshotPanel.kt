package com.taplab.studio

import com.taplab.experiment.model.GrabGesture
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.GrabSquare
import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.model.Step
import com.taplab.experiment.model.square
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel

class ScreenshotPanel : JPanel() {
    var onPick: (Int, Int) -> Unit = { _, _ -> }

    private var screen: BufferedImage? = null
    private var grabs: List<GrabSpec> = emptyList()
    private var clicks: List<Step.Click> = emptyList()
    private var draft: GrabSquare? = null
    private var grabbing = false
    private var picking = false

    init {
        background = Color(0xE7, 0xE1, 0xD6)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                val point = toLogical(event) ?: return
                when {
                    picking -> {
                        picking = false
                        onPick(point.first, point.second)
                        repaint()
                    }
                    grabbing -> {
                        draft = draft?.let { GrabGesture.drag(it, point.first, point.second) }
                            ?: GrabGesture.begin(point.first, point.second)
                        repaint()
                    }
                }
            }

            override fun mouseDragged(event: MouseEvent) {
                if (!grabbing) return
                val point = toLogical(event) ?: return
                val current = draft ?: return
                draft = GrabGesture.drag(current, point.first, point.second)
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    fun setScreen(rgba: ByteArray?) {
        screen = rgba?.let { ScreenImage.previewScreen(it) }
        repaint()
    }

    fun setOverlays(grabs: List<GrabSpec>, clicks: List<Step.Click>) {
        this.grabs = grabs
        this.clicks = clicks
        repaint()
    }

    fun beginGrab() {
        grabbing = true
        picking = false
        draft = null
        repaint()
    }

    fun cancelGrab() {
        grabbing = false
        draft = null
        repaint()
    }

    fun currentSquare(): GrabSquare? = draft

    fun beginPick() {
        grabbing = false
        draft = null
        picking = true
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val canvas = graphics as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val image = screen
        val rect = imageRect()
        if (image == null || rect == null) {
            canvas.color = Color(0x5E, 0x5A, 0x54)
            canvas.drawString("打开一张屏幕截图。最好是 720×1080 的 png 或 jpg。", 16, 32)
            return
        }
        canvas.drawImage(image, rect.x, rect.y, rect.width, rect.height, null)
        canvas.stroke = BasicStroke(2f)
        grabs.forEach { grab -> drawSquare(canvas, rect, grab.square(), Color(0x1C, 0x1B, 0x19)) }
        draft?.let { drawSquare(canvas, rect, it, Color(0x8C, 0x3A, 0x2F)) }
        canvas.color = Color(0x1F, 0x6B, 0x4A)
        clicks.forEach { click ->
            val x = rect.x + click.x * rect.width / ScreenSpec.WIDTH
            val y = rect.y + click.y * rect.height / ScreenSpec.HEIGHT
            canvas.drawLine(x - 6, y, x + 6, y)
            canvas.drawLine(x, y - 6, x, y + 6)
        }
        if (grabbing || picking) {
            canvas.color = Color(0x1C, 0x1B, 0x19)
            val hint = if (grabbing) "点中心，再拖出正方形" else "在画面上点一下"
            canvas.drawString(hint, rect.x + 8, rect.y + 18)
        }
    }

    private fun drawSquare(canvas: Graphics2D, rect: Rectangle, square: GrabSquare, color: Color) {
        val left = rect.x + square.left * rect.width / ScreenSpec.WIDTH
        val top = rect.y + square.top * rect.height / ScreenSpec.HEIGHT
        val right = rect.x + (square.left + square.side) * rect.width / ScreenSpec.WIDTH
        val bottom = rect.y + (square.top + square.side) * rect.height / ScreenSpec.HEIGHT
        canvas.color = color
        canvas.drawRect(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun imageRect(): Rectangle? {
        if (screen == null || width < 20 || height < 20) return null
        val availW = width - 16
        val availH = height - 16
        val aspect = ScreenSpec.WIDTH.toFloat() / ScreenSpec.HEIGHT
        var rectWidth = availW
        var rectHeight = (rectWidth / aspect).toInt()
        if (rectHeight > availH) {
            rectHeight = availH
            rectWidth = (rectHeight * aspect).toInt()
        }
        return Rectangle((width - rectWidth) / 2, (height - rectHeight) / 2, rectWidth.coerceAtLeast(1), rectHeight.coerceAtLeast(1))
    }

    private fun toLogical(event: MouseEvent): Pair<Int, Int>? {
        val rect = imageRect() ?: return null
        if (event.x !in rect.x until rect.x + rect.width || event.y !in rect.y until rect.y + rect.height) return null
        val x = ((event.x - rect.x).toLong() * ScreenSpec.WIDTH / rect.width).toInt().coerceIn(0, ScreenSpec.WIDTH - 1)
        val y = ((event.y - rect.y).toLong() * ScreenSpec.HEIGHT / rect.height).toInt().coerceIn(0, ScreenSpec.HEIGHT - 1)
        return x to y
    }
}
