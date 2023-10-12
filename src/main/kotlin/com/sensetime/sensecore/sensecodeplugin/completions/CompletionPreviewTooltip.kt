package com.sensetime.sensecore.sensecodeplugin.completions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.sensetime.sensecore.sensecodeplugin.actions.Utils
import com.sensetime.sensecore.sensecodeplugin.actions.inline.ShowNextInlineCompletionAction
import com.sensetime.sensecore.sensecodeplugin.actions.inline.ShowPreviousInlineCompletionAction
import com.sensetime.sensecore.sensecodeplugin.completions.render.CompletionInlays
import com.sensetime.sensecore.sensecodeplugin.resources.SenseCodeBundle
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities

class CompletionPreviewTooltip(
    parent: Disposable,
    private var editor: Editor?,
    private var completionInlays: CompletionInlays?
) : Disposable, EditorMouseMotionListener {
    init {
        editor?.addEditorMouseMotionListener(this, parent)
        Disposer.register(parent, this)
    }

    override fun dispose() {
        editor = null
        completionInlays = null
    }

    override fun mouseMoved(e: EditorMouseEvent) {
        val tmpEditor = editor
        if ((null == tmpEditor) || e.area !== EditorMouseEventArea.EDITING_AREA) {
            return
        }

        val mouseEvent = e.mouseEvent
        val point = mouseEvent.point
        if (true == completionInlays?.contains(point)) {
            kotlin.runCatching {
                HintManagerImpl.getInstanceImpl().showEditorHint(
                    LightweightHint(createTooltipComponent()),
                    tmpEditor,
                    SwingUtilities.convertPoint(
                        mouseEvent.source as Component,
                        point,
                        tmpEditor.component.rootPane.layeredPane
                    ),
                    HintManager.HIDE_BY_ANY_KEY or HintManager.UPDATE_BY_SCROLLING,
                    0,
                    false
                )
            }
        }
    }

    companion object {
        @JvmStatic
        private fun createTooltipComponent(): JComponent = HintUtil.createInformationComponent().also {
            it.border = JBUI.Borders.empty()
            SimpleColoredText(
                SenseCodeBundle.message(
                    "completions.inline.completion.inlays.tooltip.multiple",
                    Utils.getShortcutText(ShowPreviousInlineCompletionAction::class),
                    Utils.getShortcutText(ShowNextInlineCompletionAction::class)
                ), SimpleTextAttributes.REGULAR_ATTRIBUTES
            ).appendToComponent(it)
            it.isOpaque = true
        }
    }
}