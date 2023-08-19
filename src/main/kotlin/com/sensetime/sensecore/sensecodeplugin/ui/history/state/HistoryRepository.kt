package com.sensetime.sensecore.sensecodeplugin.ui.history.state

interface HistoryRepository {
    fun addOrUpdateHistoryItem(item: HistoryItem)
    fun deleteHistoryItem(item: HistoryItem)
    fun renameHistoryItem(item: HistoryItem, newName: String)
    fun getAllHistoryItems(): List<HistoryItem>
}
