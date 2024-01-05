package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

@Topic.AppLevel
val SENSE_CODE_EDITOR_CHANGED_TOPIC =
    Topic.create("RaccoonEditorChangedListener", RaccoonEditorChangedListener::class.java)

interface RaccoonEditorChangedListener {
    enum class Type {
        CHAR_TYPED,
        ENTER_TYPED,
        MOUSE_PRESSED,
        MOUSE_RELEASED,
        MOUSE_CLICKED,
        DOCUMENT_CHANGED,
        CARET_POSITION_CHANGED,
        FOCUS_LOST
    }

    fun onEditorChanged(type: Type, editor: Editor)

    companion object {
        fun onEditorChanged(type: Type, editor: Editor) {
            ApplicationManager.getApplication().messageBus.syncPublisher(SENSE_CODE_EDITOR_CHANGED_TOPIC)
                .onEditorChanged(type, editor)
        }
    }
}