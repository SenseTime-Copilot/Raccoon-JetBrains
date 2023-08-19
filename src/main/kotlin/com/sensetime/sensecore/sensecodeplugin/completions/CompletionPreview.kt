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

class CompletionPreview private constructor(
    tmpEditor: Editor,
    private val offset: Int
) : Disposable {
    var done: Boolean = false
    private var currentIndex = 0
    private var editor: Editor? = null
    private val inlays: CompletionInlays
    private var completions: List<String>? = null

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

    fun appendCompletions(suffixes: List<String>) {
        completions = completions?.mapIndexed { index: Int, s: String ->
            s + suffixes.getOrNull(index)
        } ?: suffixes
        showCompletion()
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
            return completions?.getOrNull(currentIndex)?.let { completion ->
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
                completions?.getOrNull(currentIndex)?.let { completion ->
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
