package com.taplab.experiment.engine

import com.taplab.experiment.model.Frame
import com.taplab.experiment.vision.RawScreen

class ScreenGrabber(private val shell: RootShell) {
    fun capture(): Frame {
        val direct = try {
            shell.execBytes("screencap")
        } catch (error: Exception) {
            null
        }
        if (direct != null && direct.exitCode == 0) {
            RawScreen.parse(direct.stdout)?.let { return it }
        }
        val dumped = shell.exec("screencap $RAW_PATH && chmod 600 $RAW_PATH")
        if (dumped.exitCode != 0) {
            val reason = direct?.stderr?.ifBlank { dumped.stderr } ?: dumped.stderr
            throw IllegalStateException(reason.ifBlank { "截屏失败" })
        }
        val file = shell.execBytes("cat $RAW_PATH")
        return RawScreen.parse(file.stdout)
            ?: throw IllegalStateException("无法解析截屏")
    }

    private companion object {
        const val RAW_PATH = "/data/local/tmp/taplab.raw"
    }
}
