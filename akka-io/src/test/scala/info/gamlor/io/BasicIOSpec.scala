package info.gamlor.io

import akka.dispatch.Await
import akka.util.duration._
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.io.IOException
import java.nio.file.StandardOpenOption
import akka.util.ByteString

/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

class BasicIOSpec extends SpecBase {


  describe("Basic IO") {

    it("allows to read a file") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      val size = file.size()
      size must be (11)

      val allContentFuture = file.read(0,size.toInt);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }
    it("can read bigger file") {
      val file = FileIO.open(TestFiles.inTestFolder("largerTestFile.txt").toString)
      val size = file.size()
      size must be (109847)

      val allContentFuture = file.read(0,size.toInt);

      val content = Await.result(allContentFuture, 5 seconds)
      content.size must be (109847)

      file.close()
    }
    it("can write") {
      val file = FileIO.open(TestFiles.tempFile(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"),0)
        r <- file.read(0,file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }
    it("read from offset") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)

      val allContentFuture = file.read(6,100);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be ("World")

      file.close()
    }
    it("reads only available stuff") {
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)

      val allContentFuture = file.read(0,256);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }
    it("can write bytes directly") {
      val file = FileIO.open(TestFiles.tempFile(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write("Hello World".getBytes("UTF8"),0)
        r <- file.read(0,file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }
    it("write returns byte written") {
      val file = FileIO.open(TestFiles.tempFile(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val f = file.write("Hello World".getBytes("UTF8"),0)

      val content = Await.result(f, 5 seconds)
      content must be (11)

      file.close()
    }
    it("can write into a certain part") {
      val file = FileIO.open(TestFiles.tempFile(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"),0)
        w2 <- file.write(ByteString("Roman"),6)
        r <- file.read(0,file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be ("Hello Roman")

      file.close()
    }
    it("can write into a certain part of empty file") {
      val file = FileIO.open(TestFiles.tempFile(),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(ByteString("Hello World"),12)
        r <- file.read(0,file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be ("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000Hello World")

      file.close()
    }
    it("reports exception on reads") {
      val file = failingChannel()

      val allContentFuture = file.read(0,100);


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be (true)
      content.value.get.left.get.getMessage must be ("Simulated Error")

      file.close()
    }
    it("reports exception on writes") {
      val file = failingChannel()

      val allContentFuture = file.write(ByteString("Hello World"),0);


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be (true)
      content.value.get.left.get.getMessage must be ("Simulated Error")

      file.close()
    }

  }

  private def failingChannel() = {
    val failingChannel = mock(classOf[AsynchronousFileChannel]);
    val failingRequestMethod = new Answer[Unit] {
          def answer(invocation: InvocationOnMock) {
            invocation.getArguments()(3)
              .asInstanceOf[CompletionHandler[Int, Any]]
              .failed(new IOException("Simulated Error"), null)

          }
        };
    when(failingChannel.read(anyObject(),anyObject(),anyObject(),anyObject())).thenAnswer(failingRequestMethod)
    when(failingChannel.write(anyObject(), anyObject(), anyObject(), anyObject())).thenAnswer(failingRequestMethod)
    new FileChannelIO(failingChannel, system.dispatcher)
  }


}
