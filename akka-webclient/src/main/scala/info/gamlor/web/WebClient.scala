package info.gamlor.web

import akka.actor.{Extension, ExtensionIdProvider, ExtendedActorSystem, ExtensionId}
import akka.util.ByteString
import akka.dispatch.{ExecutionContext, Promise, Future}
import java.util.concurrent.CancellationException
import java.nio.file.Path
import java.io.File
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE


/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

/**
 * Integration for the Async HTTP library with Akka.
 *
 * Use this to get the web client instance assiosiated with this actor system.
 * The client will be initializied with the settings specified in the Akka/System configuration.
 *
 * Usage:
 * <pre>
 *   val webAccess = WebClient(actorSystem)
 *   val futureWithResult = webAccess.prepareGet("http://some.site.localhost").execute()
 *
 *   // handel result of the future
 * </pre>
 *
 * If you want to manually create a instance you can use [[info.gamlor.web.WebClient]] to wrap
 * the [[com.ning.http.client.AsyncHttpClient]] you want to use.
 */
object WebClient
  extends ExtensionId[WebClient]
  with ExtensionIdProvider {
  override def lookup = this

  override def createExtension(system: ExtendedActorSystem) = {
    val cfg = WebClientSettings.createConfig(system.settings.config)
    val client = new WebClient(system.dispatcher,new AsyncHttpClient(cfg))
    system.registerOnTermination(()=>client.asyncHttpClient.close())
    client
  }
}


class WebClient(private val context:ExecutionContext,
                val asyncHttpClient: AsyncHttpClient
                 ) extends Extension {


  /**
   * Creates a new get request. Add options to the given builder and then
   * execute the request with [[info.gamlor.web.WebRequestBuilder#execute()]]
   * @param url a valid URL for a HTTP or HTTPS site
   * @return builder to add additional settings
   */
  def prepareGet(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareGet(url)
    new WebRequestBuilder(f,context)
  }
  /**
   * Creates a new post request. Add options to the given builder and then
   * execute the request with [[info.gamlor.web.WebRequestBuilder#execute()]]
   * @param url a valid URL for a HTTP or HTTPS site
   * @return builder to add additional settings
   */
  def preparePost(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.preparePost(url)
    new WebRequestBuilder(f,context)
  }
  /**
   * Creates a new put request. Add options to the given builder and then
   * execute the request with [[info.gamlor.web.WebRequestBuilder#execute()]]
   * @param url a valid URL for a HTTP or HTTPS site
   * @return builder to add additional settings
   */
  def preparePut(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.preparePut(url)
    new WebRequestBuilder(f,context)
  }

  /**
   * Creates a new delete request. Add options to the given builder and then
   * execute the request with [[info.gamlor.web.WebRequestBuilder#execute()]]
   * @param url a valid URL for a HTTP or HTTPS site
   * @return builder to add additional settings
   */
  def prepareDelete(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareDelete(url)
    new WebRequestBuilder(f,context)
  }
  /**
   * Creates a new head request. Add options to the given builder and then
   * execute the request with [[info.gamlor.web.WebRequestBuilder#execute()]]
   * @param url a valid URL for a HTTP or HTTPS site
   * @return builder to add additional settings
   */
  def prepareHead(url: String): WebRequestBuilder = {
    val f = asyncHttpClient.prepareHead(url)
    new WebRequestBuilder(f,context)
  }
}


class WebRequestBuilder(private val unterlyingRequestBuilder: AsyncHttpClient#BoundRequestBuilder,
                      private val context:ExecutionContext) {

  /**
   * Executes this request. The result or error of the completed
   * request is passed to the future.
   * @return future which will complete with the result or an error
   */
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

  /**
   * Same as [[info.gamlor.web.WebRequestBuilder#execute()]] but with additional async handler.
   * The async handler allows you to process the request result while the data is arriving.
   *
   * You also can cancel the request processing at any time by returning a [[com.ning.http.client.AsyncHandler.STATE.ABORT]]
   * @param handler the response handler which will be called
   * @tparam A the type of the resulting data
   * @return a future which will complete with the result or an error
   */
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
