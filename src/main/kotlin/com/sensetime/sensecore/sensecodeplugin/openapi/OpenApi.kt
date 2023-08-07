package com.sensetime.sensecore.sensecodeplugin.openapi

import com.sensetime.sensecore.sensecodeplugin.openapi.request.ChatGptRequest
import kotlinx.coroutines.flow.Flow

interface OpenApi {
    suspend fun executeBasicActionStreaming(chatGptRequest: ChatGptRequest): Flow<StreamingResponse>
}

