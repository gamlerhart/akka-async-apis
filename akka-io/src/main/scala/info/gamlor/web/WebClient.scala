package info.gamlor.web

import akka.actor.{Extension, ExtensionIdProvider, ExtendedActorSystem, ExtensionId}
import akka.util.ByteString
import com.ning.http.client._
import akka.dispatch.{ExecutionContext, Promise, Future}


/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

object WebClient
  extends ExtensionId[WebClient]
  with ExtensionIdProvider {
  override def lookup = this

  override def createExtension(system: ExtendedActorSystem) = new WebClient(system.dispatcher)
}


class WebClient(private val context:ExecutionContext,
                private val asyncHttpClient: AsyncHttpClient = new AsyncHttpClient()
                 ) extends Extension {


  def prepareGet(url: String): RequestBuilder = {
    val f = asyncHttpClient.prepareGet(url)
    new RequestBuilder(f,context)
  }
}


class RequestBuilder(private val unterlyingRequestBuilder: AsyncHttpClient#BoundRequestBuilder,
                      private val context:ExecutionContext) {
  def execute():Future[Response] = {
    val result = Promise[Response]()(context)
    unterlyingRequestBuilder.execute(new AsyncCompletionHandler[Response] {
      def onCompleted(response: Response) = {
        result.success(response)
        response
      }

      override def onThrowable(t: Throwable) {
        result.failure(t)
      }
    })
    result
  }

  def addBodyPart(part: Part) = unterlyingRequestBuilder.addBodyPart(part)

  def addCookie(cookie: Cookie) = unterlyingRequestBuilder.addCookie(cookie)

  def addHeader(name: String, value: String) = unterlyingRequestBuilder.addHeader(name, value)

  def addParameter(key: String, value: String) = unterlyingRequestBuilder.addHeader(key, value)

  def addQueryParameter(name: String, value: String) = unterlyingRequestBuilder.addQueryParameter(name, value)


  def setBody(data: Array[Byte]) = unterlyingRequestBuilder.setBody(data)

  def setBody(data: ByteString) = unterlyingRequestBuilder.setBody(data.toArray)


  def setBody(data: String): AsyncHttpClient#BoundRequestBuilder = unterlyingRequestBuilder.setBody(data)


  def setHeader(name: String, value: String) = unterlyingRequestBuilder.setHeader(name, value)


}
