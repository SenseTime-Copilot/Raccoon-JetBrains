package com.sensetime.intellij.plugins.sensecode.services.authentication

import com.intellij.ide.BrowserUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.sensetime.intellij.plugins.sensecode.clients.SenseChatOnlyLoginClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService

class SenseChatAuthService : RestService() {
    override fun getServiceName(): String = Util.SERVICE_NAME

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        Util.updateLoginResult(urlDecoder)
        sendStatus(HttpResponseStatus.OK, false, context.channel())
        activateLastFocusedFrame()
        return null
    }

    object Util : CodeAuthServiceBase {
        val SERVICE_NAME = "${CodeAuthServiceBase.SENSECODE_PREFIX}/sensechat"
        override val baseUrl: Url
            get() = Urls.newFromEncoded("http://localhost:${BuiltInServerManager.getInstance().port}/$PREFIX/$SERVICE_NAME")

        fun updateLoginResult(urlDecoder: QueryStringDecoder) {
            val queries = urlDecoder.parameters()
            SenseChatOnlyLoginClient.updateLoginResult(
                queries.getValue("token").first(),
                queries.getValue("refresh").first()
            )
        }

        fun startLoginFromBrowser(loginUrl: String) {
            BrowserUtil.browse("$loginUrl?redirect_url=${baseUrl.toExternalForm()}")
        }
    }
}