package com.sensetime.sensecode.jetbrains.raccoon.utils

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet


internal object RaccoonMarkdown {
    fun convertMarkdownToHtml(markdownText: String): String = MutableDataSet().let { options ->
        HtmlRenderer.builder(options).build().render(Parser.builder(options).build().parse(markdownText))
    }
}
