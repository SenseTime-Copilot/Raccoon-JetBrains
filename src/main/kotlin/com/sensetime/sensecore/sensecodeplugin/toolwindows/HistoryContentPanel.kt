package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.addMouseListener
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.observable.util.addListDataListener
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.min

class HistoryContentPanel(
    private var onHistoryClick: ((SenseCodeChatHistoryState.History) -> Unit)?
) : JPanel(), Disposable, ListDataListener, MouseListener by object : MouseAdapter() {},
    KeyListener by object : KeyAdapter() {} {
    class HistoryListModel(items: List<SenseCodeChatHistoryState.History>) :
        CollectionListModel<SenseCodeChatHistoryState.History>(items)

    fun saveHistory(history: SenseCodeChatHistoryState.History) {
        historyListModel.add(history)
    }

    private val historyListModel: HistoryListModel =
        HistoryListModel(SenseCodeChatHistoryState.instance.histories.toHistories())
    private val historyList: JBList<SenseCodeChatHistoryState.History> = JBList(historyListModel).apply {
        cellRenderer = ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
            Box.createVerticalBox().apply {
                value.conversations.firstOrNull()?.let {
                    add(JSeparator())
                    add(Utils.createMessagePanel(
                        Utils.createRoleBox(
                            true,
                            it.user.datetime,
                            it.user.displayName
                        ),
                        Utils.createContentTextPane(
                            true,
                            SenseCodeChatHistoryState.GenerateState.ONLY_PROMPT,
                            it.user.displayText.run { substring(0, min(length, 100)) }
                        )
                    ))
                    add(JSeparator())
                }
            }
        }
        addMouseListener(this@HistoryContentPanel, this@HistoryContentPanel)
        addKeyListener(this@HistoryContentPanel, this@HistoryContentPanel)
    }

    init {
        layout = BorderLayout()
        add(JBScrollPane(historyList), BorderLayout.CENTER)
        historyListModel.addListDataListener(this, this)
    }

    override fun dispose() {
        onHistoryClick = null
    }

    override fun mouseClicked(e: MouseEvent?) {
        e?.takeIf { 2 == it.clickCount }?.let {
            onHistoryClick?.let { callback ->
                historyList.selectedIndex.takeIf { it in 0..historyListModel.items.lastIndex }?.let {
                    val history = historyListModel.items[it]
                    historyListModel.remove(it)
                    callback(history)
                }
            }
        }
    }

    override fun keyReleased(e: KeyEvent?) {
        e?.takeIf { it.keyCode == KeyEvent.VK_BACK_SPACE }?.let {
            historyList.selectedIndices.forEach { index ->
                historyListModel.remove(index)
            }
        }
    }

    override fun intervalAdded(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                SenseCodeChatHistoryState.instance.histories.add(i, historyListModel.items[i].encodeToJsonString())
            }
        }
    }

    override fun intervalRemoved(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                SenseCodeChatHistoryState.instance.histories.removeAt(index0)
            }
        }
    }

    override fun contentsChanged(e: ListDataEvent?) {
        e?.run {
            for (i in index0..index1) {
                SenseCodeChatHistoryState.instance.histories[i] = historyListModel.items[i].encodeToJsonString()
            }
        }
    }
}