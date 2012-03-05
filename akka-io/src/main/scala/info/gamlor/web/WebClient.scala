package info.gamlor.web

import akka.actor.{Extension, ExtensionIdProvider, ExtendedActorSystem, ExtensionId}
import akka.util.ByteString
import com.ning.http.client._
import akka.dispatch.{ExecutionContext, Promise, Future}
import com.ning.http.client.AsyncHandler.STATE
import java.util.concurrent.CancellationException
import java.nio.file.Path
import java.io.File


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


  def prepareGet(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareGet(url)
    new WebRequestBuilder(f,context)
  }
  def preparePost(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.preparePost(url)
    new WebRequestBuilder(f,context)
  }
  def preparePut(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.preparePut(url)
    new WebRequestBuilder(f,context)
  }
  def prepareDelete(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareDelete(url)
    new WebRequestBuilder(f,context)
  }
  def prepareHead(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareHead(url)
    new WebRequestBuilder(f,context)
  }
}


class WebRequestBuilder(private val unterlyingRequestBuilder: AsyncHttpClient#BoundRequestBuilder,
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

      def onStatusReceived(responseStatus: HttpResponseStatus) = {
        reportCancelledStatus(()=>handler.onStatusReceived(responseStatus))
      }

      def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = reportCancelledStatus(()=>handler.onBodyPartReceived(bodyPart))

      def onThrowable(t: Throwable) {
        handler.onThrowable(t)
        result.failure(t)
      }

      def onHeadersReceived(headers: HttpResponseHeaders)  =  reportCancelledStatus(()=>handler.onHeadersReceived(headers))

      private def reportCancelledStatus(originalCall: ()=>AsyncHandler.STATE): AsyncHandler.STATE = {
        val status = originalCall()
        status match {
          case STATE.ABORT => {
            result.failure(throw new CancellationException("Processing of this request has been cancelled"))
            status
          }
          case x => x
        }
      }
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
  def setBody(path: File)={
    unterlyingRequestBuilder.setBody(path)
    this
  }

  def setBody(path: Path):WebRequestBuilder  =setBody(path.toFile)


  def setBody(data: String) = {
    unterlyingRequestBuilder.setBody(data)
    this
  }


  def setHeader(name: String, value: String) = {
    unterlyingRequestBuilder.setHeader(name, value)
    this
  }

  def setPerRequestConfig(config:PerRequestConfig) = {
    unterlyingRequestBuilder.setPerRequestConfig(config)
    this
  }


}
