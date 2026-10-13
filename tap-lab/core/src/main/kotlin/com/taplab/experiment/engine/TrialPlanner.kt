package com.taplab.experiment.engine

import com.taplab.experiment.human.AimPoint
import com.taplab.experiment.human.ReactionTime
import com.taplab.experiment.model.BranchId
import com.taplab.experiment.model.DelaySpec
import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.model.Frame
import com.taplab.experiment.model.MatchedBranch
import com.taplab.experiment.model.PlannedTap
import com.taplab.experiment.vision.ColorMatcher
import java.util.Random

object TrialPlanner {
    /** 一号功能的两个分支优先于二号功能，功能内一号分支优先于二号分支。 */
    fun firstMatch(frame: Frame, config: ExperimentConfig): MatchedBranch? {
        for (id in BranchId.entries) {
            val branch = config.branch(id)
            if (!branch.enabled) continue
            if (ColorMatcher.matches(frame, branch)) {
                return MatchedBranch(id, branch)
            }
        }
        return null
    }

    fun plan(
        random: Random,
        frame: Frame,
        config: ExperimentConfig,
        matched: MatchedBranch,
    ): PlannedTap {
        val branch = matched.branch
        val delay = ReactionTime.sampleMillis(
            random,
            DelaySpec(
                distribution = config.distribution,
                medianMs = branch.medianMs,
                dispersion = branch.dispersion,
                floorMs = branch.floorMs,
                uniformMaxMs = branch.uniformMaxMs,
            ),
        )
        val (tapX, tapY) = AimPoint.sample(
            random,
            branch.aimX,
            branch.aimY,
            branch.sigmaX,
            branch.sigmaY,
            frame.width,
            frame.height,
        )
        return PlannedTap(
            id = matched.id,
            delayMs = delay,
            aimX = branch.aimX,
            aimY = branch.aimY,
            tapX = tapX,
            tapY = tapY,
        )
    }
}
