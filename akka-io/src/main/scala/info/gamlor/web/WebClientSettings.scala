package info.gamlor.web

import com.typesafe.config.Config
import com.ning.http.client.AsyncHttpClientConfig

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

object WebClientSettings {

  private val WebClientConfig = "webclient."


  def createConfig(config: Config) = {
    val builder = new AsyncHttpClientConfig.Builder()
    builder.setMaximumConnectionsTotal(config.getInt(WebClientConfig+"maxTotalConnections"))
    builder.setMaximumConnectionsPerHost(config.getInt(WebClientConfig+"maxConnectionsPerHost"))
    builder.setConnectionTimeoutInMs(config.getMilliseconds(WebClientConfig+"connectionTimeoutInMS").toInt)
    builder.setWebSocketIdleTimeoutInMs(config.getMilliseconds(WebClientConfig+"websocketTimoutInMS").toInt)
    builder.setIdleConnectionInPoolTimeoutInMs(config.getMilliseconds(WebClientConfig+"idleConnectionInPoolTimeoutInMS").toInt)
    builder.setIdleConnectionTimeoutInMs(config.getMilliseconds(WebClientConfig+"idleConnectionTimeoutInMS").toInt)
    builder.setRequestTimeoutInMs(config.getMilliseconds(WebClientConfig+"requestTimeoutInMS").toInt)
    builder.setFollowRedirects(config.getBoolean(WebClientConfig+"redirectsEnabled"))
    builder.setMaximumNumberOfRedirects(config.getInt(WebClientConfig+"maxRedirects"))
    builder.setCompressionEnabled(config.getBoolean(WebClientConfig+"compressionEnabled"))
    builder.setUserAgent(config.getString(WebClientConfig+"userAgent"))
    builder.setUseProxyProperties(config.getBoolean(WebClientConfig+"useProxyProperties"))

    builder.build()
  }
}
