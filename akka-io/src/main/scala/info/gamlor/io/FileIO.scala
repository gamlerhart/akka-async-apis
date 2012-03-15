package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext, Promise}
import scala.collection.JavaConversions._
import scala.math._
import collection.mutable.Buffer
import akka.actor.IO.{Iteratee, Input}
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import akka.actor.IO


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object FileIO {
  private val defaultOpenOptions: java.util.Set[OpenOption] = java.util.Collections.singleton(StandardOpenOption.READ)

  def open(fileName: String)(implicit context: ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName), defaultOpenOptions, new DelegateToContext(context))
    new FileIO(fileChannel, context)
  }

  def open(fileName: String, openOptions: OpenOption*)(implicit context: ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName), openOptions.toSet, new DelegateToContext(context))
    new FileIO(fileChannel, context)
  }


}

class FileIO(val channel: AsynchronousFileChannel, private implicit val context: ExecutionContext) {


  /**
   * @see [[java.nio.channels.AsynchronousFileChannel#size]]
   */
  def size() = channel.size()

  /**
   * @see [[java.nio.channels.AsynchronousFileChannel#force]]
   */
  def force(metaData:Boolean = true) = channel.force(metaData)


  /**
   * Reads a sequence of bytes from this file, starting at the given file position. It will allocate
   * a buffer which can hold the requested data. Finally it returns the read data as a byte string.
   * @param startPoint start point of the read operation, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @return future which will complete with the read data or exception.
   */
  def read(startPoint: Long, amountToRead: Int): Future[ByteString] = {
    val readBuffer = ByteBuffer.allocate(amountToRead)
    val promise = Promise[ByteString]
    channel.read[Any](readBuffer, startPoint, null, new CompletionHandler[java.lang.Integer, Any] {
      def completed(result: java.lang.Integer, attachment: Any) {
        readBuffer.flip()
        promise.success(ByteString(readBuffer))
      }

      def failed(exc: Throwable, attachment: Any) {
        promise.failure(exc)
      }
    })
    promise
  }

  /**
   * Reads a sequence of bytes and passes those bytes to the given iteratee.
   * It will stop when the iteratee signals that it is done with [[akka.actor.IO.Done]] and then return the result of that iteraree.
   * Or it will stop when the amount bytes to read is reached. It does not return the 'left' over bytes not processed by the iteraree.
   *
   * @param parser the iteratee which is used to process the incoming data
   * @param startPos start point of the read operation, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes.
   * @tparam A the parsed result type
   * @return future which will complete with the read data or exception.
   */
  def readAll[A](parser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[A]
  = readWithIteraree(Accumulators.parseWhole(parser), startPos, amountToRead)

  /**
   * Reads a sequence of bytes and passes those bytes to the given iteratee. Evertime the iteraree signals
   * that it is finished that result is added to the result sequence. The the processing is started over with the left
   * over bytes of the file. This allows to parse the same repeated structure of a file.
   * The parsing stops when the end of the file or the read limit is reached.
   * @param segmentParser the iteratee which is used to process the incoming data
   * @param startPos start point of the read, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes.
   * @tparam A the parsed result type
   * @return future which will complete with the read data or exception.
   */
  def readSegments[A](segmentParser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[Seq[A]]
  = readWithIteraree(Accumulators.parseSegments(segmentParser), startPos, amountToRead)

  /**
   * Writes a sequence of bytes to this file, starting at the given file position.
   *
   * If the given position is greater than the file's size, at the time that the write is attempted,
   * then the file will be grown to accommodate the new bytes;
   * the values of any bytes between the previous end-of-file and the newly-written bytes are unspecified.
   * @param startPostion The file position at which the transfer is to begin; must be non-negative
   * @param dataToWrite Data to write
   * @return Unit future which signals the completion or errors
   */
  def write(startPostion: Long, dataToWrite: ByteString): Future[Unit] = {
    val buffer = dataToWrite.asByteBuffer
    writeBuffer(buffer, startPostion)
  }
  /**
     * Writes a sequence of bytes to this file, starting at the given file position.
     *
     * If the given position is greater than the file's size, at the time that the write is attempted,
     * then the file will be grown to accommodate the new bytes;
     * the values of any bytes between the previous end-of-file and the newly-written bytes are unspecified.
     * @param startPostion The file position at which the transfer is to begin; must be non-negative
     * @param dataToWrite Data to write
     * @return Unit future which signals the completion or errors
     */
  def write(startPostion: Long, dataToWrite: Array[Byte]): Future[Unit] = {
    writeBuffer(ByteBuffer.wrap(dataToWrite), startPostion)
  }

  /**
   * Closes this file and the underlying channel.
   *
   * Any outstanding asynchronous operations upon this channel will complete with the exception AsynchronousCloseException.
   * After a channel is closed, further attempts to initiate asynchronous I/O operations complete immediately with cause ClosedChannelException.
   */
  def close() = channel.close()


  private def readWithIteraree[A](parser: Accumulator[A], startPos: Long = 0, amountToRead: Long = -1) = {
    val bytesToRead = if (amountToRead == -1) {
      channel.size()
    } else {
      amountToRead
    }
    val reader = new ContinuesReader(startPos, bytesToRead, parser)
    reader.startReading()
  }

  private def writeBuffer(writeBuffer: ByteBuffer, startPostion: Long): Promise[Unit] = {
    val promise = Promise[Unit]
    channel.write(writeBuffer, startPostion, null, new CompletionHandler[java.lang.Integer, Any] {
      def completed(result: java.lang.Integer, attachment: Any) {
        promise.success()
      }

      def failed(exc: Throwable, attachment: Any) {
        promise.failure(exc)
      }
    })
    promise
  }


  trait Accumulator[A] {
    def apply(input: IO.Input):Boolean

    def finishedValue(): A
  }

  object Accumulators {

    def parseWhole[A](parser: Iteratee[A]) = new Accumulator[A] {
      private val mutableItaree = IO.IterateeRef.sync(parser)

      def apply(input: Input):Boolean = {
        mutableItaree(input)
        val isDone = mutableItaree.value._1.isInstanceOf[IO.Done[A]]
        return !isDone
      }

      def finishedValue(): A = mutableItaree.value._1.get
    }

    def parseSegments[A](parser: Iteratee[A]) = new Accumulator[Seq[A]] {
      private val buffer = Buffer[A]();
      private var currentIteratee : Iteratee[A] = parser;

      def apply(input: Input):Boolean = {
        if(input.isInstanceOf[IO.EOF]){
          buffer.add(currentIteratee(input)._1.get);
        } else{
          var (parsedValue, rest) = currentIteratee(input)
          while(parsedValue.isInstanceOf[IO.Done[A]]){
            buffer.add(parsedValue.get)
            var (newParsedValue, newRest) = parser(rest)
            parsedValue = newParsedValue
            rest = newRest
          }
          currentIteratee = parsedValue
        }
        true

      }

      def finishedValue(): Seq[A] = buffer.toSeq
    }
  }


  class ContinuesReader[A](private var readPosition: Long,
                           private var amountStillToRead: Long,
                           private val resultAccumulator: Accumulator[A]
                            ) extends CompletionHandler[java.lang.Integer, ContinuesReader[A]] {
    val stepSize = min(32 * 1024, amountStillToRead).toInt;
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
