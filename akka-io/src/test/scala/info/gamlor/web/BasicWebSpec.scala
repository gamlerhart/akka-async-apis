package info.gamlor.web

import akka.testkit.TestKit
import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers
import akka.util.duration._
import java.util.concurrent.atomic.AtomicBoolean
import com.ning.http.client.AsyncHandler.STATE
import akka.dispatch.Await
import com.ning.http.client._
import info.gamlor.io.{TestFiles, TestActorSystem}
import java.util.concurrent.{TimeoutException, CancellationException}

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

class BasicWebSpec extends TestKit(TestActorSystem.DefaultSystem) with Spec with MustMatchers {

  describe("Web client IO") {

    it("can get stuff") {
      roundTripHeadersOnly(url=>WebClient(system).prepareGet(url))
    }
    it("can post stuff") {
      roundTripWithBody(url=>WebClient(system).preparePost(url))
    }
    it("can put stuff") {
      roundTripWithBody(url=>WebClient(system).preparePut(url))
    }
    it("can delete stuff") {
      roundTripWithBody(url=>WebClient(system).prepareDelete(url))
    }
    it("heads up") {
      TestWebServer.withTestServer(TestWebServer.EchoServer,
        server => {
          val future = WebClient(system).prepareHead(server.url).execute()

          val result = Await.result(future, 5 seconds)
          server.amountOfProcessedRequests must be (1)
        })
    }
    it("can post file") {
      TestWebServer.withTestServer(TestWebServer.EchoServer,
        server => {
          val future = WebClient(system).preparePost(server.url)
            .setBody(TestFiles.inTestFolder("helloWorld.txt")).execute()

          val result = Await.result(future, 5 seconds)

          result.getResponseBody must be ("Hello World")
        })
    }
    it("can timeout quickly") {
      TestWebServer.withTestServer(TestWebServer.VerySlowServer,
        server => {
          val requestConfig = new PerRequestConfig();
          requestConfig.setRequestTimeoutInMs(1000);
          val future = WebClient(system).prepareGet(server.url)
            .setPerRequestConfig(requestConfig).execute()


          val content = Await.ready(future, 5 seconds)
          content.value.get.isLeft must be(true)
          content.value.get.left.get.isInstanceOf[TimeoutException] must be(true)
        })
    }
    it("resolves redirects") {
      TestWebServer.withTestServerExtended(TestWebServer.Redirect,
        server => {
          var future = WebClient(system).prepareGet(server.url).execute()

          val result = Await.result(future, 500 seconds)


          result.getResponseBody must be ("Hello World")
        })
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
  private def roundTripWithBody(methodToUse:String => WebRequestBuilder) {
    TestWebServer.withTestServer(TestWebServer.EchoServer,
      server => {
        val future = methodToUse(server.url)
          .setBody("This is the data we post").execute()

        val result = Await.result(future, 5 seconds)

        result.getResponseBody must be ("This is the data we post")
      })
  }
  private def roundTripHeadersOnly(methodToUse:String => WebRequestBuilder) {
    TestWebServer.withTestServer(TestWebServer.HelloWorld,
      server => {
        val future = methodToUse(server.url).execute()


        val result = Await.result(future, 5 seconds)

        result.getResponseBody must be ("Hello World")
      })
  }
}
