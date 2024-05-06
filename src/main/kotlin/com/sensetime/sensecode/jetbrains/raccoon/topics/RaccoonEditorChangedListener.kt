package com.sensetime.sensecode.jetbrains.raccoon.topics

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic


@Topic.ProjectLevel
internal val RACCOON_EDITOR_CHANGED_TOPIC =
    Topic.create("RaccoonEditorChangedListener", RaccoonEditorChangedListener::class.java)

internal interface RaccoonEditorChangedListener {
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
        fun onEditorChanged(project: Project, type: Type, editor: Editor) {
            project.messageBus.syncPublisher(RACCOON_EDITOR_CHANGED_TOPIC).onEditorChanged(type, editor)
        }
    }
}
