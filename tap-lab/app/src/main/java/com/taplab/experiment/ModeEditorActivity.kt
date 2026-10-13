package com.taplab.experiment

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.Step
import com.taplab.experiment.store.ModeStore
import com.taplab.experiment.ui.asLabel
import com.taplab.experiment.ui.dp
import com.taplab.experiment.ui.field
import com.taplab.experiment.ui.rounded
import com.taplab.experiment.ui.textButton
import com.taplab.experiment.vision.TemplateImage
import java.util.UUID

class ModeEditorActivity : Activity() {
    private lateinit var store: ModeStore
    private lateinit var mode: BehaviorMode
    private lateinit var nameField: EditText
    private lateinit var grabList: LinearLayout
    private lateinit var stepList: LinearLayout
    private var reloadOnResume = false
    private var suppressPauseSave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ModeStore(this)
        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { store.load(it) }
        if (loaded == null) {
            finish()
            return
        }
        mode = loaded
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFF4F1EA.toInt())
        }
        root.addView(TextView(this).apply {
            text = "编辑行为模式"
            asLabel(20f, 0xFF1C1B19.toInt())
        })
        nameField = field(signed = false).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(mode.name)
        }
        root.addView(labeled("名称", nameField))
        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(section("抓取"))
        grabList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(grabList)
        form.addView(textButton("新建抓取", 0xFF1C1B19.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { beginGrab() }
        })
        form.addView(section("循环"))
        form.addView(TextView(this).apply {
            text = "循环会按顺序执行。对比用抓取里的图片和当前屏幕上同一块正方形比较，再走匹配或不匹配。"
            asLabel(12f, 0xFF5E5A54.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        stepList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(stepList)
        scroll.addView(form)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(textButton("保存", 0xFF1C1B19.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener {
                save()
                finish()
            }
        })
        setContentView(root)
        render()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) setIntent(intent)
        reloadOnResume = true
    }

    override fun onResume() {
        super.onResume()
        suppressPauseSave = false
        if (reloadOnResume) {
            reloadOnResume = false
            store.load(mode.id)?.let { mode = it }
            nameField.setText(mode.name)
            render()
        }
    }

    override fun onPause() {
        if (!suppressPauseSave && this::mode.isInitialized) save()
        super.onPause()
    }

    private fun beginGrab() {
        save()
        suppressPauseSave = true
        reloadOnResume = true
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_GRAB).putExtra(EXTRA_ID, mode.id))
        moveTaskToBack(true)
    }

    private fun save() {
        mode = mode.copy(name = nameField.text?.toString().orEmpty()).sanitized()
        store.save(mode)
    }

    private fun render() {
        grabList.removeAllViews()
        if (mode.grabs.isEmpty()) {
            grabList.addView(note("还没有抓取"))
        }
        mode.grabs.forEach { grab -> grabList.addView(grabRow(grab)) }
        stepList.removeAllViews()
        renderSteps(stepList, mode.steps, depth = 0) { updated ->
            mode = mode.copy(steps = updated)
            render()
        }
    }

    private fun grabRow(grab: GrabSpec): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFFEFEBE3.toInt(), dp(8).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val preview = ImageView(this@ModeEditorActivity).apply {
                val image = store.loadTemplate(grab.id)
                setImageBitmap(image?.let { bitmapOf(it) })
            }
            addView(preview, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@ModeEditorActivity).apply {
                text = "${grab.name}\n中心 ${grab.centerX}, ${grab.centerY}  边长 ${grab.side}"
                asLabel(13f, 0xFF1C1B19.toInt())
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(mini("改名") {
                rename(grab.name) { name ->
                    mode = mode.copy(grabs = mode.grabs.map { if (it.id == grab.id) it.copy(name = name) else it })
                    render()
                }
            })
            addView(mini("删") {
                mode = mode.copy(grabs = mode.grabs.filter { it.id != grab.id })
                render()
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }
    }

    private fun renderSteps(
        parent: LinearLayout,
        steps: List<Step>,
        depth: Int,
        onChange: (List<Step>) -> Unit,
    ) {
        steps.forEachIndexed { index, step ->
            parent.addView(stepCard(step, index, steps, depth, onChange))
        }
        val add = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add.addView(mini("加点击") { onChange(steps + Step.Click(newId(), 360, 540)) })
        add.addView(mini("加延时") { onChange(steps + Step.Delay(newId(), 200)) })
        if (depth < 2) {
            add.addView(mini("加对比") {
                val grabId = mode.grabs.firstOrNull()?.id.orEmpty()
                onChange(steps + Step.Compare(id = newId(), grabId = grabId))
            })
        }
        parent.addView(add)
    }

    private fun stepCard(
        step: Step,
        index: Int,
        steps: List<Step>,
        depth: Int,
        onChange: (List<Step>) -> Unit,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFFEFEBE3.toInt(), dp(8).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(TextView(this@ModeEditorActivity).apply {
                text = summary(step)
                asLabel(14f, 0xFF1C1B19.toInt())
            })
            val actions = LinearLayout(this@ModeEditorActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(mini("改") { editStep(step) { updated -> replace(steps, index, updated, onChange) } })
            actions.addView(mini("上") { if (index > 0) onChange(steps.move(index, index - 1)) })
            actions.addView(mini("下") { if (index < steps.lastIndex) onChange(steps.move(index, index + 1)) })
            actions.addView(mini("删") { onChange(steps.filterIndexed { i, _ -> i != index }) })
            addView(actions)
            if (step is Step.Compare) {
                addView(note("匹配时"))
                val matchBox = LinearLayout(this@ModeEditorActivity).apply { orientation = LinearLayout.VERTICAL }
                renderSteps(matchBox, step.whenMatch, depth + 1) { updated ->
                    replace(steps, index, step.copy(whenMatch = updated), onChange)
                }
                addView(matchBox)
                addView(note("不匹配时"))
                val missBox = LinearLayout(this@ModeEditorActivity).apply { orientation = LinearLayout.VERTICAL }
                renderSteps(missBox, step.whenMiss, depth + 1) { updated ->
                    replace(steps, index, step.copy(whenMiss = updated), onChange)
                }
                addView(missBox)
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }
    }

    private fun editStep(step: Step, onUpdated: (Step) -> Unit) {
        when (step) {
            is Step.Click -> numberDialog("点击", arrayOf("X", "Y", "横向抖动", "纵向抖动"), arrayOf(step.x.toString(), step.y.toString(), step.sigmaX.toString(), step.sigmaY.toString())) { values ->
                val x = values[0].toIntOrNull() ?: return@numberDialog
                val y = values[1].toIntOrNull() ?: return@numberDialog
                val sx = values[2].toDoubleOrNull() ?: return@numberDialog
                val sy = values[3].toDoubleOrNull() ?: return@numberDialog
                onUpdated(step.copy(x = x, y = y, sigmaX = sx, sigmaY = sy))
            }
            is Step.Delay -> numberDialog("延时", arrayOf("毫秒"), arrayOf(step.millis.toString())) { values ->
                val millis = values[0].toLongOrNull() ?: return@numberDialog
                onUpdated(step.copy(millis = millis))
            }
            is Step.Compare -> {
                val names = mode.grabs.map { it.name.ifBlank { it.id } }.ifEmpty { listOf("还没有抓取") }
                val ids = mode.grabs.map { it.id }
                AlertDialog.Builder(this)
                    .setTitle("选择抓取")
                    .setItems(names.toTypedArray()) { _, which ->
                        if (ids.isEmpty()) return@setItems
                        numberDialog("对比", arrayOf("容差", "命中%"), arrayOf(step.tolerance.toString(), step.matchPercent.toString())) { values ->
                            val tolerance = values[0].toIntOrNull() ?: return@numberDialog
                            val percent = values[1].toIntOrNull() ?: return@numberDialog
                            onUpdated(step.copy(grabId = ids[which], tolerance = tolerance, matchPercent = percent))
                        }
                    }
                    .show()
            }
        }
    }

    private fun numberDialog(title: String, labels: Array<String>, initial: Array<String>, onDone: (List<String>) -> Unit) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val fields = labels.mapIndexed { index, label ->
            form.addView(TextView(this).apply { text = label; asLabel(12f, 0xFF5E5A54.toInt()) })
            val field = field(decimal = label.contains("抖动")).apply { setText(initial[index]) }
            form.addView(field)
            field
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(form)
            .setPositiveButton("确定") { _, _ -> onDone(fields.map { it.text?.toString()?.trim().orEmpty() }) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun rename(initial: String, onDone: (String) -> Unit) {
        val field = field().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
        }
        AlertDialog.Builder(this)
            .setTitle("名称")
            .setView(field)
            .setPositiveButton("确定") { _, _ -> onDone(field.text?.toString().orEmpty()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun replace(steps: List<Step>, index: Int, updated: Step, onChange: (List<Step>) -> Unit) {
        onChange(steps.mapIndexed { i, step -> if (i == index) updated else step })
    }

    private fun summary(step: Step): String {
        return when (step) {
            is Step.Click -> "点击  ${step.x}, ${step.y}"
            is Step.Delay -> "延时  ${step.millis} ms"
            is Step.Compare -> {
                val name = mode.grabs.find { it.id == step.grabId }?.name ?: "未选择"
                "对比  $name  命中 ${step.matchPercent}%"
            }
        }
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        asLabel(16f, 0xFF1C1B19.toInt())
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        asLabel(12f, 0xFF5E5A54.toInt())
    }

    private fun labeled(label: String, field: EditText): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ModeEditorActivity).apply { text = label; asLabel(12f, 0xFF5E5A54.toInt()) })
            addView(field)
        }
    }

    private fun mini(label: String, action: () -> Unit) = textButton(label, 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
        setOnClickListener { action() }
        minimumWidth = 0
        minWidth = 0
        textSize = 12f
    }

    private fun bitmapOf(image: TemplateImage): Bitmap? {
        if (image.width <= 0 || image.height <= 0) return null
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val bytesPerPixel = if (image.format == 3) 3 else 4
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val index = (y * image.width + x) * bytesPerPixel
                if (index + 2 >= image.pixels.size) continue
                val red: Int
                val green: Int
                val blue: Int
                if (image.format == 5) {
                    blue = image.pixels[index].toInt() and 0xFF
                    green = image.pixels[index + 1].toInt() and 0xFF
                    red = image.pixels[index + 2].toInt() and 0xFF
                } else {
                    red = image.pixels[index].toInt() and 0xFF
                    green = image.pixels[index + 1].toInt() and 0xFF
                    blue = image.pixels[index + 2].toInt() and 0xFF
                }
                val alpha = if (bytesPerPixel == 4 && index + 3 < image.pixels.size) image.pixels[index + 3].toInt() and 0xFF else 255
                bitmap.setPixel(x, y, android.graphics.Color.argb(alpha, red, green, blue))
            }
        }
        return bitmap
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        const val EXTRA_ID = "modeId"
    }
}

private fun List<Step>.move(from: Int, to: Int): List<Step> {
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}
