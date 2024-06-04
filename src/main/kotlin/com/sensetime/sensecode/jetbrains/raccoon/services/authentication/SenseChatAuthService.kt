package com.sensetime.sensecode.jetbrains.raccoon.services.authentication

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.util.Url
import com.intellij.util.Urls
import com.sensetime.intellij.plugins.sensecode.services.authentication.CodeAuthServiceBase
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import kotlinx.coroutines.runBlocking

class SenseChatAuthService : RestService() {
    // TODO 改个唯一的名字 小浣熊的
    override fun getServiceName(): String = "raccoon"
    val SERVICE_NAME: String = "raccoon"

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        updateLoginResult(urlDecoder)
        sendStatus(HttpResponseStatus.OK, false, context.channel())
        activateLastFocusedFrame()
        return null
    }

    private fun updateLoginResult(urlDecoder: QueryStringDecoder) {
        val parameters = urlDecoder.parameters()
        var token = parameters["token"]?.firstOrNull()
        var refreshToken = parameters["refresh"]?.firstOrNull()

        if (token != null) {
//            token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MTcxNTY2OTAsImlzcyI6IjQ5ZDNhIiwibmFtZSI6IlJhY2Nvb25EYXZpZCIsIm5hdGlvbl9jb2RlIjoiODYiLCJuYmYiOjE3MTcxNDU4ODUsInNpZCI6InBsdWdpbjYxOWJjMmVkOGQ1NDE0OWQzYWFiM2U2YmUzLTg3MzAtNDA2OC05YzU4LWVkYWRjMDI3MzlkOCJ9.2nEB8yLxg47vcT_4dDipS9N07zwZX6j3Vu2cNW7-kwY'
//            refreshToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MTgzNTU0OTAsImlzcyI6IjQ5ZDNhIiwibmFtZSI6IlJhY2Nvb25EYXZpZCIsIm5hdGlvbl9jb2RlIjoiODYiLCJuYmYiOjE3MTcxNDU4ODUsInNpZCI6InBsdWdpbjYxOWJjMmVkOGQzZTI0OWQzYTYwNmI0MDg1LTJjNjYtNGFkMC04MmI4LTlmZmRiM2VlYTQwMyJ9.O-RvrwRIViPp13nU_99b7vcIkhg4HHr5Mh6cmNjAnNw'
//            runBlocking {
////                clientJobRunner.updateTokensResponseBodyInsideCatching
//                RaccoonClient.updateLoginResult('token, refreshToken')
//            }

            // llmClient 是 LLMClient类型，需要转换成 实现类 RaccoonClient，这样可以调用RaccoonClient 的方法
            // LLMClientManager.launchClientJob 是类的伴生对象 方法，不需要初始化实例就可以调用
            LLMClientManager.launchClientJob { llmClient ->
                val raccoonClient = llmClient as? RaccoonClient
                // 获取url的参数
                raccoonClient?.updateLoginResult("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MTcxNTY2OTAsImlzcyI6IjQ5ZDNhIiwibmFtZSI6IlJhY2Nvb25EYXZpZCIsIm5hdGlvbl9jb2RlIjoiODYiLCJuYmYiOjE3MTcxNDU4ODUsInNpZCI6InBsdWdpbjYxOWJjMmVkOGQ1NDE0OWQzYWFiM2U2YmUzLTg3MzAtNDA2OC05YzU4LWVkYWRjMDI3MzlkOCJ9.2nEB8yLxg47vcT_4dDipS9N07zwZX6j3Vu2cNW7-kwY",
                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MTgzNTU0OTAsImlzcyI6IjQ5ZDNhIiwibmFtZSI6IlJhY2Nvb25EYXZpZCIsIm5hdGlvbl9jb2RlIjoiODYiLCJuYmYiOjE3MTcxNDU4ODUsInNpZCI6InBsdWdpbjYxOWJjMmVkOGQzZTI0OWQzYTYwNmI0MDg1LTJjNjYtNGFkMC04MmI4LTlmZmRiM2VlYTQwMyJ9.O-RvrwRIViPp13nU_99b7vcIkhg4HHr5Mh6cmNjAnNw")

            }
            println("Token received and processed: $token")
        } else {
            println("No token received")
        }
    }

    companion object {
        const val SERVICE_NAME = "raccoon"
        private fun getServiceUrl(): Url {
            val port = BuiltInServerManager.getInstance().port
            println("Server started at port $port")
            return Urls.newFromEncoded("http://localhost:$port/api/$SERVICE_NAME")
        }

        fun startLoginFromBrowser(loginUrl: String) {
            BrowserUtil.browse("$loginUrl?redirect_uri=${getServiceUrl().toExternalForm()}")
        }
    }
}