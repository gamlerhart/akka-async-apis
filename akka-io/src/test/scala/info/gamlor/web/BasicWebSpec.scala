package info.gamlor.web

import akka.testkit.TestKit
import info.gamlor.io.TestActorSystem
import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers
import akka.util.duration._
import java.util.concurrent.atomic.AtomicBoolean
import com.ning.http.client.AsyncHandler.STATE
import java.util.concurrent.CancellationException
import akka.dispatch.Await
import com.ning.http.client._

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

class BasicWebSpec extends TestKit(TestActorSystem.DefaultSystem) with Spec with MustMatchers {

  describe("Web client IO") {

    it("can get stuff") {
      TestWebServer.withTestServer(TestWebServer.HelloWorld,
        server => {
          val future = WebClient(system).prepareGet(server.url).execute()

          val result = Await.result(future, 5 seconds)

          result.getResponseBody must not be ("Hello World")
        })
    }
    it("can post stuff") {
      roundTripWith(url=>WebClient(system).preparePost(url))
    }
    it("can put stuff") {
      roundTripWith(url=>WebClient(system).preparePut(url))
    }
    it("can delete stuff") {
      roundTripWith(url=>WebClient(system).prepareDelete(url))
    }
    it("has events") {
      TestWebServer.withTestServer(TestWebServer.EchoServer,
        server => {
          val onCompletedCalled = new AtomicBoolean(false)
          val onStatusReceivedCalled = new AtomicBoolean(false)
          val onBodyPartReceivedCalled = new AtomicBoolean(false)
          val onHeadersReceivedCalled = new AtomicBoolean(false)
          val future = WebClient(system).preparePost(server.url)
            .setBody("This is the data we post")
            .execute(new AsyncHandler[String] {
            def onCompleted() = {
              onCompletedCalled.set(true)
              "final result"
            }

            def onStatusReceived(responseStatus: HttpResponseStatus) = {
              onStatusReceivedCalled.set(true)
              STATE.CONTINUE;
            }

            def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
              onBodyPartReceivedCalled.set(true)
              STATE.CONTINUE;
            }

            def onThrowable(t: Throwable) {}

            def onHeadersReceived(headers: HttpResponseHeaders) = {
              onHeadersReceivedCalled.set(true)
              STATE.CONTINUE;
            }
          })

          val result = Await.result(future, 5 seconds)
          onCompletedCalled.get() must be(true)
          onStatusReceivedCalled.get() must be(true)
          onBodyPartReceivedCalled.get() must be(true)
          onHeadersReceivedCalled.get() must be(true)
          result must be("final result")
        })
    }
    it("can cancel processing") {
      TestWebServer.withTestServer(TestWebServer.EchoServer,
        server => {
          val result = WebClient(system).preparePost(server.url)
            .setBody("This is the data we post")
            .execute(new AsyncHandler[String] {
            def onCompleted() = {
              "final result"
            }

            def onStatusReceived(responseStatus: HttpResponseStatus) = {
              STATE.ABORT;
            }

            def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
              STATE.CONTINUE;
            }

            def onThrowable(t: Throwable) {}

            def onHeadersReceived(headers: HttpResponseHeaders) = {
              STATE.CONTINUE;
            }
          })

          val content = Await.ready(result, 5 seconds)
          content.value.get.isLeft must be(true)
          content.value.get.left.get.isInstanceOf[CancellationException] must be(true)
        })

    }
    it("report failure") {
      TestWebServer.withTestServerExtended(TestWebServer.FailCompletly,
        server => {
          val result = WebClient(system).prepareGet(server.url).execute()


          val content = Await.ready(result, 5 seconds)
          content.value.get.isLeft must be(true)
          content.value.get.left.get.getMessage must not be (null)
        })
    }

  }
  private def roundTripWith(methodToUse:String => WebRequestBuilder) {
    TestWebServer.withTestServer(TestWebServer.EchoServer,
      server => {
        val future = methodToUse(server.url)
          .setBody("This is the data we post").execute()

        val result = Await.result(future, 5 seconds)

        result.getResponseBody must not be ("This is the data we post")
      })
  }
}
