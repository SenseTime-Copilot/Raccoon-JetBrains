package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_EDITOR_CHANGED_TOPIC =
    Topic.create("RaccoonEditorChangedListener", RaccoonEditorChangedListener::class.java)

interface RaccoonEditorChangedListener {
    fun onEditorChanged(editor: Editor)

    companion object {
        fun onEditorChanged(editor: Editor) {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_EDITOR_CHANGED_TOPIC)
                .onEditorChanged(editor)
        }
    }
}