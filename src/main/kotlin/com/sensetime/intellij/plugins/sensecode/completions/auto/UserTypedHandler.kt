package com.sensetime.intellij.plugins.sensecode.completions.auto

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.sensetime.intellij.plugins.sensecode.topics.SenseCodeEditorChangedListener

class UserTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        SenseCodeEditorChangedListener.onEditorChanged(editor)
        return super.charTyped(c, project, editor, file)
    }
}