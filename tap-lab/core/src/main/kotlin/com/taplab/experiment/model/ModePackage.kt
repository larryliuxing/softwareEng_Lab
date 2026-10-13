package com.taplab.experiment.model

import com.taplab.experiment.vision.TemplateFile
import com.taplab.experiment.vision.TemplateImage
import java.io.File

data class ModeBundle(
    val mode: BehaviorMode,
    val templates: Map<String, TemplateImage>,
)

/**
 * 行为模式在磁盘上的样子：一个文件夹，里面是 mode.json 和每个抓取的 .bin。
 * Windows 配置程序写出这个文件夹，安卓应用按同样的格式读进去。
 */
object ModePackage {
    const val FILE_NAME = "mode.json"

    fun write(directory: File, bundle: ModeBundle) {
        val mode = bundle.mode.sanitized()
        directory.mkdirs()
        File(directory, FILE_NAME).writeText(BehaviorCodec.encode(mode))
        val keep = mode.grabs.map { fileId(it.id) }.toSet()
        directory.listFiles()?.forEach { file ->
            if (file.extension == "bin" && file.nameWithoutExtension !in keep) file.delete()
        }
        mode.grabs.forEach { grab ->
            val image = bundle.templates[grab.id] ?: return@forEach
            File(directory, "${fileId(grab.id)}.bin").writeBytes(TemplateFile.encode(image))
        }
    }

    fun read(directory: File): ModeBundle {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) throw IllegalArgumentException("这个文件夹里没有 mode.json")
        val mode = try {
            BehaviorCodec.decode(file.readText())
        } catch (error: Exception) {
            throw IllegalArgumentException("mode.json 读不了")
        }
        val templates = mode.grabs.mapNotNull { grab ->
            if (!grab.id.matches(SAFE_ID)) return@mapNotNull null
            val imageFile = File(directory, "${grab.id}.bin")
            if (!imageFile.isFile) return@mapNotNull null
            val image = TemplateFile.decode(imageFile.readBytes()) ?: return@mapNotNull null
            grab.id to image
        }.toMap()
        return ModeBundle(mode, templates)
    }

    fun folderName(mode: BehaviorMode): String {
        val name = mode.sanitized().name
            .replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fff_-]"), "_")
            .take(40)
            .ifBlank { "mode" }
        return "$name-${mode.id.take(8)}"
    }

    private fun fileId(id: String): String {
        if (!id.matches(SAFE_ID)) throw IllegalArgumentException("抓取编号不能作为文件名")
        return id
    }

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
}
