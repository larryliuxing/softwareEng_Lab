package com.taplab.experiment.model

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

object ConfigCodec {
    fun encode(config: ExperimentConfig): String {
        val stored = config.sanitized()
        val properties = Properties()
        properties.setProperty("version", "1")
        properties.setProperty("distribution", stored.distribution.name)
        properties.setProperty("seed", stored.seed.toString())
        properties.setProperty("scanIntervalMs", stored.scanIntervalMs.toString())
        for (id in BranchId.entries) {
            writeBranch(properties, id, stored.branch(id))
        }
        val writer = StringWriter()
        properties.store(writer, null)
        return writer.toString()
    }

    fun decode(text: String): ExperimentConfig {
        val defaults = ExperimentConfig.default()
        val properties = Properties()
        properties.load(StringReader(text))
        val distribution = runCatching {
            Distribution.valueOf(properties.getProperty("distribution") ?: "")
        }.getOrDefault(defaults.distribution)
        val branches = BranchId.entries.associateWith { id ->
            readBranch(properties, id, defaults.branch(id))
        }
        return ExperimentConfig(
            branches = branches,
            distribution = distribution,
            seed = properties.long("seed", defaults.seed),
            scanIntervalMs = properties.long("scanIntervalMs", defaults.scanIntervalMs),
        ).sanitized()
    }

    private fun writeBranch(properties: Properties, id: BranchId, branch: BranchConfig) {
        val key = id.key
        properties.setProperty("$key.enabled", branch.enabled.toString())
        properties.setProperty("$key.regionX", branch.regionX.toString())
        properties.setProperty("$key.regionY", branch.regionY.toString())
        properties.setProperty("$key.regionW", branch.regionW.toString())
        properties.setProperty("$key.regionH", branch.regionH.toString())
        properties.setProperty("$key.color", branch.colorHex())
        properties.setProperty("$key.tolerance", branch.tolerance.toString())
        properties.setProperty("$key.matchPercent", branch.matchPercent.toString())
        properties.setProperty("$key.aimX", branch.aimX.toString())
        properties.setProperty("$key.aimY", branch.aimY.toString())
        properties.setProperty("$key.medianMs", branch.medianMs.toString())
        properties.setProperty("$key.dispersion", branch.dispersion.toString())
        properties.setProperty("$key.floorMs", branch.floorMs.toString())
        properties.setProperty("$key.uniformMaxMs", branch.uniformMaxMs.toString())
        properties.setProperty("$key.sigmaX", branch.sigmaX.toString())
        properties.setProperty("$key.sigmaY", branch.sigmaY.toString())
    }

    private fun readBranch(properties: Properties, id: BranchId, fallback: BranchConfig): BranchConfig {
        val key = id.key
        if (properties.getProperty("$key.enabled") == null) return fallback
        return BranchConfig(
            enabled = properties.getProperty("$key.enabled")?.toBooleanStrictOrNull() ?: fallback.enabled,
            regionX = properties.int("$key.regionX", fallback.regionX),
            regionY = properties.int("$key.regionY", fallback.regionY),
            regionW = properties.int("$key.regionW", fallback.regionW),
            regionH = properties.int("$key.regionH", fallback.regionH),
            color = parseColor(properties.getProperty("$key.color") ?: "") ?: fallback.color,
            tolerance = properties.int("$key.tolerance", fallback.tolerance),
            matchPercent = properties.int("$key.matchPercent", fallback.matchPercent),
            aimX = properties.int("$key.aimX", fallback.aimX),
            aimY = properties.int("$key.aimY", fallback.aimY),
            medianMs = properties.long("$key.medianMs", fallback.medianMs),
            dispersion = properties.double("$key.dispersion", fallback.dispersion),
            floorMs = properties.long("$key.floorMs", fallback.floorMs),
            uniformMaxMs = properties.long("$key.uniformMaxMs", fallback.uniformMaxMs),
            sigmaX = properties.double("$key.sigmaX", fallback.sigmaX),
            sigmaY = properties.double("$key.sigmaY", fallback.sigmaY),
        )
    }

    private fun Properties.int(key: String, fallback: Int): Int {
        return getProperty(key)?.trim()?.toIntOrNull() ?: fallback
    }

    private fun Properties.long(key: String, fallback: Long): Long {
        return getProperty(key)?.trim()?.toLongOrNull() ?: fallback
    }

    private fun Properties.double(key: String, fallback: Double): Double {
        return getProperty(key)?.trim()?.toDoubleOrNull() ?: fallback
    }
}
