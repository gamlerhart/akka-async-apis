package info.gamlor.io

import akka.dispatch.{Await, Future}
import akka.util.duration._


/**
 * @author roman.stoffel@gamlor.info
 * @since 15.03.12
 */

class TextOperationsSpec extends SpecBase {
  describe("Text IO") {

    it("allows to read a file") {
      val txt = FileIO.openText(TestFiles.inTestFolder("helloWorld.txt"))
      val contentFuture: Future[String] = txt.readWholeFile()

      val content = Await.result(contentFuture, 5 seconds)
      content must be ("Hello World")
    }

  }

}
