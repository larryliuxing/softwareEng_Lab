package com.taplab.experiment.store

import android.content.Context
import com.taplab.experiment.model.BehaviorCodec
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.ModeBundle
import com.taplab.experiment.model.ModePackage
import com.taplab.experiment.model.sanitized
import com.taplab.experiment.vision.TemplateFile
import com.taplab.experiment.vision.TemplateImage
import java.io.File

class ModeStore(context: Context) {
    private val root = File(context.filesDir, "modes")
    private val templates = File(root, "templates")
    private val exports = File(context.getExternalFilesDir(null) ?: context.filesDir, "export")
    private val preferences = context.getSharedPreferences("taplab", Context.MODE_PRIVATE)

    fun list(): List<BehaviorMode> {
        synchronized(lock) {
            val dir = root
            if (!dir.exists()) return emptyList()
            return dir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file -> runCatching { BehaviorCodec.decode(file.readText()) }.getOrNull() }
                ?.sortedBy { it.name }
                ?: emptyList()
        }
    }

    fun load(id: String): BehaviorMode? {
        synchronized(lock) {
            val file = File(root, "$id.json")
            if (!file.exists()) return null
            return runCatching { BehaviorCodec.decode(file.readText()) }.getOrNull()
        }
    }

    fun save(mode: BehaviorMode) {
        synchronized(lock) {
            root.mkdirs()
            File(root, "${mode.id}.json").writeText(BehaviorCodec.encode(mode.sanitized()))
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            val mode = loadUnlocked(id)
            File(root, "$id.json").delete()
            mode?.grabs?.forEach { templateFile(it.id).delete() }
            if (preferences.getString(ACTIVE, null) == id) {
                preferences.edit().remove(ACTIVE).apply()
            }
        }
    }

    fun addGrab(modeId: String, grab: GrabSpec, image: TemplateImage) {
        synchronized(lock) {
            val mode = loadUnlocked(modeId) ?: return
            writeTemplate(image, grab.id)
            saveUnlocked(mode.copy(grabs = mode.grabs + grab.sanitized()))
        }
    }

    fun activeId(): String? = preferences.getString(ACTIVE, null)

    fun setActive(id: String?) {
        preferences.edit().putString(ACTIVE, id).apply()
    }

    fun activeMode(): BehaviorMode? {
        val id = activeId() ?: return null
        return load(id)
    }

    fun loadTemplates(mode: BehaviorMode): Map<String, TemplateImage> {
        synchronized(lock) {
            return mode.grabs.mapNotNull { grab ->
                val file = templateFile(grab.id)
                if (!file.exists()) return@mapNotNull null
                val image = TemplateFile.decode(file.readBytes()) ?: return@mapNotNull null
                grab.id to image
            }.toMap()
        }
    }

    fun loadTemplate(grabId: String): TemplateImage? {
        synchronized(lock) {
            val file = templateFile(grabId)
            if (!file.exists()) return null
            return TemplateFile.decode(file.readBytes())
        }
    }

    fun export(mode: BehaviorMode): File {
        synchronized(lock) {
            val folder = File(exports, ModePackage.folderName(mode))
            ModePackage.write(folder, ModeBundle(mode, loadTemplates(mode)))
            return folder
        }
    }

    fun importBundle(bundle: ModeBundle): BehaviorMode {
        synchronized(lock) {
            val mode = bundle.mode.sanitized()
            val keep = mode.grabs.map { it.id }.toSet()
            loadUnlocked(mode.id)?.grabs?.forEach { grab ->
                if (grab.id !in keep) templateFile(grab.id).delete()
            }
            mode.grabs.forEach { grab ->
                val image = bundle.templates[grab.id] ?: return@forEach
                writeTemplate(image, grab.id)
            }
            saveUnlocked(mode)
            return mode
        }
    }

    fun inbox(): File = File(exports.parentFile ?: root, "inbox")

    fun inboxFolders(): List<File> {
        val dir = inbox()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, ModePackage.FILE_NAME).isFile }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun loadUnlocked(id: String): BehaviorMode? {
        val file = File(root, "$id.json")
        if (!file.exists()) return null
        return runCatching { BehaviorCodec.decode(file.readText()) }.getOrNull()
    }

    private fun saveUnlocked(mode: BehaviorMode) {
        root.mkdirs()
        File(root, "${mode.id}.json").writeText(BehaviorCodec.encode(mode.sanitized()))
    }

    private fun writeTemplate(image: TemplateImage, grabId: String) {
        templates.mkdirs()
        templateFile(grabId).writeBytes(TemplateFile.encode(image))
    }

    private fun templateFile(grabId: String): File = File(templates, "$grabId.bin")

    private companion object {
        val lock = Any()
        const val ACTIVE = "activeMode"
    }
}
