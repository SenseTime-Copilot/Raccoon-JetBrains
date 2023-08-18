package com.sensetime.sensecore.sensecodeplugin.completions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.sensetime.sensecore.sensecodeplugin.completions.render.CompletionInlays

class CompletionPreview private constructor(
    tmpEditor: Editor,
    private val offset: Int,
    private var completions: List<String>
) : Disposable {
    private var currentIndex = 0
    private var editor: Editor? = null
    private val inlays: CompletionInlays

    init {
        editor = tmpEditor
        inlays = CompletionInlays(this)
        EditorUtil.disposeWithEditor(tmpEditor, this)
        tmpEditor.putUserData(COMPLETION_PREVIEW, this)
    }

    override fun dispose() {
        editor?.putUserData(COMPLETION_PREVIEW, null)
        editor = null
    }

    fun showCompletion(): String? {
        inlays.clear()
        return editor?.let {
            if (completions.isEmpty() || (it !is EditorImpl) || it.selectionModel.hasSelection() || (InplaceRefactoring.getActiveInplaceRenamer(
                    it
                ) != null)
            ) {
                return null
            }
            return try {
                val completion = completions[currentIndex]
                it.document.startGuardedBlockChecking()
                inlays.render(it, completion, offset)
                completion
            } finally {
                it.document.stopGuardedBlockChecking()
            }
        }
    }

    fun cancel() {
        Disposer.dispose(this)
    }

    fun apply() {
        cancel()
        editor?.let {
            val completion = completions[currentIndex]
            it.document.insertString(offset, completion)
            it.caretModel.moveToOffset(offset + completion.length)
        }
    }

    companion object {
        private val COMPLETION_PREVIEW: Key<CompletionPreview> = Key.create("COMPLETION_PREVIEW")

        @JvmStatic
        fun getInstance(editor: Editor): CompletionPreview? {
            return editor.getUserData(COMPLETION_PREVIEW)
        }

        @JvmStatic
        fun createInstance(editor: Editor, offset: Int, completions: List<String>): CompletionPreview {
            getInstance(editor)?.cancel()
            return CompletionPreview(editor, offset, completions)
        }
    }
}
