package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener

internal class UserTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        RaccoonEditorChangedListener.onEditorChanged(project, RaccoonEditorChangedListener.Type.CHAR_TYPED, editor)
        return super.charTyped(c, project, editor, file)
    }
}