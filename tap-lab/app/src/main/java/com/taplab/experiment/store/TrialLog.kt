package com.taplab.experiment.store

import android.content.Context
import com.taplab.experiment.model.TrialRecord
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream

class TrialLog(context: Context) {
    private val file: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "trials.csv",
    )

    private var writer: BufferedWriter? = null

    fun path(): String = file.absolutePath

    fun append(record: TrialRecord) {
        synchronized(this) {
            val out = writer ?: openWriter()
            out.appendLine(
                listOf(
                    record.stimulusEpochMs.toString(),
                    csv(record.functionName),
                    csv(record.branchName),
                    csv(record.distribution),
                    record.plannedDelayMs.toString(),
                    record.actualIntervalMs.toString(),
                    record.aimX.toString(),
                    record.aimY.toString(),
                    record.tapX.toString(),
                    record.tapY.toString(),
                ).joinToString(","),
            )
            out.flush()
        }
    }

    fun close() {
        synchronized(this) {
            writer?.close()
            writer = null
        }
    }

    private fun openWriter(): BufferedWriter {
        file.parentFile?.mkdirs()
        val fresh = !file.exists() || file.length() == 0L
        val out = FileOutputStream(file, true).bufferedWriter()
        if (fresh) out.appendLine(HEADER)
        writer = out
        return out
    }

    private fun csv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private companion object {
        const val HEADER =
            "stimulus_epoch_ms,function,branch,distribution,planned_delay_ms,actual_interval_ms,aim_x,aim_y,tap_x,tap_y"
    }
}
