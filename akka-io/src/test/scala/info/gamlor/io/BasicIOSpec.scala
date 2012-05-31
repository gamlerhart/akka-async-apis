package info.gamlor.io

import akka.util.duration._
import java.nio.file.StandardOpenOption
import akka.util.ByteString
import akka.actor.IO
import java.util.concurrent.atomic.AtomicReference
import akka.dispatch.{Promise, Await}
import java.io.IOException

/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

class BasicIOSpec extends SpecBase {


  describe("Basic IO") {

    it("allows to read a file") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      val size = file.size()
      size must be(11)

      val allContentFuture = file.read(0, size.toInt)

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be("Hello World")

      file.close()
    }
    it("can read bigger file") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)
      val size = file.size()
      size must be(109847)

      val allContentFuture = file.read(0, size.toInt);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(109847)

      file.close()
    }
    it("can write") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"), 0)
        r <- file.read(0, file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be("Hello World")

      file.close()
    }
    it("can close future style") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val readStuff = for {
        r <- file.read(0, file.size().toInt)
        c <- file.close()
      } yield r

      val content = Await.result(readStuff, 5 seconds)
      content.utf8String must be("")

      intercept[IOException]{
        file.size()
      }
    }
    it("read from offset") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)

      val allContentFuture = file.read(6, 100);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be("World")

      file.close()
    }
    it("reads only available stuff") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)

      val allContentFuture = file.read(0, 256);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be("Hello World")

      file.close()
    }
    it("can read chunked") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)
      val size = file.size()

      val allContentFuture = file.readChunked[ByteString](0, size.toInt, ByteString.empty)(
        (oldData, newData) => newData match {
          case IO.Chunk(newBytes) => oldData.++(newBytes)
          case IO.EOF(_) => oldData
        }
      )

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be(109847)

      file.close()
    }
    it("can read chunked with side effects") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)
      val size = file.size()

      val mutableReference = new AtomicReference[ByteString](ByteString.empty)
      val done = Promise[ByteString]()
      val allContentFuture = file.readChunked[ByteString](0, size){
          case IO.Chunk(newBytes) => {
            mutableReference.set(mutableReference.get.++(newBytes))
            mutableReference.get
          }
          case IO.EOF(_) => {
            done.success(mutableReference.get)
            mutableReference.get
          }
      }

      val content = Await.result(done, 5 seconds)
      content.size must be(109847)

      file.close()
    }
    it("can write bytes directly") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write("Hello World".getBytes("UTF8"), 0)
        r <- file.read(0, file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be("Hello World")

      file.close()
    }
    it("write returns byte written") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val f = file.write("Hello World".getBytes("UTF8"), 0)

      val content = Await.result(f, 5 seconds)
      content must be(11)

      file.close()
    }
    it("can write into a certain part") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"), 0)
        w2 <- file.write(ByteString("Roman"), 6)
        r <- file.read(0, file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be("Hello Roman")

      file.close()
    }
    it("can write into a certain part of empty file") {
      val file = FileIO.open(TestFiles.tempFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"), 12)
        r <- file.read(0, file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000Hello World")

      file.close()
    }
    it("reports exception on reads") {
      val file = FailingTestChannels.failingChannel(system.dispatcher)

      val allContentFuture = file.read(0, 100);


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be(true)
      content.value.get.left.get.getMessage must be("Simulated Error")

      file.close()
    }
    it("reports exception on writes") {
      val file = FailingTestChannels.failingChannel(system.dispatcher)

      val allContentFuture = file.write(ByteString("Hello World"), 0);


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be(true)
      content.value.get.left.get.getMessage must be("Simulated Error")

      file.close()
    }

  }


}
