package com.taplab.studio

import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--check") {
        println("taplab-studio ok")
        return
    }
    SwingUtilities.invokeLater {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        StudioFrame().isVisible = true
    }
}
