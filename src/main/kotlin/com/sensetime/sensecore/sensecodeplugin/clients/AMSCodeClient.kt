package com.sensetime.sensecore.sensecodeplugin.clients

import com.sensetime.sensecore.sensecodeplugin.clients.requests.AMSCodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.requests.CodeRequest
import com.sensetime.sensecore.sensecodeplugin.clients.responses.AMSCodeResponse
import com.sensetime.sensecore.sensecodeplugin.clients.responses.CodeResponse
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

abstract class AMSCodeClient : CodeClient() {
    override fun toOkHttpRequest(request: CodeRequest, stream: Boolean): Request {
        val amsRequest = AMSCodeRequest.makeAMSCodeRequest(request, stream)
        val amsRequestJson = SenseCodeClientJson.encodeToString(AMSCodeRequest.serializer(), amsRequest)
        var requestBuilder = Request.Builder()
            .url(request.apiEndpoint)
            .header("Content-Type", "application/json")
        if (stream) {
            requestBuilder = requestBuilder.addHeader("Accept", "text/event-stream")
        }
        return addAuthorizationToHeader(requestBuilder, request).post(amsRequestJson.toRequestBody()).build()
    }

    override fun isStringResponseDone(data: String): Boolean = "[DONE]" == data

    override fun toCodeResponse(body: String): CodeResponse =
        SenseCodeClientJson.decodeFromString(AMSCodeResponse.serializer(), body)

    protected abstract fun addAuthorizationToHeader(
        requestBuilder: Request.Builder,
        request: CodeRequest
    ): Request.Builder
}