package com.sensetime.sensecore.sensecodeplugin.toolwindows

import com.intellij.ui.CollectionListModel

class ChatConversationListModel(items: List<SenseCodeChatHistoryState.Conversation>) :
    CollectionListModel<SenseCodeChatHistoryState.Conversation>(items)