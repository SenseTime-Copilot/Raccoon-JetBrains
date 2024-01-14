package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener

class UserEnterHandler : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        RaccoonEditorChangedListener.onEditorChanged(RaccoonEditorChangedListener.Type.ENTER_TYPED, editor)
        return super.postProcessEnter(file, editor, dataContext)
    }
}