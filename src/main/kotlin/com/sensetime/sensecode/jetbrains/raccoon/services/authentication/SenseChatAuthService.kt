package com.sensetime.sensecode.jetbrains.raccoon.services.authentication
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.Url
import com.intellij.util.Urls
import com.sensetime.sensecode.jetbrains.raccoon.clients.LLMClientManager
import com.sensetime.sensecode.jetbrains.raccoon.clients.RaccoonClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SenseChatAuthService : RestService() {

    private val logger = LoggerFactory.getLogger(SenseChatAuthService::class.java)

    override fun getServiceName(): String = SERVICE_NAME

    override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
        return true
    }

    override fun isOriginAllowed(request: HttpRequest): OriginCheckResult {
        return OriginCheckResult.ALLOW
    }

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        println("Received request:${request.uri()}")

        try {
            updateLoginResult(urlDecoder)
            sendStatus(HttpResponseStatus.OK, false, context.channel())
            activateLastFocusedFrame()
        } catch (e: Exception) {
            logger.error("Failed to process request: ${request.uri()}", e)
            sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, false, context.channel())
        }
        return null
    }


    private fun updateLoginResult(urlDecoder: QueryStringDecoder) {
        val parameters = urlDecoder.parameters()
        val authorizationCode = parameters["authorization_code"]?.firstOrNull()

        if (authorizationCode != null) {
            LLMClientManager.launchClientJob { llmClient ->
                val raccoonClient = llmClient as? RaccoonClient
                raccoonClient?.loginWithAuthorizationCode(project = null,uiComponentForEdt = null,authorizationCode = authorizationCode)
            }
        } else {
            logger.warn("No token received")
        }
    }

//    private fun updateLoginResult(urlDecoder: QueryStringDecoder) {
//        val parameters = urlDecoder.parameters()
//        val token = parameters["access_token"]?.firstOrNull()
//        val refreshToken = parameters["refresh_token"]?.firstOrNull()
//
//        if (token != null) {
//            LLMClientManager.launchClientJob { llmClient ->
//                val raccoonClient = llmClient as? RaccoonClient
//                raccoonClient?.updateLoginResult(token, refreshToken)
//                logger.info("Token received and processed: $token")
//            }
//        } else {
//            logger.warn("No token received")
//        }
//    }

    companion object {
        const val SERVICE_NAME = "raccoon"
        private fun getServiceUrl(): Url {
            val port = BuiltInServerManager.getInstance().port
            println("Server started at port $port")
            return Urls.newFromEncoded("http://localhost:$port/api/$SERVICE_NAME")
        }

        fun startLoginFromBrowser(loginUrl: String) {
            val url = getServiceUrl().toExternalForm()
            val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
            val appInfo = ApplicationInfo.getInstance()
            val appname = URLEncoder.encode(appInfo.versionName, StandardCharsets.UTF_8.toString())
            BrowserUtil.browse("$loginUrl?ide=IDEA&appname=$appname&redirect=$encodedUrl")
        }
    }
}
