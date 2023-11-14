package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_EDITOR_CHANGED_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonActionUtils
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Service(Service.Level.PROJECT)
class AutoCompletionServer(
    private var project: Project?
) : RaccoonEditorChangedListener, EditorFactoryListener, CaretListener, FocusChangeListener, Disposable {
    private var lastEditorChangedTimeMs: AtomicLong = AtomicLong(getCurrentTimeMs())
    private val autoCompletionCoroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SenseAutoCompletionServer"))
    private var editorChangedMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    init {
        ApplicationManager.getApplication().invokeLater {
            editorChangedMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
                it.subscribe(SENSE_CODE_EDITOR_CHANGED_TOPIC, this)
            }

            EditorFactory.getInstance().addEditorFactoryListener(this, this)

            autoCompletionCoroutineScope.launch {
                val waitForTimeMs = AtomicLong(1000)
                while (true) {
                    kotlin.runCatching {
                        ApplicationManager.getApplication().invokeAndWait {
                            waitForTimeMs.set(tryTriggerCompletion())
                        }
                        delay(waitForTimeMs.get())
                    }
                }
            }
        }
    }

    override fun dispose() {
        ApplicationManager.getApplication().invokeLater {
            autoCompletionCoroutineScope.cancel()
            editorChangedMessageBusConnection = null
            project = null
        }
    }

    override fun caretPositionChanged(event: CaretEvent) {
        RaccoonEditorChangedListener.onEditorChanged(event.editor)
    }

    override fun focusLost(editor: Editor) {
        RaccoonEditorChangedListener.onEditorChanged(editor)
    }

    override fun onEditorChanged(editor: Editor) {
        lastEditorChangedTimeMs.set(getCurrentTimeMs())
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        super.editorCreated(event)

        event.editor.caretModel.addCaretListener(this, this)
        (event.editor as? EditorEx)?.addFocusListener(this, this)
    }

    private fun triggerCompletion() {
        project?.let {
            (FileEditorManager.getInstance(it).selectedTextEditor as? EditorEx)?.let { editor ->
                RaccoonActionUtils.getAction(ManualTriggerInlineCompletionAction::class)?.let { action ->
                    ActionUtil.invokeAction(action, editor.dataContext, ActionPlaces.UNKNOWN, null, null)
                }
            }
        }
    }

    private fun tryTriggerCompletion(): Long {
        val isAutoCompleteMode = RaccoonSettingsState.instance.isAutoCompleteMode
        val autoCompleteDelayMs = RaccoonSettingsState.instance.autoCompleteDelayMs.toLong()
        if (!isAutoCompleteMode || (null == project?.takeUnless {
                DumbService.isDumb(it) || (null != LookupManager.getInstance(
                    it
                ).activeLookup)
            })) {
            return autoCompleteDelayMs
        }

        var waitForTimeMs: Long = autoCompleteDelayMs - max(0, (getCurrentTimeMs() - lastEditorChangedTimeMs.get()))
        if (waitForTimeMs <= 0) {
            triggerCompletion()
            waitForTimeMs = autoCompleteDelayMs
        }
        return waitForTimeMs
    }

    private class Initializer : ProjectActivity {
        override suspend fun execute(project: Project) {
            project.service<AutoCompletionServer>()
        }
    }

    companion object {
        @JvmStatic
        fun getCurrentTimeMs(): Long = System.nanoTime() / (1000L * 1000L)
    }
}