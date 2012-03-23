package info.gamlor.io

import org.mockito.Mockito._
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.io.IOException
import org.mockito.Matchers._
import akka.dispatch.ExecutionContext

/**
 * @author roman.stoffel@gamlor.info
 * @since 23.03.12
 */

object FailingTestChannels {
  def failingChannel(context:ExecutionContext) = {
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
    new FileChannelIO(failingChannel, context)
  }

}
