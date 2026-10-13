package com.taplab.studio

import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.ModeBundle
import com.taplab.experiment.model.ModePackage
import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.model.Step
import com.taplab.experiment.model.sanitized
import com.taplab.experiment.vision.RgbaImage
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.io.File
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.filechooser.FileNameExtensionFilter

class StudioFrame : JFrame("行为模式配置") {
    private val library = StudioLibrary.inHomeDirectory()
    private val modeModel = DefaultListModel<BehaviorMode>()
    private val modeList = JList(modeModel)
    private val canvas = ScreenshotPanel()
    private val nameField = JTextField()
    private val grabList = JPanel()
    private val stepList = JPanel()
    private val status = JLabel("新建一个行为模式，打开截图，再抓取。")
    private var session: Session? = null
    private var updatingList = false
    private var pickClickId: String? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        canvas.onPick = { x, y -> applyPickedClick(x, y) }
        grabList.layout = BoxLayout(grabList, BoxLayout.Y_AXIS)
        stepList.layout = BoxLayout(stepList, BoxLayout.Y_AXIS)
        nameField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                commitName()
            }
        })
        modeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        modeList.addListSelectionListener { event ->
            if (event.valueIsAdjusting || updatingList) return@addListSelectionListener
            val selected = modeList.selectedValue ?: return@addListSelectionListener
            if (selected.id == session?.mode?.id) return@addListSelectionListener
            commitName()
            open(selected.id)
        }
        layout = BorderLayout(8, 8)
        add(toolbar(), BorderLayout.NORTH)
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, modePane(), editor()).apply {
            resizeWeight = 0.18
            dividerLocation = 220
        }, BorderLayout.CENTER)
        add(status.apply { border = BorderFactory.createEmptyBorder(4, 12, 8, 12) }, BorderLayout.SOUTH)
        size = Dimension(1180, 780)
        setLocationRelativeTo(null)
        refreshList(null)
        if (modeModel.isEmpty) showEmpty() else open(modeModel.getElementAt(0).id)
    }

    private fun toolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("新建").apply { addActionListener { createMode() } })
            add(JButton("打开导出文件夹").apply { addActionListener { importFolder() } })
            add(JButton("导出").apply { addActionListener { exportCurrent() } })
            add(JButton("删除").apply { addActionListener { deleteCurrent() } })
        }
    }

    private fun modePane(): JComponentScroll {
        return JComponentScroll(modeList)
    }

    private fun editor(): JSplitPane {
        val tools = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("打开图片").apply { addActionListener { openImage() } })
            add(JButton("新建抓取").apply { addActionListener { beginGrab() } })
            add(JButton("确定抓取").apply { addActionListener { confirmGrab() } })
            add(JButton("取消抓取").apply { addActionListener { canvas.cancelGrab(); status.text = "已取消抓取" } })
        }
        val picture = JPanel(BorderLayout()).apply {
            add(tools, BorderLayout.NORTH)
            add(canvas, BorderLayout.CENTER)
            preferredSize = Dimension(460, 680)
        }
        val form = JPanel()
        form.layout = BoxLayout(form, BoxLayout.Y_AXIS)
        form.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        form.add(JLabel("名称"))
        form.add(nameField)
        form.add(JLabel("抓取"))
        form.add(grabList)
        form.add(JLabel("循环。对比用抓取的图片和手机上同一块比较，再走匹配或不匹配。"))
        form.add(stepList)
        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, picture, JScrollPane(form)).apply {
            resizeWeight = 0.55
        }
    }

    private fun createMode() {
        commitName()
        val mode = BehaviorMode(id = UUID.randomUUID().toString(), name = "未命名")
        library.save(ModeBundle(mode, emptyMap()), null)
        refreshList(mode.id)
        open(mode.id)
    }

    private fun deleteCurrent() {
        val current = session ?: return
        val ok = JOptionPane.showConfirmDialog(this, "删除「${current.mode.name}」？", "删除", JOptionPane.OK_CANCEL_OPTION)
        if (ok != JOptionPane.OK_OPTION) return
        library.delete(current.mode.id)
        session = null
        refreshList(null)
        if (modeModel.isEmpty) showEmpty() else open(modeModel.getElementAt(0).id)
    }

    private fun importFolder() {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val bundle = try {
            ModePackage.read(chooser.selectedFile)
        } catch (error: Exception) {
            JOptionPane.showMessageDialog(this, error.message ?: "打不开这个文件夹")
            return
        }
        library.save(bundle, null)
        refreshList(bundle.mode.id)
        open(bundle.mode.id)
        status.text = "已打开「${bundle.mode.name}」"
    }

    private fun exportCurrent() {
        commitName()
        val current = session ?: return
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val folder = library.export(chooser.selectedFile, ModeBundle(current.mode, current.templates.toMap()))
        status.text = "已导出到 ${folder.absolutePath}"
        JOptionPane.showMessageDialog(
            this,
            "已导出到\n${folder.absolutePath}\n\n把这个文件夹拷进手机的 inbox 目录，在小熊的设置里点导入。",
        )
    }

    private fun openImage() {
        val current = session ?: return
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "bmp", "gif")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val rgba = try {
            ScreenImage.fromFile(chooser.selectedFile)
        } catch (error: Exception) {
            JOptionPane.showMessageDialog(this, error.message ?: "打不开这张图片")
            return
        }
        current.screen = rgba
        canvas.setScreen(rgba)
        saveSession()
        status.text = "图片已按 720×1080 铺好。点新建抓取，再在画面上框一块。"
    }

    private fun beginGrab() {
        if (session?.screen == null) {
            status.text = "先打开一张截图"
            return
        }
        pickClickId = null
        canvas.beginGrab()
        status.text = "在画面上点中心，拖动调整正方形，然后点确定抓取"
    }

    private fun confirmGrab() {
        val current = session ?: return
        val screen = current.screen
        val square = canvas.currentSquare()
        if (screen == null || square == null) {
            status.text = "先在画面上框出一块"
            return
        }
        val image = RgbaImage.crop(ScreenSpec.WIDTH, ScreenSpec.HEIGHT, screen, square)
        if (image == null) {
            status.text = "这块裁不出来"
            return
        }
        val defaultName = "抓取${current.mode.grabs.size + 1}"
        val name = JOptionPane.showInputDialog(this, "抓取名称", defaultName) ?: return
        val grab = GrabSpec(
            id = newId(),
            name = name,
            centerX = square.centerX,
            centerY = square.centerY,
            side = square.side,
        )
        current.templates[grab.id] = image
        current.mode = current.mode.copy(grabs = current.mode.grabs + grab)
        canvas.cancelGrab()
        saveSession()
        renderEditor()
        status.text = "已保存${grab.sanitized().name}，中心 ${grab.centerX}, ${grab.centerY}，边长 ${grab.side}"
    }

    private fun open(id: String) {
        val (bundle, screen) = try {
            library.load(id)
        } catch (error: Exception) {
            status.text = error.message ?: "打不开这个模式"
            return
        }
        session = Session(bundle.mode, bundle.templates.toMutableMap(), screen)
        pickClickId = null
        canvas.cancelGrab()
        nameField.isEnabled = true
        nameField.text = bundle.mode.name
        canvas.setScreen(screen)
        renderEditor()
        updatingList = true
        refreshList(id)
        updatingList = false
    }

    private fun showEmpty() {
        session = null
        nameField.text = ""
        nameField.isEnabled = false
        canvas.setScreen(null)
        canvas.setOverlays(emptyList(), emptyList())
        grabList.removeAll()
        stepList.removeAll()
        grabList.revalidate()
        stepList.revalidate()
    }

    private fun commitName() {
        val current = session ?: return
        val renamed = current.mode.copy(name = nameField.text.orEmpty()).sanitized()
        if (renamed.name == current.mode.name) return
        current.mode = renamed
        nameField.text = renamed.name
        saveSession()
        updatingList = true
        refreshList(renamed.id)
        updatingList = false
    }

    private fun saveSession() {
        val current = session ?: return
        current.mode = current.mode.sanitized()
        library.save(ModeBundle(current.mode, current.templates.toMap()), current.screen)
    }

    private fun renderEditor() {
        val current = session ?: return
        renderGrabs(current)
        renderSteps(stepList, current.mode.steps, depth = 0) { updated ->
            current.mode = current.mode.copy(steps = updated)
            saveSession()
            renderEditor()
        }
        canvas.setOverlays(current.mode.grabs, collectClicks(current.mode.steps))
        grabList.revalidate()
        stepList.revalidate()
        repaint()
    }

    private fun renderGrabs(current: Session) {
        grabList.removeAll()
        if (current.mode.grabs.isEmpty()) grabList.add(JLabel("还没有抓取"))
        current.mode.grabs.forEach { grab ->
            val row = JPanel(FlowLayout(FlowLayout.LEFT))
            current.templates[grab.id]?.let { image ->
                row.add(JLabel(ImageIcon(ScreenImage.preview(image).getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH))))
            }
            row.add(JLabel("${grab.name}  ${grab.centerX}, ${grab.centerY}  边长 ${grab.side}"))
            row.add(JButton("改名").apply {
                addActionListener {
                    val name = JOptionPane.showInputDialog(this@StudioFrame, "名称", grab.name) ?: return@addActionListener
                    current.mode = current.mode.copy(grabs = current.mode.grabs.map { if (it.id == grab.id) it.copy(name = name) else it })
                    saveSession()
                    renderEditor()
                }
            })
            row.add(JButton("删除").apply {
                addActionListener {
                    current.templates.remove(grab.id)
                    current.mode = current.mode.copy(grabs = current.mode.grabs.filter { it.id != grab.id })
                    saveSession()
                    renderEditor()
                }
            })
            grabList.add(row)
        }
    }

    private fun renderSteps(
        parent: JPanel,
        steps: List<Step>,
        depth: Int,
        onChange: (List<Step>) -> Unit,
    ) {
        parent.removeAll()
        steps.forEachIndexed { index, step ->
            parent.add(stepCard(step, index, steps, depth, onChange))
        }
        val add = JPanel(FlowLayout(FlowLayout.LEFT))
        add.add(JButton("加点击").apply { addActionListener { onChange(steps + Step.Click(newId(), 360, 540)) } })
        add.add(JButton("加延时").apply { addActionListener { onChange(steps + Step.Delay(newId(), 200)) } })
        if (depth < 2) {
            add.add(JButton("加对比").apply {
                addActionListener {
                    val grabId = session?.mode?.grabs?.firstOrNull()?.id.orEmpty()
                    onChange(steps + Step.Compare(id = newId(), grabId = grabId))
                }
            })
        }
        parent.add(add)
    }

    private fun stepCard(
        step: Step,
        index: Int,
        steps: List<Step>,
        depth: Int,
        onChange: (List<Step>) -> Unit,
    ): JPanel {
        val card = JPanel()
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, depth * 12, 4, 0),
            BorderFactory.createLineBorder(java.awt.Color(0xD5, 0xD0, 0xC8)),
        )
        val row = JPanel(FlowLayout(FlowLayout.LEFT))
        row.add(JLabel(summary(step)))
        row.add(JButton("改").apply { addActionListener { editStep(step) { updated -> onChange(replaceAt(steps, index, updated)) } } })
        if (step is Step.Click) {
            row.add(JButton("选点").apply {
                addActionListener {
                    if (session?.screen == null) {
                        status.text = "先打开一张截图，再选点击位置"
                        return@addActionListener
                    }
                    pickClickId = step.id
                    canvas.beginPick()
                    status.text = "在画面上点一下，作为这个点击的位置"
                }
            })
        }
        row.add(JButton("上").apply { addActionListener { if (index > 0) onChange(move(steps, index, index - 1)) } })
        row.add(JButton("下").apply { addActionListener { if (index < steps.lastIndex) onChange(move(steps, index, index + 1)) } })
        row.add(JButton("删").apply { addActionListener { onChange(steps.filterIndexed { item, _ -> item != index }) } })
        card.add(row)
        if (step is Step.Compare) {
            card.add(JLabel("匹配时"))
            val matchBox = JPanel()
            matchBox.layout = BoxLayout(matchBox, BoxLayout.Y_AXIS)
            card.add(matchBox)
            renderSteps(matchBox, step.whenMatch, depth + 1) { updated ->
                onChange(replaceAt(steps, index, step.copy(whenMatch = updated)))
            }
            card.add(JLabel("不匹配时"))
            val missBox = JPanel()
            missBox.layout = BoxLayout(missBox, BoxLayout.Y_AXIS)
            card.add(missBox)
            renderSteps(missBox, step.whenMiss, depth + 1) { updated ->
                onChange(replaceAt(steps, index, step.copy(whenMiss = updated)))
            }
        }
        return card
    }

    private fun editStep(step: Step, onUpdated: (Step) -> Unit) {
        when (step) {
            is Step.Click -> {
                val values = askForm("点击", listOf("X", "Y", "横向抖动", "纵向抖动"), listOf(step.x.toString(), step.y.toString(), step.sigmaX.toString(), step.sigmaY.toString())) ?: return
                val x = values[0].toIntOrNull() ?: return
                val y = values[1].toIntOrNull() ?: return
                val sigmaX = values[2].toDoubleOrNull() ?: return
                val sigmaY = values[3].toDoubleOrNull() ?: return
                onUpdated(step.copy(x = x, y = y, sigmaX = sigmaX, sigmaY = sigmaY))
            }
            is Step.Delay -> {
                val values = askForm("延时", listOf("毫秒"), listOf(step.millis.toString())) ?: return
                val millis = values[0].toLongOrNull() ?: return
                onUpdated(step.copy(millis = millis))
            }
            is Step.Compare -> editCompare(step, onUpdated)
        }
    }

    private fun editCompare(step: Step.Compare, onUpdated: (Step) -> Unit) {
        val grabs = session?.mode?.grabs.orEmpty()
        if (grabs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "先做一次抓取")
            return
        }
        val names = grabs.map { it.name }.toTypedArray()
        val which = JOptionPane.showOptionDialog(this, "用哪一块抓取", "对比", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, names, names.first())
        if (which !in grabs.indices) return
        val values = askForm("对比", listOf("容差", "命中%"), listOf(step.tolerance.toString(), step.matchPercent.toString())) ?: return
        val tolerance = values[0].toIntOrNull() ?: return
        val percent = values[1].toIntOrNull() ?: return
        onUpdated(step.copy(grabId = grabs[which].id, tolerance = tolerance, matchPercent = percent))
    }

    private fun applyPickedClick(x: Int, y: Int) {
        val current = session ?: return
        val id = pickClickId ?: return
        pickClickId = null
        current.mode = current.mode.copy(steps = current.mode.steps.mapStep(id) { step ->
            if (step is Step.Click) step.copy(x = x, y = y) else step
        })
        saveSession()
        renderEditor()
        status.text = "点击位置 $x, $y"
    }

    private fun askForm(title: String, labels: List<String>, initial: List<String>): List<String>? {
        val fields = initial.map { JTextField(it, 12) }
        val form = JPanel(GridLayout(0, 2, 8, 6))
        labels.zip(fields).forEach { (label, field) ->
            form.add(JLabel(label))
            form.add(field)
        }
        val ok = JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (ok != JOptionPane.OK_OPTION) return null
        return fields.map { it.text.trim() }
    }

    private fun summary(step: Step): String {
        return when (step) {
            is Step.Click -> "点击  ${step.x}, ${step.y}"
            is Step.Delay -> "延时  ${step.millis} ms"
            is Step.Compare -> {
                val name = session?.mode?.grabs?.find { it.id == step.grabId }?.name ?: "未选择"
                "对比  $name  命中 ${step.matchPercent}%"
            }
        }
    }

    private fun refreshList(selectId: String?) {
        val modes = library.list()
        updatingList = true
        modeModel.clear()
        modes.forEach { modeModel.addElement(it) }
        val index = modes.indexOfFirst { it.id == selectId }
        if (index >= 0) modeList.selectedIndex = index
        updatingList = false
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private class Session(
        var mode: BehaviorMode,
        val templates: MutableMap<String, com.taplab.experiment.vision.TemplateImage>,
        var screen: ByteArray?,
    )
}

private class JComponentScroll(view: javax.swing.JComponent) : JScrollPane(view)

private fun replaceAt(steps: List<Step>, index: Int, updated: Step): List<Step> {
    return steps.mapIndexed { item, step -> if (item == index) updated else step }
}

private fun move(steps: List<Step>, from: Int, to: Int): List<Step> {
    val copy = steps.toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}

private fun collectClicks(steps: List<Step>): List<Step.Click> {
    val found = mutableListOf<Step.Click>()
    fun walk(list: List<Step>) {
        list.forEach { step ->
            when (step) {
                is Step.Click -> found += step
                is Step.Delay -> Unit
                is Step.Compare -> {
                    walk(step.whenMatch)
                    walk(step.whenMiss)
                }
            }
        }
    }
    walk(steps)
    return found
}

private fun List<Step>.mapStep(id: String, transform: (Step) -> Step): List<Step> {
    return map { step ->
        when {
            step.id == id -> transform(step)
            step is Step.Compare -> step.copy(
                whenMatch = step.whenMatch.mapStep(id, transform),
                whenMiss = step.whenMiss.mapStep(id, transform),
            )
            else -> step
        }
    }
}
