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

      val allContentFuture = file.readUntilDone(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()

    }
    it("can read accross buffer size") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readUntilDone(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(2000)
      content(0) must be("StartWord")
      content(2000-1) must be("FinalWord")

      file.close()

    }
    it("cancels processing when done") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readUntilDone(separatedBySpaceWithStopWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(6)
      content(0) must be("StartWord")
      content(6-1) must be("StopWord")

      file.close()

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
  def separatedBySpaceWithStopWord = {
    def step(found: List[String]): IO.Iteratee[List[String]] = {
      val oneWord = for {
        word <- IO.takeUntil(Space)
      } yield word.utf8String

      oneWord.flatMap(s => {
        if (s.isEmpty) {
          Done(found)
        }else if (s == "StopWord") {
          Done(List(s).:::(found))
        } else {
          step(List(s).:::(found))
        }
      })
    }
    step(Nil)
  }

}
