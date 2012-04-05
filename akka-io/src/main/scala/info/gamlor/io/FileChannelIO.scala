package info.gamlor.io

import scala.math._
import java.nio.ByteBuffer
import akka.dispatch.{Promise, Future, ExecutionContext}
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import akka.actor.IO
import akka.util.ByteString
import java.io.IOException

class FileChannelIO(val channel: AsynchronousFileChannel,
                    private implicit val context: ExecutionContext) extends AccumulationReadingBase {

  /**
   * @see [[java.nio.channels.AsynchronousFileChannel# s i z e]]
   */
  def size() = channel.size()

  /**
   * @see [[java.nio.channels.AsynchronousFileChannel# f o r c e]]
   */
  def force(metaData: Boolean = true) = channel.force(metaData)


  /**
   * Closes this file and the underlying channel immediately
   *
   * Any outstanding asynchronous operations upon this channel will complete with the exception AsynchronousCloseException.
   * After a channel is closed, further attempts to initiate asynchronous I/O operations complete immediately with cause ClosedChannelException.
   *
   * It returns a future so that you can easily use it in a for comprehension:
   * <pre>
   * val readStuff = for {
   *    r <- file.read(0, file.size().toInt)
   *    c <- file.close()
   * } yield r
   *
   * If a IOException happens during the close operation it will be contained in the future.
   * </pre>
   */
  def close() :Future[Unit] = {
    try{
      channel.close()
      Promise.successful[Unit]()
    } catch {
      case ex:IOException =>Promise.failed[Unit](ex)
    }
  }


  protected def readAndAccumulate[A](parser: Accumulator[A], startPos: Long = 0, amountToRead: Long = -1) = {
    val bytesToRead = if (amountToRead == -1) {
      channel.size()
    } else {
      amountToRead
    }
    val reader = new ContinuesReader(startPos, bytesToRead, parser)
    reader.startReading()
  }

  def write(writeBuffer: ByteBuffer, startPostion: Long): Future[Int] = {
    val promise = Promise[Int]
    channel.write(writeBuffer, startPostion, null, new CompletionHandler[java.lang.Integer, Any] {
      def completed(result: java.lang.Integer, attachment: Any) {
        promise.success(result)
      }

      def failed(exc: Throwable, attachment: Any) {
        promise.failure(exc)
      }
    })
    promise
  }

  class ContinuesReader[A](private var readPosition: Long,
                           private var amountStillToRead: Long,
                           private val resultAccumulator: Accumulator[A]
                            ) extends CompletionHandler[java.lang.Integer, ContinuesReader[A]] {
    val stepSize = min(64 * 1024, amountStillToRead).toInt;
    private val readBuffer = ByteBuffer.allocate(stepSize)
    private val promiseToComplete: Promise[A] = Promise[A]()

    def startReading() = {
      readBuffer.limit(min(amountStillToRead, stepSize).toInt)
      channel.read(readBuffer, readPosition, this, this)
      promiseToComplete
    }

    def completed(result: java.lang.Integer, reader: ContinuesReader[A]) {
      assert(this == reader)
      try {
        readBuffer.flip()
        val readBytes: ByteString = ByteString(reader.readBuffer)
        val continue = resultAccumulator(IO.Chunk(readBytes))
        readBuffer.flip()
        if (result == reader.stepSize && continue) {
          amountStillToRead = amountStillToRead - result
          readPosition = readPosition + result
          readBuffer.limit(min(amountStillToRead, stepSize).toInt)
          if (amountStillToRead > 0) {
            channel.read(readBuffer, readPosition, this, this);
          } else {
            finishWithEOF()
          }
        } else {
          finishWithEOF()
        }
      } catch {
        case e: Exception => promiseToComplete.failure(e)
      }
    }

    private def finishWithEOF() {
      resultAccumulator(IO.EOF(None))
      promiseToComplete.success(resultAccumulator.finishedValue())
    }

    def failed(exc: Throwable, reader: ContinuesReader[A]) {
      promiseToComplete.failure(exc)
    }
  }

}
