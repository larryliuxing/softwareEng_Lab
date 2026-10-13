package com.taplab.experiment.store

import android.content.Context
import com.taplab.experiment.model.ConfigCodec
import com.taplab.experiment.model.ExperimentConfig

class ConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("taplab", Context.MODE_PRIVATE)

    fun load(): ExperimentConfig {
        val stored = preferences.getString(KEY, null) ?: return ExperimentConfig.default()
        return runCatching { ConfigCodec.decode(stored) }.getOrDefault(ExperimentConfig.default())
    }

    fun save(config: ExperimentConfig) {
        preferences.edit().putString(KEY, ConfigCodec.encode(config.sanitized())).apply()
    }

    private companion object {
        const val KEY = "config"
    }
}
