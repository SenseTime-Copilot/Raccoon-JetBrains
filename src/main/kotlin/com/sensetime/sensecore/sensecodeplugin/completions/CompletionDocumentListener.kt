package com.sensetime.sensecore.sensecodeplugin.completions

import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent

class CompletionDocumentListener : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
        super.documentChangedNonBulk(event)
    }
}