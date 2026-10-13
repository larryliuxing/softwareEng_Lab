package com.taplab.experiment.ui

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.taplab.experiment.model.BranchConfig
import com.taplab.experiment.model.BranchId
import com.taplab.experiment.model.Distribution
import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.model.parseColor
import java.util.Locale

class SettingsPanelView(
    context: Context,
    config: ExperimentConfig,
    logPath: String,
    panelHeightPx: Int,
    private val onSample: (BranchId, Int, Int) -> Unit,
    private val onSave: (ExperimentConfig) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    val dragHandle: View
    private val screenInfo: TextView
    private val errorView: TextView
    private val editors = linkedMapOf<BranchId, BranchEditor>()
    private val logNormalButton: RadioButton
    private val uniformButton: RadioButton
    private val seedField: EditText
    private val scanField: EditText

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(0xFFF7F4EE.toInt(), dp(14).toFloat())

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "实验设置"
            asLabel(16f, 0xFF1C1B19.toInt())
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        dragHandle = TextView(context).apply {
            text = "拖动"
            asLabel(12f, 0xFF6E6A62.toInt())
            setPadding(dp(8), dp(6), dp(4), dp(6))
        }
        header.addView(dragHandle)
        addView(header)

        screenInfo = TextView(context).apply {
            text = "打开后会截一张屏，用来显示当前分辨率"
            asLabel(12f, 0xFF6E6A62.toInt())
            setPadding(0, dp(4), 0, dp(6))
        }
        addView(screenInfo)

        val scroll = ScrollView(context)
        val form = LinearLayout(context).apply { orientation = VERTICAL }
        val scrollHeight = (panelHeightPx - dp(188)).coerceAtLeast(dp(180))
        logNormalButton = RadioButton(context).apply {
            id = View.generateViewId()
            text = "对数正态"
            setTextColor(0xFF1C1B19.toInt())
        }
        uniformButton = RadioButton(context).apply {
            id = View.generateViewId()
            text = "均匀分布"
            setTextColor(0xFF1C1B19.toInt())
        }
        val distribution = RadioGroup(context).apply {
            orientation = HORIZONTAL
            addView(logNormalButton)
            addView(uniformButton)
        }
        if (config.distribution == Distribution.UNIFORM) uniformButton.isChecked = true else logNormalButton.isChecked = true
        seedField = context.field(signed = true).apply { setText(config.seed.toString()) }
        scanField = context.field().apply { setText(config.scanIntervalMs.toString()) }
        form.addView(section("整体"))
        form.addView(distribution)
        form.addView(context.row("种子" to seedField, "间隔" to scanField))
        form.addView(note("间隔是两轮扫描之间的毫秒。均匀分布使用每个分支的下限和上限；对数正态使用中位、离散和下限。"))

        addFunction(form, "一号功能", listOf(BranchId.F1B1, BranchId.F1B2), config)
        addFunction(form, "二号功能", listOf(BranchId.F2B1, BranchId.F2B2), config)
        form.addView(note("记录文件\n$logPath"))
        scroll.addView(form)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, scrollHeight))

        errorView = TextView(context).apply {
            text = ""
            asLabel(12f, 0xFF8A3B32.toInt())
            setPadding(0, dp(6), 0, dp(6))
        }
        addView(errorView)

        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        val saveButton = context.textButton("保存", 0xFF1C1B19.toInt(), 0xFFF7F4EE.toInt()).apply {
            setOnClickListener { saveConfig() }
        }
        val reset = context.textButton("恢复默认", 0xFFE4DFD6.toInt(), 0xFF1C1B19.toInt()).apply {
            setOnClickListener { restore() }
        }
        val close = context.textButton("关闭", 0xFFE4DFD6.toInt(), 0xFF1C1B19.toInt()).apply {
            setOnClickListener { onClose() }
        }
        listOf(saveButton, reset, close).forEach { button ->
            actions.addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }
        addView(actions)
    }

    fun setScreenInfo(text: String) {
        screenInfo.text = text
    }

    fun showMessage(text: String) {
        errorView.text = text
    }

    fun editor(id: BranchId): BranchEditor? = editors[id]

    private fun saveConfig() {
        try {
            val branches = BranchId.entries.associateWith { id -> editors.getValue(id).read() }
            val next = ExperimentConfig(
                branches = branches,
                distribution = if (uniformButton.isChecked) Distribution.UNIFORM else Distribution.LOG_NORMAL,
                seed = seedField.text?.toString()?.trim()?.toLongOrNull()
                    ?: throw IllegalArgumentException("种子不是整数"),
                scanIntervalMs = scanField.text?.toString()?.trim()?.toLongOrNull()
                    ?: throw IllegalArgumentException("扫描间隔不是整数"),
            ).sanitized()
            onSave(next)
            showMessage("已保存。下一轮扫描用这组参数。种子在停止后再开始时重新生效。")
        } catch (error: IllegalArgumentException) {
            showMessage(error.message ?: "有参数不是数字")
        }
    }

    private fun restore() {
        val defaults = ExperimentConfig.default()
        logNormalButton.isChecked = true
        seedField.setText(defaults.seed.toString())
        scanField.setText(defaults.scanIntervalMs.toString())
        for (id in BranchId.entries) {
            editors.getValue(id).fill(defaults.branch(id))
        }
        showMessage("已填回默认值，还没保存。")
    }

    private fun addFunction(form: LinearLayout, title: String, ids: List<BranchId>, config: ExperimentConfig) {
        form.addView(section(title))
        for (id in ids) {
            val editor = BranchEditor(context, id, config.branch(id), onSample)
            editors[id] = editor
            form.addView(
                editor.view,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                },
            )
        }
    }

    private fun section(title: String): TextView {
        return TextView(context).apply {
            text = title
            asLabel(15f, 0xFF1C1B19.toInt())
            setPadding(0, dp(10), 0, dp(4))
        }
    }

    private fun note(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            asLabel(12f, 0xFF6E6A62.toInt())
            setPadding(0, dp(2), 0, dp(4))
        }
    }
}

class BranchEditor(
    context: Context,
    private val id: BranchId,
    initial: BranchConfig,
    private val onSample: (BranchId, Int, Int) -> Unit,
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(0xFFEFEBE3.toInt(), context.dp(10).toFloat())
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
    }
    private val enabledBox = android.widget.CheckBox(context).apply {
        text = id.branchName
        setTextColor(0xFF1C1B19.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }
    private val regionX = context.field()
    private val regionY = context.field()
    private val regionW = context.field()
    private val regionH = context.field()
    private val colorField = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(0xFF1C1B19.toInt())
        setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
        background = rounded(0xFFFFFFFF.toInt(), context.dp(6).toFloat(), 0xFFD5D0C8.toInt(), context.dp(1))
    }
    private val tolerance = context.field()
    private val matchPercent = context.field()
    private val aimX = context.field()
    private val aimY = context.field()
    private val median = context.field()
    private val dispersion = context.field(decimal = true)
    private val floorMs = context.field()
    private val uniformMax = context.field()
    private val sigmaX = context.field(decimal = true)
    private val sigmaY = context.field(decimal = true)
    private val swatch = View(context)
    private val hint = TextView(context).apply {
        asLabel(12f, 0xFF6E6A62.toInt())
    }

    init {
        view.addView(enabledBox)
        view.addView(context.row("区域X" to regionX, "区域Y" to regionY))
        view.addView(context.row("宽" to regionW, "高" to regionH))
        val colorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        colorRow.addView(TextView(context).apply {
            text = "颜色"
            asLabel(12f, 0xFF5E5A54.toInt())
        }, LinearLayout.LayoutParams(context.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT))
        colorRow.addView(colorField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        colorRow.addView(swatch, LinearLayout.LayoutParams(context.dp(28), context.dp(28)).apply { marginStart = context.dp(8) })
        view.addView(colorRow)
        view.addView(context.row("容差" to tolerance, "命中%" to matchPercent))
        view.addView(context.row("瞄准X" to aimX, "瞄准Y" to aimY))
        val sample = context.textButton("采样瞄准点颜色", 0xFF1C1B19.toInt(), 0xFFF7F4EE.toInt()).apply {
            setOnClickListener {
                val x = aimX.text?.toString()?.trim()?.toIntOrNull()
                val y = aimY.text?.toString()?.trim()?.toIntOrNull()
                if (x == null || y == null) {
                    setHint("瞄准坐标不是整数")
                    return@setOnClickListener
                }
                setHint("采样中")
                onSample(id, x, y)
            }
        }
        view.addView(sample, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(4)
        })
        view.addView(hint)
        view.addView(context.row("中位" to median, "离散" to dispersion))
        view.addView(context.row("下限" to floorMs, "上限" to uniformMax))
        view.addView(context.row("横向σ" to sigmaX, "纵向σ" to sigmaY))
        val gap = View(context)
        view.addView(gap, LinearLayout.LayoutParams(1, context.dp(8)))
        fill(initial)
    }

    fun fill(branch: BranchConfig) {
        enabledBox.isChecked = branch.enabled
        regionX.setText(branch.regionX.toString())
        regionY.setText(branch.regionY.toString())
        regionW.setText(branch.regionW.toString())
        regionH.setText(branch.regionH.toString())
        setColor(branch.color)
        tolerance.setText(branch.tolerance.toString())
        matchPercent.setText(branch.matchPercent.toString())
        aimX.setText(branch.aimX.toString())
        aimY.setText(branch.aimY.toString())
        median.setText(branch.medianMs.toString())
        dispersion.setText(branch.dispersion.toString())
        floorMs.setText(branch.floorMs.toString())
        uniformMax.setText(branch.uniformMaxMs.toString())
        sigmaX.setText(branch.sigmaX.toString())
        sigmaY.setText(branch.sigmaY.toString())
    }

    fun setColor(rgb: Int) {
        colorField.setText(String.format(Locale.US, "%06X", rgb and 0xFFFFFF))
        swatch.setBackgroundColor(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    }

    fun setHint(text: String) {
        hint.text = text
    }

    fun read(): BranchConfig {
        val title = "${id.functionName}${id.branchName}"
        val color = parseColor(colorField.text?.toString().orEmpty())
            ?: throw IllegalArgumentException("$title 的颜色要写成 RRGGBB")
        return BranchConfig(
            enabled = enabledBox.isChecked,
            regionX = regionX.int(title, "区域X"),
            regionY = regionY.int(title, "区域Y"),
            regionW = regionW.int(title, "宽"),
            regionH = regionH.int(title, "高"),
            color = color,
            tolerance = tolerance.int(title, "容差"),
            matchPercent = matchPercent.int(title, "命中比例"),
            aimX = aimX.int(title, "瞄准X"),
            aimY = aimY.int(title, "瞄准Y"),
            medianMs = median.long(title, "中位反应时"),
            dispersion = dispersion.double(title, "离散"),
            floorMs = floorMs.long(title, "下限"),
            uniformMaxMs = uniformMax.long(title, "上限"),
            sigmaX = sigmaX.double(title, "横向抖动"),
            sigmaY = sigmaY.double(title, "纵向抖动"),
        )
    }

    private fun EditText.int(title: String, label: String): Int {
        return text?.toString()?.trim()?.toIntOrNull() ?: throw IllegalArgumentException("$title 的$label 不是整数")
    }

    private fun EditText.long(title: String, label: String): Long {
        return text?.toString()?.trim()?.toLongOrNull() ?: throw IllegalArgumentException("$title 的$label 不是整数")
    }

    private fun EditText.double(title: String, label: String): Double {
        return text?.toString()?.trim()?.replace('，', '.')?.replace(',', '.')?.toDoubleOrNull()
            ?: throw IllegalArgumentException("$title 的$label 不是数字")
    }
}
