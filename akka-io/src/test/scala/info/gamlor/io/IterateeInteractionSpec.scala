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
  val WordsInTestFile =2000
  val WordsToStopWordIncluding =6

  private val Space = ByteString(" ")


  describe("FileIO and Iteratees") {
    it("can read util EOF") {
      val file = FileReader.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()

    }
    it("can read accross buffer size") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsInTestFile)
      content(0) must be("StartWord")
      content(WordsInTestFile-1) must be("FinalWord")

      file.close()

    }
    it("cancels processing when done") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpaceWithStopWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsToStopWordIncluding)
      content(0) must be("StartWord")
      content(WordsToStopWordIncluding-1) must be("StopWord")

      file.close()
    }
    it("can start somewhere in the file") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace,230,file.size());

      val content = Await.result(allContentFuture, 5 seconds)
//      content.size must be(WordsInTestFile-WordsToStopWordIncluding+1)
      content(0) must be("StopWord")
      content.last must be("FinalWord")

      file.close()
    }
    it("can read section") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace,230,8);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(1)
      content(0) must be("StopWord")
      content.last must be("StopWord")

      file.close()
    }
    it("can read in segments") {
      val file = FileReader.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readSegments(parseSingleWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()
    }
    it("can read in segments WordsIntTestFilewith larger file") {
      val file = FileReader.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readSegments(parseSingleWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsInTestFile)
      content(0) must be("StartWord")
      content(WordsInTestFile-1) must be("FinalWord")

      file.close()
    }
    it("cancels segments processing when done") {
      val file = FileReader.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readSegments(for{
        first <- IO.takeUntil(Space)
        second <- IO.takeUntil(Space)
        third <- IO.takeUntil(Space)
      } yield first.utf8String+second.utf8String+third.utf8String);

      val content = Await.result(allContentFuture, 5 seconds)
      content(0) must be("Helloeverybodyin")

      file.close()
    }
  }


  //  private val separatedBySpace() = for{
  //    word <- IO takeUntil Space
  //  } yield word.utf8String

  def separatedBySpace = {
    def step(found: List[String]): IO.Iteratee[List[String]] = {

      parseSingleWord.flatMap(s => {
        if (s.isEmpty) {
          Done(found)
        } else {
          step(List(s).:::(found))
        }
      })
    }
    step(Nil)
  }

  def parseSingleWord = for {
    word <- IO.takeUntil(Space)
  } yield word.utf8String

  def separatedBySpaceWithStopWord = {
    def step(found: List[String]): IO.Iteratee[List[String]] = {

      parseSingleWord.flatMap(s => {
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
