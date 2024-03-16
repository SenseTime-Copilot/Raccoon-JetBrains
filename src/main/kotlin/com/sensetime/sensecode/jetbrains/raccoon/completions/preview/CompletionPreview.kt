package com.sensetime.sensecode.jetbrains.raccoon.completions.preview

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.util.messages.SimpleMessageBusConnection
import com.sensetime.sensecode.jetbrains.raccoon.completions.preview.render.CompletionInlays
import com.sensetime.sensecode.jetbrains.raccoon.persistent.settings.RaccoonSettingsState
import com.sensetime.sensecode.jetbrains.raccoon.topics.RACCOON_STATISTICS_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.SENSE_CODE_EDITOR_CHANGED_TOPIC
import com.sensetime.sensecode.jetbrains.raccoon.topics.RaccoonEditorChangedListener
import com.sensetime.sensecode.jetbrains.raccoon.ui.RaccoonNotification

class CompletionPreview private constructor(
    tmpEditor: Editor,
    private val offset: Int,
    private val language: String
) : RaccoonEditorChangedListener, Disposable {
    private var currentIndex: Int = 0
        set(value) {
            field = if (value < 0) {
                completions?.lastIndex ?: 0
            } else if (value > (completions?.lastIndex ?: 0)) {
                0
            } else {
                value
            }
            tooltip?.updateTooltip()
        }
    private var editor: Editor? = null
    private val inlays: CompletionInlays
    private var tooltip: CompletionPreviewTooltip? = null
    private var completions: List<String>? = null
        set(value) {
            value?.takeIf { (null == tooltip) && (null != editor) && (it.size > 1) }?.let {
                tooltip = CompletionPreviewTooltip(this, ::currentIndex, { completions?.size }, editor, inlays)
            }
            field = value
        }
    private val currentCompletion: String?
        get() = completions?.getOrNull(currentIndex)

    var done: Boolean = false
        set(value) {
            if (!field && value) {
                if (currentCompletion.isNullOrEmpty()) {
                    RaccoonNotification.popupNoCompletionSuggestionMessage(
                        editor,
                        RaccoonSettingsState.instance.isAutoCompleteMode
                    )
                } else {
                    ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                        .onInlineCompletionFinished(language, (completions?.size) ?: 1)
                }
            }
            field = value
        }
    private var editorChangedMessageBusConnection: SimpleMessageBusConnection? = null
        set(value) {
            field?.disconnect()
            field = value
        }

    init {
        editor = tmpEditor
        EditorUtil.disposeWithEditor(tmpEditor, this)
        inlays = CompletionInlays(this)
        tmpEditor.caretModel.addCaretListener(EditorCaretListener(), this)
        (tmpEditor as? EditorEx)?.addFocusListener(EditorFocusChangeListener(), this)
        tmpEditor.putUserData(COMPLETION_PREVIEW, this)

        editorChangedMessageBusConnection = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(SENSE_CODE_EDITOR_CHANGED_TOPIC, this)
        }
    }

    override fun dispose() {
        editor?.putUserData(COMPLETION_PREVIEW, null)
        editorChangedMessageBusConnection = null
        editor = null
    }

    override fun onEditorChanged(type: RaccoonEditorChangedListener.Type, editor: Editor) {
        if (editor === this.editor) {
            cancel()
        }
    }

    fun showError(message: String) {
        RaccoonNotification.popupMessageInBestPositionForEditor(
            message,
            editor,
            RaccoonSettingsState.instance.isAutoCompleteMode
        )
    }

    fun appendCompletions(suffixes: List<String>): String? {
        completions = completions?.mapIndexed { index: Int, s: String ->
            s + suffixes.getOrNull(index)
        } ?: suffixes
        return showCompletion()
    }

    fun showPreviousCompletion(): String? {
        currentIndex -= 1
        return showCompletion()
    }

    fun showNextCompletion(): String? {
        currentIndex += 1
        return showCompletion()
    }

    private fun showCompletion(): String? {
        inlays.clear()
        return editor?.let {
            if ((it !is EditorImpl) || it.selectionModel.hasSelection() || (InplaceRefactoring.getActiveInplaceRenamer(
                    it
                ) != null) || (null != LookupManager.getActiveLookup(editor))
            ) {
                return null
            }
            return currentCompletion.let { completion ->
                try {
                    it.document.startGuardedBlockChecking()
                    inlays.render(it, completion, offset)
                    completion
                } finally {
                    it.document.stopGuardedBlockChecking()
                }
            }
        }
    }

    fun cancel() {
        Disposer.dispose(this)
    }

    fun apply() {
        if (done) {
            val tmpEditor = editor
            cancel()
            tmpEditor?.let {
                currentCompletion?.takeIf { it.isNotEmpty() }?.let { completion ->
                    it.document.insertString(offset, completion)
                    it.caretModel.moveToOffset(offset + completion.length)
                    ApplicationManager.getApplication().messageBus.syncPublisher(RACCOON_STATISTICS_TOPIC)
                        .onInlineCompletionAccepted(language)
                }
            }
        }
    }

    companion object {
        private val COMPLETION_PREVIEW: Key<CompletionPreview> = Key.create("COMPLETION_PREVIEW")

        @JvmStatic
        fun getInstance(editor: Editor): CompletionPreview? {
            return editor.getUserData(COMPLETION_PREVIEW)
        }

        @JvmStatic
        fun createInstance(editor: Editor, offset: Int, language: String): CompletionPreview {
            getInstance(editor)?.cancel()
            return CompletionPreview(editor, offset, language)
        }
    }
}