package info.gamlor.io

import akka.dispatch.Await
import akka.util.ByteString
import akka.actor.IO
import akka.util.duration._
import akka.actor.IO.Done
import org.mockito.Mockito._
import org.mockito.Matchers._
import java.nio.channels.AsynchronousFileChannel
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock

/**
 * @author roman.stoffel@gamlor.info
 * @since 02.03.12
 */

class IterateeInteractionSpec extends SpecBase {
  val WordsInTestFile = 2000
  val WordsToStopWordIncluding = 6

  private val Space = ByteString(" ")


  describe("FileIO and Iteratees") {
    it("can read util EOF") {
      val file = FileIO.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()

    }
    it("can read accross buffer size") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsInTestFile)
      content(0) must be("StartWord")
      content(WordsInTestFile - 1) must be("FinalWord")

      file.close()

    }
    it("cancels processing when done") {
      val readFile = AsynchronousFileChannel.open(TestFiles.inTestFolder("largerTestFile.txt"))
      val interactionRecorder = mock(classOf[AsynchronousFileChannel], withSettings().defaultAnswer(new Answer[Object] {
        def answer(invocation: InvocationOnMock) = {
          invocation.getMethod.invoke(readFile, invocation.getArguments: _*)
        }
      }))

      val fileIO = new FileIO(interactionRecorder, system.dispatcher)

      val allContentFuture = fileIO.readAll(separatedBySpaceWithStopWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsToStopWordIncluding)
      content(0) must be("StartWord")
      content(WordsToStopWordIncluding - 1) must be("StopWord")

      fileIO.close()

      verify(interactionRecorder, atMost(1)).read(anyObject(), anyObject(), anyObject(), anyObject())

    }
    it("returns empty result if ends before success") {
      val file = FileIO.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readAll(readToStopWorld);

      val content = Await.result(allContentFuture, 5 seconds)
      content must not be(null)

      file.close()

    }
    it("can start somewhere in the file") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace, 230, file.size());

      val content = Await.result(allContentFuture, 5 seconds)
      //      content.size must be(WordsInTestFile-WordsToStopWordIncluding+1)
      content(0) must be("StopWord")
      content.last must be("FinalWord")

      file.close()
    }
    it("can read section") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readAll(separatedBySpace, 230, 8);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(1)
      content(0) must be("StopWord")
      content.last must be("StopWord")

      file.close()
    }
    it("can read in segments") {
      val file = FileIO.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readSegments(parseSingleWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content must be(List("Hello", "everybody", "in", "this", "room"))

      file.close()
    }
    it("can read in segments WordsIntTestFilewith larger file") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)

      val allContentFuture = file.readSegments(parseSingleWord);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(WordsInTestFile)
      content(0) must be("StartWord")
      content(WordsInTestFile - 1) must be("FinalWord")

      file.close()
    }
    it("cancels segments processing when done") {
      val file = FileIO.open(TestFiles.inTestFolder("multipleWords.txt").toString)

      val allContentFuture = file.readSegments(for {
        first <- IO.takeUntil(Space)
        second <- IO.takeUntil(Space)
        third <- IO.takeUntil(Space)
      } yield first.utf8String + second.utf8String + third.utf8String);

      val content = Await.result(allContentFuture, 5 seconds)
      content(0) must be("Helloeverybodyin")

      file.close()
    }
  }


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

  private def readToStopWorld = for {
    f <- readStopToken
    space <- IO.take(1)
    s <- readStopToken
  } yield TwoTokens(f,s)

  private def readStopToken = for {
    word <- IO.takeUntil(ByteString("StopWord"))
  } yield StopToken(word.utf8String)



  case class StopToken(text:String)
  case class TwoTokens(first:StopToken,second:StopToken)

  def separatedBySpaceWithStopWord = {
    def step(found: List[String]): IO.Iteratee[List[String]] = {

      parseSingleWord.flatMap(s => {
        if (s.isEmpty) {
          Done(found)
        } else if (s == "StopWord") {
          Done(List(s).:::(found))
        } else {
          step(List(s).:::(found))
        }
      })
    }
    step(Nil)
  }

}
