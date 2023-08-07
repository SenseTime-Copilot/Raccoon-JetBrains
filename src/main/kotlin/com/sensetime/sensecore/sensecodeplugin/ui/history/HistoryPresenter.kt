package com.sensetime.sensecore.sensecodeplugin.ui.history

import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryItem
import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryRepository

class HistoryPresenter(
    private val view: HistoryView,
    private val repository: HistoryRepository,
) {
    fun refreshHistory() {
        view.showHistory(repository.getAllHistoryItems())
    }

    fun delete(item: HistoryItem) {
        repository.deleteHistoryItem(item)
        refreshHistory()
    }

    fun deleteAll(selectedValues: List<HistoryItem>) {
        selectedValues.forEach {
                repository.deleteHistoryItem(it)
        }
        refreshHistory()
    }

    fun rename(item: HistoryItem, newName: String) {
        repository.renameHistoryItem(item, newName)
        refreshHistory()
    }
}

