package info.gamlor.web

import akka.testkit.TestKit
import info.gamlor.io.TestActorSystem
import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers
import akka.dispatch.Await
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

class BasicWebSpec extends TestKit(TestActorSystem.DefaultSystem) with Spec with MustMatchers {
  describe("Web client IO") {

    it("can get stuff"){

      TestWebServer.withTestServer(TestWebServer.HelloWorld,
         server=>{
           val future = WebClient(system).prepareGet(server.url).execute()

           val result = Await.result(future, 5 seconds)

           result.getResponseBody must not be("Hello World")
         })
    }

  }
}
