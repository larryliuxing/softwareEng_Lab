package com.taplab.experiment.engine

class TapInjector(private val shell: RootShell) {
    fun tap(x: Int, y: Int) {
        val result = shell.exec("input tap $x $y")
        if (result.exitCode != 0) {
            throw IllegalStateException(result.stderr.ifBlank { result.text.ifBlank { "点击失败" } })
        }
    }
}
