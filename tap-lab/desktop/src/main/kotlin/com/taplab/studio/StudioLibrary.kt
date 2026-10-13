package com.taplab.studio

import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.ModeBundle
import com.taplab.experiment.model.ModePackage
import com.taplab.experiment.model.ScreenSpec
import java.io.File

class StudioLibrary(private val root: File) {
    fun list(): List<BehaviorMode> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, ModePackage.FILE_NAME).isFile }
            ?.mapNotNull { directory -> runCatching { ModePackage.read(directory).mode }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun load(id: String): Pair<ModeBundle, ByteArray?> {
        val directory = directory(id)
        val bundle = ModePackage.read(directory)
        val screenFile = File(directory, SCREEN)
        val screen = if (screenFile.isFile && screenFile.length() == SCREEN_BYTES.toLong()) screenFile.readBytes() else null
        return bundle to screen
    }

    fun save(bundle: ModeBundle, screen: ByteArray?) {
        val directory = directory(bundle.mode.id)
        ModePackage.write(directory, bundle)
        val screenFile = File(directory, SCREEN)
        if (screen != null && screen.size == SCREEN_BYTES) {
            screenFile.writeBytes(screen)
        } else {
            screenFile.delete()
        }
    }

    fun delete(id: String) {
        directory(id).deleteRecursively()
    }

    fun export(parent: File, bundle: ModeBundle): File {
        val destination = File(parent, ModePackage.folderName(bundle.mode))
        if (destination.exists()) destination.deleteRecursively()
        ModePackage.write(destination, bundle)
        return destination
    }

    private fun directory(id: String): File {
        if (!id.matches(SAFE_ID)) throw IllegalArgumentException("模式编号无效")
        return File(root, id)
    }

    companion object {
        const val SCREEN = "screen.rgba"
        val SCREEN_BYTES = ScreenSpec.WIDTH * ScreenSpec.HEIGHT * 4
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")

        fun inHomeDirectory(): StudioLibrary {
            return StudioLibrary(File(System.getProperty("user.home"), ".taplab/modes"))
        }
    }
}
