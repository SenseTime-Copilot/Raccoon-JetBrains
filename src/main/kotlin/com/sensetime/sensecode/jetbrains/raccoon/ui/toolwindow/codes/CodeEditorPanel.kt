//package com.sensetime.sensecode.jetbrains.raccoon.ui.toolwindow.codes
//
//import com.intellij.openapi.Disposable
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.editor.EditorFactory
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.util.Disposer
//import com.intellij.util.ui.JBUI
//import com.sensetime.sensecode.jetbrains.raccoon.ui.common.RaccoonUIUtils
//import java.awt.BorderLayout
//import javax.swing.JPanel
//
//class CodeEditorPanel(
//    parent: Disposable,
//    project: Project?
//) : JPanel(BorderLayout()), Disposable {
//    private var editor: Editor? = null
//        set(value) {
//            field?.let { EditorFactory.getInstance().releaseEditor(it) }
//            field = value
//        }
//
//    init {
//        isOpaque = false
//        border = JBUI.Borders.empty(RaccoonUIUtils.DEFAULT_GAP_SIZE, 0)
//
//        editor = EditorFactory.getInstance().createEditor()
//        Disposer.register(parent, this)
//    }
//
//    override fun dispose() {
//        editor = null
//    }
//}