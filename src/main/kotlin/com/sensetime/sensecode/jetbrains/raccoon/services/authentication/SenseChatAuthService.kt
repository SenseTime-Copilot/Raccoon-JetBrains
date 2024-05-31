package com.sensetime.intellij.plugins.sensecode.services.authentication

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.util.Url
import com.intellij.util.Urls
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import kotlinx.coroutines.runBlocking

class SenseChatAuthService : RestService() {

    override fun getServiceName(): String = "sensechat"

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        updateLoginResult(urlDecoder)
        sendStatus(HttpResponseStatus.OK, false, context.channel())
        return null
    }

    private fun updateLoginResult(urlDecoder: QueryStringDecoder) {
        val parameters = urlDecoder.parameters()
        val token = parameters["token"]?.firstOrNull()
        val refreshToken = parameters["refresh"]?.firstOrNull()

        if (token != null) {
            runBlocking {
                RaccoonClient.updateLoginResult(token, refreshToken)
            }
            println("Token received and processed: $token")
        } else {
            println("No token received")
        }
    }

    companion object {
        fun getServiceUrl(): Url {
            val port = BuiltInServerManager.getInstance().port
            println("Server started at port $port")
            return Urls.newFromEncoded("http://localhost:$port/api/sensechat")
        }

        fun startLoginFromBrowser(loginUrl: String) {
            BrowserUtil.browse("$loginUrl?redirect_uri=${getServiceUrl().toExternalForm()}")
        }
    }
}