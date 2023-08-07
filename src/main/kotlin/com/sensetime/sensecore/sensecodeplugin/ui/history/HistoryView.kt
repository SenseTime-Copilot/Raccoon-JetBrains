package com.sensetime.sensecore.sensecodeplugin.ui.history

import com.sensetime.sensecore.sensecodeplugin.ui.history.state.HistoryItem

interface HistoryView {
    fun showHistory(history: List<HistoryItem>)
}
