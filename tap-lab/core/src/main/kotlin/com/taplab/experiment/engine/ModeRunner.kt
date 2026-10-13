package com.taplab.experiment.engine

import com.taplab.experiment.human.AimPoint
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.Limits
import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.model.Step
import com.taplab.experiment.model.square
import com.taplab.experiment.vision.TemplateImage
import com.taplab.experiment.vision.TemplateMatcher
import java.util.Random

class ModeRunner(
    private val capture: Capture,
    private val tap: (Int, Int) -> Unit,
    private val waiter: Waiter,
    private val clock: () -> Long,
    private val isActive: () -> Boolean,
    private val onStatus: (String) -> Unit,
    private val random: Random,
    private val modeSource: () -> BehaviorMode?,
    private val templates: (BehaviorMode) -> Map<String, TemplateImage>,
) {
    private var screenWidth = ScreenSpec.WIDTH
    private var screenHeight = ScreenSpec.HEIGHT

    fun runOnce(): StepResult {
        val mode = modeSource()?.sanitized()
        if (mode == null) {
            onStatus("还没有读取行为模式")
            if (!waiter.waitMs(Limits.MIN_SCAN_MS, isActive)) return StepResult.CANCELLED
            return StepResult.NO_MATCH
        }
        if (mode.steps.isEmpty()) {
            onStatus("「${mode.name}」的循环是空的")
            if (!waiter.waitMs(Limits.MIN_SCAN_MS, isActive)) return StepResult.CANCELLED
            return StepResult.NO_MATCH
        }
        val started = clock()
        val images = templates(mode)
        val result = runSteps(mode, images, mode.steps, depth = 0)
        if (result == StepResult.CANCELLED) return result
        val rest = Limits.MIN_SCAN_MS - (clock() - started)
        if (rest > 0 && !waiter.waitMs(rest, isActive)) return StepResult.CANCELLED
        return StepResult.TAPPED
    }

    private fun runSteps(
        mode: BehaviorMode,
        images: Map<String, TemplateImage>,
        steps: List<Step>,
        depth: Int,
    ): StepResult {
        if (depth > MAX_DEPTH) {
            onStatus("分支嵌套太深")
            return StepResult.NO_MATCH
        }
        for (step in steps) {
            if (!isActive()) return StepResult.CANCELLED
            val result = when (step) {
                is Step.Click -> runClick(step)
                is Step.Delay -> runDelay(step)
                is Step.Compare -> runCompare(mode, images, step, depth)
            }
            if (result == StepResult.CANCELLED) return result
        }
        return StepResult.TAPPED
    }

    private fun runClick(step: Step.Click): StepResult {
        val aimed = AimPoint.sample(random, step.x, step.y, step.sigmaX, step.sigmaY, ScreenSpec.WIDTH, ScreenSpec.HEIGHT)
        val x = ScreenSpec.scaleX(aimed.first, screenWidth)
        val y = ScreenSpec.scaleY(aimed.second, screenHeight)
        onStatus("点击 $x, $y")
        tap(x, y)
        return StepResult.TAPPED
    }

    private fun runDelay(step: Step.Delay): StepResult {
        onStatus("延时 ${step.millis} ms")
        return if (waiter.waitMs(step.millis, isActive)) StepResult.TAPPED else StepResult.CANCELLED
    }

    private fun runCompare(
        mode: BehaviorMode,
        images: Map<String, TemplateImage>,
        step: Step.Compare,
        depth: Int,
    ): StepResult {
        val grab = mode.grabs.find { it.id == step.grabId }
        val image = images[step.grabId]
        if (grab == null || image == null) {
            onStatus("找不到抓取")
            return runSteps(mode, images, step.whenMiss, depth + 1)
        }
        val matched = capture.use { cropper ->
            screenWidth = cropper.screenWidth
            screenHeight = cropper.screenHeight
            val window = ScreenSpec.toFrameWindow(grab.square(), cropper.screenWidth, cropper.screenHeight)
            val frame = cropper.crop(window)
            TemplateMatcher.matches(frame, image, step.tolerance, step.matchPercent)
        }
        val name = grab.name
        onStatus(if (matched) "匹配 $name" else "不匹配 $name")
        val branch = if (matched) step.whenMatch else step.whenMiss
        return runSteps(mode, images, branch, depth + 1)
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}
