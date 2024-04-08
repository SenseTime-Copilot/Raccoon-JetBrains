package com.sensetime.sensecode.jetbrains.raccoon.completions.preview

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
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ShowNextInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ShowPreviousInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render.CompletionInlays
import com.sensetime.sensecode.jetbrains.raccoon.resources.RaccoonBundle
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonActionUtils
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonExceptions
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class CompletionPreviewTooltip(
    parent: Disposable,
    private var indexGetter: (() -> Int)?,
    private var countGetter: (() -> Int?)?,
    private var editor: Editor?,
    private var completionInlays: CompletionInlays?
) : Disposable, EditorMouseMotionListener {
    init {
        editor?.addEditorMouseMotionListener(this, parent)
        Disposer.register(parent, this)
    }

    override fun dispose() {
        editor = null
        indexGetter = null
        countGetter = null
        completionInlays = null
    }

    private var lastPoint: Point? = null
    fun updateTooltip(point: Point? = null) {
        editor?.let { tmpEditor ->
            (point ?: lastPoint)?.let { tmpPoint ->
                lastPoint = tmpPoint
                indexGetter?.invoke()?.let { index ->
                    countGetter?.invoke()?.let { count ->
                        RaccoonExceptions.resultOf {
                            HintManagerImpl.getInstanceImpl().showEditorHint(
                                LightweightHint(createTooltipComponent(index, count)),
                                tmpEditor, tmpPoint,
                                HintManager.HIDE_BY_ANY_KEY or HintManager.UPDATE_BY_SCROLLING,
                                0,
                                false
                            )
                        }
                    }
                }
            }
        }
    }

    override fun mouseMoved(e: EditorMouseEvent) {
        val tmpEditor = editor
        if ((null == tmpEditor) || e.area !== EditorMouseEventArea.EDITING_AREA) {
            return
        }

        val mouseEvent = e.mouseEvent
        val point = mouseEvent.point
        if (true == completionInlays?.contains(point)) {
            updateTooltip(
                SwingUtilities.convertPoint(
                    mouseEvent.source as Component,
                    point,
                    tmpEditor.component.rootPane.layeredPane
                )
            )
        }
    }

    companion object {
        @JvmStatic
        private fun createTooltipComponent(index: Int, count: Int): JComponent =
            HintUtil.createInformationComponent().also {
                it.border = JBUI.Borders.empty()
                SimpleColoredText(
                    "< ${index + 1}/$count >  " + RaccoonBundle.message(
                        "completions.inline.completion.inlays.tooltip.multiple",
                        RaccoonActionUtils.getShortcutText(ShowPreviousInlineCompletionAction::class),
                        RaccoonActionUtils.getShortcutText(ShowNextInlineCompletionAction::class)
                    ), SimpleTextAttributes.REGULAR_ATTRIBUTES
                ).appendToComponent(it)
                it.isOpaque = true
            }
    }
}