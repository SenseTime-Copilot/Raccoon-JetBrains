package com.sensetime.sensecode.jetbrains.raccoon.completions.auto

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
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
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.completions.actions.ManualTriggerInlineCompletionAction
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.statistics.RaccoonStatisticsServer
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_EDITOR_CHANGED_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener
import com.sensetime.sensecode.jetbrains.raccoon.utils.RaccoonActionUtils
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Service(Service.Level.PROJECT)
class AutoCompletionServer(
    private var project: Project?
) : RaccoonEditorChangedListener, EditorFactoryListener, CaretListener, FocusChangeListener, Disposable {
    private val lastEditorChangedType: AtomicInteger = AtomicInteger(-1)
    private val lastEditorChangedTimeMs: AtomicLong = AtomicLong(getCurrentTimeMs())
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
                    }.onFailure { e ->
                        if (e is CancellationException) {
                            throw e
                        }
                    }
                }
            }

            RaccoonStatisticsServer.getInstance().onProjectOpened()

            project?.let {
                FileEditorManager.getInstance(it).allEditors.forEach { fileEditor ->
                    (fileEditor as? TextEditor)?.editor?.let { editor ->
                        addEditorListener(editor)
                    }
                }
            }
        }
    }

    override fun dispose() {
        RaccoonStatisticsServer.getInstance().onProjectClosed()
        ApplicationManager.getApplication().invokeLater {
            autoCompletionCoroutineScope.cancel()
            editorChangedMessageBusConnection = null
            project = null
        }
    }

    override fun caretPositionChanged(event: CaretEvent) {
        RaccoonEditorChangedListener.onEditorChanged(
            RaccoonEditorChangedListener.Type.CARET_POSITION_CHANGED,
            event.editor
        )
    }

    override fun focusLost(editor: Editor) {
        RaccoonEditorChangedListener.onEditorChanged(RaccoonEditorChangedListener.Type.FOCUS_LOST, editor)
    }

    override fun onEditorChanged(type: RaccoonEditorChangedListener.Type, editor: Editor) {
        if ((RaccoonEditorChangedListener.Type.DOCUMENT_CHANGED == type) && (lastEditorChangedType.get() == RaccoonEditorChangedListener.Type.ENTER_TYPED.ordinal)) {
            lastEditorChangedType.set(RaccoonEditorChangedListener.Type.CHAR_TYPED.ordinal)
        } else {
            lastEditorChangedType.set(type.ordinal)
        }
        lastEditorChangedTimeMs.set(getCurrentTimeMs())
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        super.editorCreated(event)
        addEditorListener(event.editor)
    }

    private fun addEditorListener(editor: Editor) {
        editor.caretModel.addCaretListener(this, this)
        (editor as? EditorEx)?.addFocusListener(this, this)
    }

    private fun triggerCompletion() {
        project?.let {
            (FileEditorManager.getInstance(it).selectedTextEditor as? EditorEx)?.let { editor ->
                RaccoonActionUtils.getAction(ManualTriggerInlineCompletionAction::class)?.let { action ->
                    ActionUtil.invokeAction(action, editor.dataContext, AUTO_COMPLETION_PLACE, null, null)
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
            if (lastEditorChangedType.get() in setOf(
                    RaccoonEditorChangedListener.Type.CHAR_TYPED.ordinal,
                    RaccoonEditorChangedListener.Type.ENTER_TYPED.ordinal
                )
            ) {
                triggerCompletion()
            }
            waitForTimeMs = autoCompleteDelayMs
        }
        return waitForTimeMs
    }

    private class Initializer : StartupActivity {
        override fun runActivity(project: Project) {
            project.service<AutoCompletionServer>()
        }
    }

    companion object {
        @JvmStatic
        fun getCurrentTimeMs(): Long = System.nanoTime() / (1000L * 1000L)

        const val AUTO_COMPLETION_PLACE = "RaccoonAutoCompletion"
    }
}