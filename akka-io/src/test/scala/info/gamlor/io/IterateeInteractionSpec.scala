package info.gamlor.io

import akka.dispatch.Await
import akka.util.ByteString
import akka.actor.IO
import akka.util.duration._
import akka.actor.IO.Done

/**
 * @author roman.stoffel@gamlor.info
 * @since 02.03.12
 */

class IterateeInteractionSpec extends SpecBase {


  describe("FileIO and Iteratees") {
    it("can read util EOF") {
      val file = FileReader.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readUntilDone(AllWords);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()

    }
    it("can read accross buffer size") {

    }
  }


  private val Space = ByteString(" ")

  //  private val separatedBySpace() = for{
  //    word <- IO takeUntil Space
  //  } yield word.utf8String

  def separatedBySpace = {
    def step(found: List[String]): IO.Iteratee[List[String]] = {
      val oneWord = for {
        word <- IO.takeUntil(Space)
      } yield word.utf8String

      oneWord.flatMap(s => {
        if (s.isEmpty) {
          Done(found)
        } else {
          step(List(s).:::(found))
        }
      })
    }
    step(Nil)
  }

  private val AllWords = for {
    allWords <- separatedBySpace
  } yield allWords

}
