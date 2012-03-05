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
  def preparePost(url: String): RequestBuilder = {
    val f = asyncHttpClient.preparePost(url)
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
  def execute[A](handler: AsyncHandler[A]):Future[A] = {
    val result = Promise[A]()(context)
    unterlyingRequestBuilder.execute(new AsyncHandler[A] {
      def onCompleted() = {
        val r = handler.onCompleted();
        result.success(r)
        r
      }

      def onStatusReceived(responseStatus: HttpResponseStatus) = handler.onStatusReceived(responseStatus)

      def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = handler.onBodyPartReceived(bodyPart)

      def onThrowable(t: Throwable) {
        handler.onThrowable(t)
        result.failure(t)
      }

      def onHeadersReceived(headers: HttpResponseHeaders)  = handler.onHeadersReceived(headers)
    })
    result
  }

  def addBodyPart(part: Part) = {
    unterlyingRequestBuilder.addBodyPart(part)
    this
  }

  def addCookie(cookie: Cookie) = {
    unterlyingRequestBuilder.addCookie(cookie)
    this
  }

  def addHeader(name: String, value: String) = {
    unterlyingRequestBuilder.addHeader(name, value)
    this
  }

  def addParameter(key: String, value: String) = {
    unterlyingRequestBuilder.addHeader(key, value)
    this
  }

  def addQueryParameter(name: String, value: String) = {
    unterlyingRequestBuilder.addQueryParameter(name, value)
    this
  }


  def setBody(data: Array[Byte]) = {
    unterlyingRequestBuilder.setBody(data)
    this
  }

  def setBody(data: ByteString) = {
    unterlyingRequestBuilder.setBody(data.toArray)
    this
  }


  def setBody(data: String) = {
    unterlyingRequestBuilder.setBody(data)
    this
  }


  def setHeader(name: String, value: String) = {
    unterlyingRequestBuilder.setHeader(name, value)
    this
  }


}
