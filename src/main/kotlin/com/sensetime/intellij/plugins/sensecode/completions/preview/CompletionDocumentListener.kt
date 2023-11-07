package com.sensetime.intellij.plugins.sensecode.completions.preview

import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent

class CompletionDocumentListener : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
        super.documentChangedNonBulk(event)
    }
}