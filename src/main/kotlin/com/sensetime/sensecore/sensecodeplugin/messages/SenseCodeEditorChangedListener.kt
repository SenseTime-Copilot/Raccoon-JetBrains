package com.sensetime.sensecore.sensecodeplugin.messages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_EDITOR_CHANGED_TOPIC =
    Topic.create("SenseCodeEditorChangedListener", SenseCodeEditorChangedListener::class.java)

interface SenseCodeEditorChangedListener {
    fun onEditorChanged(editor: Editor)

    companion object {
        fun onEditorChanged(editor: Editor) {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_EDITOR_CHANGED_TOPIC)
                .onEditorChanged(editor)
        }
    }
}