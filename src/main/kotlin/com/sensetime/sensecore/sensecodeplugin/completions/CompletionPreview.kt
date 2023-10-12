package com.sensetime.sensecore.sensecodeplugin.completions

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.sensetime.sensecore.sensecodeplugin.completions.render.CompletionInlays
import com.sensetime.sensecore.sensecodeplugin.utils.SenseCodeNotification

class CompletionPreview private constructor(
    tmpEditor: Editor,
    private val offset: Int
) : Disposable {
    private var currentIndex: Int = 0
        set(value) {
            field = if (value < 0) {
                completions?.lastIndex ?: 0
            } else if (value > (completions?.lastIndex ?: 0)) {
                0
            } else {
                value
            }
        }
    private var editor: Editor? = null
    private val inlays: CompletionInlays
    private var tooltip: CompletionPreviewTooltip? = null
    private var completions: List<String>? = null
        set(value) {
            value?.takeIf { (null == tooltip) && (it.size > 1) }?.let {
                tooltip = CompletionPreviewTooltip(this, editor, inlays)
            }
            field = value
        }
    private val currentCompletion: String?
        get() = completions?.getOrNull(currentIndex)

    var done: Boolean = false
        set(value) {
            field = value
            if (value && currentCompletion.isNullOrEmpty()) {
                SenseCodeNotification.popupNoCompletionSuggestionMessage(editor)
            }
        }

    init {
        editor = tmpEditor
        EditorUtil.disposeWithEditor(tmpEditor, this)
        inlays = CompletionInlays(this)
        tmpEditor.caretModel.addCaretListener(EditorCaretListener(), this)
        (tmpEditor as? EditorEx)?.addFocusListener(EditorFocusChangeListener(), this)
        tmpEditor.putUserData(COMPLETION_PREVIEW, this)
    }

    override fun dispose() {
        editor?.putUserData(COMPLETION_PREVIEW, null)
        editor = null
    }

    fun showError(message: String) {
        SenseCodeNotification.popupMessageInBestPositionForEditor(message, editor)
    }

    fun appendCompletions(suffixes: List<String>): String? {
        completions = completions?.mapIndexed { index: Int, s: String ->
            s + suffixes.getOrNull(index)
        } ?: suffixes
        return showCompletion()
    }

    fun showPreviousCompletion(): String? {
        currentIndex -= 1
        return showCompletion()
    }

    fun showNextCompletion(): String? {
        currentIndex += 1
        return showCompletion()
    }

    private fun showCompletion(): String? {
        inlays.clear()
        return editor?.let {
            if ((it !is EditorImpl) || it.selectionModel.hasSelection() || (InplaceRefactoring.getActiveInplaceRenamer(
                    it
                ) != null)
            ) {
                return null
            }
            return currentCompletion.let { completion ->
                try {
                    it.document.startGuardedBlockChecking()
                    inlays.render(it, completion, offset)
                    completion
                } finally {
                    it.document.stopGuardedBlockChecking()
                }
            }
        }
    }

    fun cancel() {
        Disposer.dispose(this)
    }

    fun apply() {
        if (done) {
            val tmpEditor = editor
            cancel()
            tmpEditor?.let {
                currentCompletion?.let { completion ->
                    it.document.insertString(offset, completion)
                    it.caretModel.moveToOffset(offset + completion.length)
                }
            }
        }
    }

    companion object {
        private val COMPLETION_PREVIEW: Key<CompletionPreview> = Key.create("COMPLETION_PREVIEW")

        @JvmStatic
        fun getInstance(editor: Editor): CompletionPreview? {
            return editor.getUserData(COMPLETION_PREVIEW)
        }

        @JvmStatic
        fun createInstance(editor: Editor, offset: Int): CompletionPreview {
            getInstance(editor)?.cancel()
            return CompletionPreview(editor, offset)
        }
    }
}
