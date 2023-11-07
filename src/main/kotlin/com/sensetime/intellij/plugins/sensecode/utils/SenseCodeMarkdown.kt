package com.sensetime.intellij.plugins.sensecode.utils

import org.intellij.markdown.IElementType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

object SenseCodeMarkdown {
    // https://github.com/JetBrains/markdown/issues/72
    private val embeddedHtmlType = IElementType("ROOT")

    fun convertMarkdownToHtml(markdownText: String): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).parse(embeddedHtmlType, markdownText)
        return HtmlGenerator(markdownText, parsedTree, flavour).generateHtml()
    }
}