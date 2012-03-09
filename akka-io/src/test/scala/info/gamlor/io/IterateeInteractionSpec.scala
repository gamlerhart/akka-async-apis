package info.gamlor.io

import akka.dispatch.Await
import akka.util.ByteString
import akka.actor.IO
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 02.03.12
 */

class IterateeInteractionSpec extends SpecBase {


  describe("FileIO and Iteratees") {

    it("allows to read whole file") {
      val file = FileReader.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readAll(SeparatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(Seq("Hello"," everybody","in","this","room"))

      file.close()
    }
  }


  private val Space = ByteString(" ")
  private val SeparatedBySpace = for{
    word <- IO takeUntil Space
  } yield word.utf8String

}
