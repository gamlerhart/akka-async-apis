package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext, Promise}
import scala.collection.JavaConversions._
import scala.math._
import akka.actor.IO
import akka.actor.IO.{IterateeRefSync, IterateeRefAsync, Iteratee}
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import akka.util.ByteString._


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

  def write(startPostion: Long, dataToWrite: ByteString): Future[Unit] = {
    val buffer = dataToWrite.asByteBuffer
    writeBuffer(buffer, startPostion)
  }

  def write(startPostion: Long, dataToWrite: Array[Byte]): Future[Unit] = {
    writeBuffer(ByteBuffer.wrap(dataToWrite), startPostion)
  }


  def readUntilDone[A](parser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[A] = {
    val bytesToRead = if (amountToRead == -1) {
      channel.size()
    } else {
      amountToRead
    }
    val reader = new ContinuesReader(startPos, bytesToRead, parser)
    reader.startReading()
  }

  def close() = channel.close()

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

  class ContinuesReader[A](private var readPosition: Long,
                           private var amountStillToRead: Long,
                           private val parser: Iteratee[A]
                            ) extends CompletionHandler[java.lang.Integer, ContinuesReader[A]] {
    val stepSize = min(32 * 1024, amountStillToRead).toInt;
    private val readBuffer = ByteBuffer.allocate(stepSize)
    private val promiseToComplete: Promise[A] = Promise[A]()
    private val mutableItaree = IO.IterateeRef.sync(parser)

    def startReading() = {
      readBuffer.limit(min(amountStillToRead, stepSize).toInt)
      channel.read(readBuffer, readPosition, this, this)
      promiseToComplete
    }

    private def finishWithEOF() {
      parser(IO.EOF(None))
      promiseToComplete.success(mutableItaree.value._1.get)
    }

    def completed(result: java.lang.Integer, reader: ContinuesReader[A]) {
      assert(this == reader)
      try {
        readBuffer.flip()
        mutableItaree(IO.Chunk(ByteString(reader.readBuffer)))
        readBuffer.flip()
        if (result == reader.stepSize) {
          amountStillToRead = amountStillToRead - result
          readBuffer.limit(min(amountStillToRead, stepSize).toInt)
          if(amountStillToRead>0){
            channel.read(readBuffer, readPosition, this, this);
          }else{
            finishWithEOF()
          }
        } else {
          finishWithEOF()
        }
      } catch {
        case e: Exception => promiseToComplete.failure(e)
      }
    }

    def failed(exc: Throwable, reader: ContinuesReader[A]) {
      promiseToComplete.failure(exc)
    }
  }

}
