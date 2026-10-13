package com.taplab.experiment.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class RootShell {
    private val su: String = resolveSu()

    @Volatile
    private var current: Process? = null

    fun available(): Boolean {
        return try {
            val result = exec("id", timeoutSec = 3)
            result.exitCode == 0 && result.text.contains("uid=0")
        } catch (error: Exception) {
            false
        }
    }

    fun grantOverlay(packageName: String): Boolean {
        if (!packageName.matches(PACKAGE_NAME)) return false
        return try {
            exec("appops set $packageName SYSTEM_ALERT_WINDOW allow", timeoutSec = 4).exitCode == 0
        } catch (error: Exception) {
            false
        }
    }

    fun exec(command: String, timeoutSec: Long = 8): ShellResult {
        val bytes = execBytes(command, timeoutSec)
        return ShellResult(bytes.exitCode, bytes.stdout.toString(Charsets.UTF_8).trim(), bytes.stderr)
    }

    fun execBytes(command: String, timeoutSec: Long = 8): BytesResult {
        synchronized(this) {
            val process = ProcessBuilder(su, "-c", command).start()
            current = process
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val outThread = Thread({ process.inputStream.copyTo(stdout) }, "taplab-su-out")
            val errThread = Thread({ process.errorStream.copyTo(stderr) }, "taplab-su-err")
            outThread.start()
            errThread.start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                current = null
                throw IllegalStateException("命令超时")
            }
            outThread.join()
            errThread.join()
            current = null
            return BytesResult(process.exitValue(), stdout.toByteArray(), stderr.toString(Charsets.UTF_8).trim())
        }
    }

    fun cancel() {
        current?.destroyForcibly()
    }

    private fun resolveSu(): String {
        return listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
            .firstOrNull { File(it).canExecute() }
            ?: "su"
    }

    data class ShellResult(val exitCode: Int, val text: String, val stderr: String)

    data class BytesResult(val exitCode: Int, val stdout: ByteArray, val stderr: String)

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z0-9._]+")
    }
}
