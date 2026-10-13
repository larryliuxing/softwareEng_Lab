package com.taplab.experiment.model

import kotlin.math.abs

object GrabGesture {
    fun begin(x: Int, y: Int, side: Int = 64): GrabSquare {
        return ScreenSpec.clampSquare(x, y, side)
    }

    fun drag(current: GrabSquare, pointerX: Int, pointerY: Int): GrabSquare {
        val reach = maxOf(abs(pointerX - current.centerX), abs(pointerY - current.centerY)) * 2
        return if (reach > current.side / 3) {
            ScreenSpec.clampSquare(current.centerX, current.centerY, reach)
        } else {
            ScreenSpec.clampSquare(pointerX, pointerY, current.side)
        }
    }
}
