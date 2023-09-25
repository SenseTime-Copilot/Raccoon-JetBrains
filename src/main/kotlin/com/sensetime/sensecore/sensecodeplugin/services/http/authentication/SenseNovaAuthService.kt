package com.sensetime.sensecore.sensecodeplugin.services.http.authentication

import com.intellij.ide.BrowserUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.sensetime.sensecore.sensecodeplugin.clients.SenseNovaClient
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService

class SenseNovaAuthService : RestService() {
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
        val SERVICE_NAME = "${CodeAuthServiceBase.SENSECODE_PREFIX}/sensenova"
        override val baseUrl: Url
            get() = Urls.newFromEncoded("http://localhost:${BuiltInServerManager.getInstance().port}/$PREFIX/$SERVICE_NAME")

        fun updateLoginResult(urlDecoder: QueryStringDecoder) {
            val queries = urlDecoder.parameters()
            SenseNovaClient.updateLoginResult(
                queries.getValue("token").first(),
                queries.getValue("refresh").first(),
                queries["expires"]?.firstOrNull()?.toIntOrNull()
            )
        }

        fun startLoginFromBrowser(loginUrl: String, refreshExpiresAfter: Int) {
            BrowserUtil.browse(
                "$loginUrl?redirect_url=${baseUrl.toExternalForm()}&refresh_expires_after=$refreshExpiresAfter"
            )
        }
    }
}