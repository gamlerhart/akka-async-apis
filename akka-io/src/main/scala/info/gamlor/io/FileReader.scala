package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext, Promise}
import scala.collection.JavaConversions._
import scala.math._
import akka.actor.IO
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import collection.mutable.Buffer
import akka.actor.IO.{Iteratee, Input}


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object FileReader {
  private val defaultOpenOptions: java.util.Set[OpenOption] = java.util.Collections.singleton(StandardOpenOption.READ)

  def open(fileName: String)(implicit context: ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName), defaultOpenOptions, new DelegateToContext(context))
    new FileReader(fileChannel, context)
  }

  def open(fileName: String, openOptions: OpenOption*)(implicit context: ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName), openOptions.toSet, new DelegateToContext(context))
    new FileReader(fileChannel, context)
  }


}

class FileReader(val channel: AsynchronousFileChannel, private implicit val context: ExecutionContext) {


  def size() = channel.size()

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

  def readAll[A](parser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[A]
  = readWithIteraree(Accumulators.parseWhole(parser), startPos, amountToRead)

  def readSegments[A](segmentParser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[Seq[A]]
  = readWithIteraree(Accumulators.parseSegments(segmentParser), startPos, amountToRead)


  def write(startPostion: Long, dataToWrite: ByteString): Future[Unit] = {
    val buffer = dataToWrite.asByteBuffer
    writeBuffer(buffer, startPostion)
  }

  def write(startPostion: Long, dataToWrite: Array[Byte]): Future[Unit] = {
    writeBuffer(ByteBuffer.wrap(dataToWrite), startPostion)
  }


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
    def apply(input: IO.Input)

    def finishedValue(): A
  }

  object Accumulators {

    def parseWhole[A](parser: Iteratee[A]) = new Accumulator[A] {
      private val mutableItaree = IO.IterateeRef.sync(parser)

      def apply(input: Input) = mutableItaree(input)

      def finishedValue(): A = mutableItaree.value._1.get
    }

    def parseSegments[A](parser: Iteratee[A]) = new Accumulator[Seq[A]] {
      private val buffer = Buffer[A]();
      private var currentIteratee : Iteratee[A] = parser;

      def apply(input: Input) {
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
        resultAccumulator(IO.Chunk(readBytes))
        readBuffer.flip()
        if (result == reader.stepSize) {
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
