package info.gamlor.io

import akka.dispatch.{Await, Future}
import akka.util.duration._
import java.nio.file.StandardOpenOption._


/**
 * @author roman.stoffel@gamlor.info
 * @since 15.03.12
 */

class TextOperationsSpec extends SpecBase {
  describe("Reading Text IO") {

    it("allows to read a file") {
      val txt = FileIO.openText(TestFiles.inTestFolder("helloWorld.txt"))
      val contentFuture: Future[String] = txt.readWholeFile()

      val content = Await.result(contentFuture, 5 seconds)
      content must be ("Hello World")
    }

    it("allows to a larger file") {
      val txt = FileIO.openText(TestFiles.inTestFolder("largerTestFile.txt"))
      val contentFuture = txt.readWholeFile()

      val content = Await.result(contentFuture, 5 seconds)
      content.length must be (109847)
    }
    it("can read by line") {
      val txt = FileIO.openText(TestFiles.inTestFolder("aFewLines.txt"))
      val contentFuture = txt.readAllLines()

      val content = Await.result(contentFuture, 5 seconds)
      content must be (Seq("Line 1","Line 2","Line 3","Line 4"))
    }
    it("can read with delemiter") {
      val txt = FileIO.openText(TestFiles.inTestFolder("multipleWords.txt"))
      val contentFuture = txt.readSplitBy(" ")

      val content = Await.result(contentFuture, 5 seconds)
      content must be (Seq("Hello","everybody","in","this","room"))
    }
    it("can read with multipe delemiters") {
      val txt = FileIO.openText(TestFiles.inTestFolder("delimiters.txt"))
      val contentFuture = txt.readSplitBy(" ",":",".","-")

      val content = Await.result(contentFuture, 5 seconds)
      content must be (Seq("this","is","split","with","different","tokens"))
    }
    it("can read with encoding") {
      val txt = FileIO.openText(TestFiles.inTestFolder("utf16encoding.txt"), encoding="UTF-16")
      val contentFuture: Future[String] = txt.readWholeFile()

      val content = Await.result(contentFuture, 5 seconds)
      content must be ("This is in UFT16")
    }
  }

  describe("Wrting Text IO") {

    it("can write to a file") {
      val txt = FileIO.openText(TestFiles.tempFile(), encoding="UTF-16", openOptions = Set(READ,WRITE))

      val fileAfterWrite = for{
        write <- txt.appendToEnd("Some new Data Here")
        read <- txt.readWholeFile()
      } yield read

      val readText = Await.result(fileAfterWrite,5 seconds)

      readText must be("Some new Data Here")
    }
    it("can append to a file") {
      val txt = FileIO.openText(TestFiles.tempFile(), openOptions = Set(READ,WRITE))

      val fileAfterWrite = for{
        w1 <- txt.appendToEnd("Some new Data Here")
        w2 <- txt.appendToEnd(" More Data")
        read <- txt.readWholeFile()
      } yield read

      val readText = Await.result(fileAfterWrite,5 seconds)

      readText must be("Some new Data Here More Data")
    }

  }

}
