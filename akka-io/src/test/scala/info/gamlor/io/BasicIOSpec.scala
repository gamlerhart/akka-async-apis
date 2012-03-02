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

/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

class BasicIOSpec extends TestKit(TestActorSystem.DefaultSystem) with Spec with MustMatchers {

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
    it("reports exception") {
      val failingChannel = mock(classOf[AsynchronousFileChannel]);
      when(failingChannel.read(anyObject(),anyObject(),anyObject(),anyObject())).thenAnswer(new Answer[Unit] {
        def answer(invocation: InvocationOnMock) {
          invocation.getArguments()(3)
            .asInstanceOf[CompletionHandler[Int,Any]]
            .failed(new IOException("Simulated Error"),null)

        }
      })
      val file = new FileReader(failingChannel,system.dispatcher)

      val allContentFuture = file.read(0,100);


      val content = Await.ready(allContentFuture, 5 seconds)
      content.value.get.isLeft must be (true)
      content.value.get.left.get.getMessage must be ("Simulated Error")

      file.close()
    }
    it("can set flags") {
      val file = FileReader.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      val size = file.size()
      size must be (11)

      val allContentFuture = file.read(0,size.toInt);

      val content = Await.result(allContentFuture, 5 seconds)
      content.utf8String must be ("Hello World")

      file.close()
    }

  }


}
