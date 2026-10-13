package com.taplab.experiment

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.ModePackage
import com.taplab.experiment.store.ModeStore
import com.taplab.experiment.store.PackageImporter
import java.io.File
import com.taplab.experiment.ui.asLabel
import com.taplab.experiment.ui.dp
import com.taplab.experiment.ui.rounded
import com.taplab.experiment.ui.textButton
import java.util.UUID

class ModeLibraryActivity : Activity() {
    private lateinit var store: ModeStore
    private lateinit var list: LinearLayout
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ModeStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(0xFFF4F1EA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "行为模式"
            asLabel(22f, 0xFF1C1B19.toInt())
        })
        root.addView(TextView(this).apply {
            text = "分辨率固定 720×1080。可以在 Windows 上配好再导入，也可以在这里新建。导入后点读取，回到小熊点开始。"
            asLabel(13f, 0xFF5E5A54.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        message = TextView(this).apply {
            asLabel(13f, 0xFF5E5A54.toInt())
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(message)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(textButton("新建", 0xFF1C1B19.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { createMode() }
        })
        buttons.addView(textButton("导入", 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { showImport() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        root.addView(buttons)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val active = store.activeId()
        val modes = store.list()
        if (modes.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "还没有行为模式"
                asLabel(15f, 0xFF5E5A54.toInt())
            })
            return
        }
        modes.forEach { mode -> list.addView(row(mode, mode.id == active)) }
    }

    private fun row(mode: BehaviorMode, active: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFFEFEBE3.toInt(), dp(10).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(TextView(this@ModeLibraryActivity).apply {
                text = if (active) "${mode.name}  ·  已读取" else mode.name
                asLabel(16f, 0xFF1C1B19.toInt())
            })
            val actions = LinearLayout(this@ModeLibraryActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("读取", gap = false) {
                store.setActive(mode.id)
                message.text = "已读取「${mode.name}」。回到小熊后点开始。"
                render()
            })
            actions.addView(actionButton("编辑") {
                startActivity(android.content.Intent(this@ModeLibraryActivity, ModeEditorActivity::class.java).putExtra(ModeEditorActivity.EXTRA_ID, mode.id))
            })
            actions.addView(actionButton("导出") {
                val folder = store.export(mode)
                message.text = "已导出到\n${folder.absolutePath}"
            })
            actions.addView(actionButton("删除") { confirmDelete(mode) })
            addView(actions)
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun actionButton(label: String, gap: Boolean = true, action: () -> Unit) =
        textButton(label, 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { action() }
            minimumWidth = 0
            minWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (gap) marginStart = dp(6)
            }
        }

    private fun createMode() {
        val mode = BehaviorMode(id = UUID.randomUUID().toString(), name = "未命名")
        store.save(mode)
        startActivity(android.content.Intent(this, ModeEditorActivity::class.java).putExtra(ModeEditorActivity.EXTRA_ID, mode.id))
    }

    private fun showImport() {
        val folders = store.inboxFolders()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        box.addView(TextView(this).apply {
            text = "把 Windows 导出的文件夹拷到\n${store.inbox().absolutePath}\n里面每个模式单独放一个文件夹。也可以直接从文件管理器选。"
            asLabel(13f, 0xFF5E5A54.toInt())
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("导入行为模式")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("取消", null)
            .create()
        folders.forEach { folder ->
            box.addView(textButton(folder.name, 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
                setOnClickListener {
                    dialog.dismiss()
                    importResult(runCatching { ModePackage.read(folder) })
                }
            })
        }
        box.addView(textButton("从文件管理器选择", 0xFF1C1B19.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener {
                dialog.dismiss()
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    REQUEST_IMPORT,
                )
            }
        })
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val scratch = File(cacheDir, "import-scratch")
        val result = runCatching { PackageImporter.readTree(contentResolver, uri, scratch) }
        scratch.deleteRecursively()
        importResult(result)
    }

    private fun importResult(result: Result<com.taplab.experiment.model.ModeBundle>) {
        val bundle = result.getOrElse { error ->
            message.text = error.message ?: "导入失败"
            return
        }
        val mode = store.importBundle(bundle)
        val missing = mode.grabs.count { bundle.templates[it.id] == null }
        message.text = if (missing == 0) {
            "已导入「${mode.name}」。点读取后，回到小熊点开始。"
        } else {
            "已导入「${mode.name}」，有 $missing 个抓取没有图片。"
        }
        render()
    }

    private fun confirmDelete(mode: BehaviorMode) {
        AlertDialog.Builder(this)
            .setMessage("删除「${mode.name}」？抓取的图片也会一起删掉。")
            .setPositiveButton("删除") { _, _ ->
                store.delete(mode.id)
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private companion object {
        const val REQUEST_IMPORT = 41
    }
}
