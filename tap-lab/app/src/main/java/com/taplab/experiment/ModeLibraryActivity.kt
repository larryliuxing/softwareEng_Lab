package com.taplab.experiment

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.store.ModeStore
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
            text = "分辨率固定 720×1080。模式保存在这台设备上，导出后可以把文件夹拷走。"
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
        root.addView(textButton("新建", 0xFF1C1B19.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { createMode() }
        })
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
}
