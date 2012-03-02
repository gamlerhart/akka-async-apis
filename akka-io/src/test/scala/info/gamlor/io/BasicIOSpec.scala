package info.gamlor.io

import org.scalatest.matchers.MustMatchers
import org.scalatest.Spec
import akka.dispatch.Await
import akka.util.duration._
import akka.testkit.TestKit
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

class BasicIOSpec extends TestKit(TestActorSystem.DefaultSystem) with Spec with MustMatchers {

  def failingChannel() = {
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
    new FileReader(failingChannel, system.dispatcher)
  }

  describe("Basic IO") {

    it("allows to read a file") {
      val file = FileReader.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      val size = file.size()
      size must be (11)

      val allContentFuture = file.read(0,size.toInt);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }
    it("can write") {
      val file = FileReader.open(TestFiles.tempFile().toString,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.READ)

      val writtenStuff = for {
        w <- file.write(0,ByteString("Hello World"))
        r <- file.read(0,file.size().toInt)
      } yield r

      val content = Await.result(writtenStuff, 5 seconds)
      content.utf8String must be ("Hello World")

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

      val allContentFuture = file.write(0,ByteString("Hello World"));


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be (true)
      content.value.get.left.get.getMessage must be ("Simulated Error")

      file.close()
    }

  }


}
