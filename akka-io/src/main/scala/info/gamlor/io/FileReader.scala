package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext, Promise}
import scala.collection.JavaConversions._
import collection.mutable.ArrayBuffer
import akka.actor.IO
import akka.actor.IO.{Input, Iteratee}


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

  def readAll[A](parser: Iteratee[A]): Future[A] = {
    val stepSize = 32 * 1024;
    val readBuffer = ByteBuffer.allocate(stepSize)
    val state = ReadState[A](0, stepSize)
    val processor =
      IO.repeat {
        for {
          parcedPiece ← parser
        } yield {
          state.resultBuffer.add(parcedPiece)
          ()
        }
      }
    val promise = Promise[A]
    channel.read[ReadState[A]](readBuffer, 0, state,
      new CompletionHandler[java.lang.Integer, ReadState[A]] {
        def completed(result: java.lang.Integer, attachment: ReadState[A]) {
          readBuffer.flip()
          processor(IO.Chunk(ByteString(readBuffer)))
          readBuffer.flip()
          if(result==attachment.stepSize){
            throw new Error("Read next bit")
          } else{
            //processor(IO.EOF(None))
          }
          Promise.successful(attachment.resultBuffer.toSeq)
        }

        def failed(exc: Throwable, attachment: ReadState[A]) {
          promise.failure(exc)
        }
      })
    promise
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


  case class ReadState[A](position: Long,
                          stepSize: Long,
                          resultBuffer: scala.collection.mutable.ArrayBuffer[A] = new ArrayBuffer[A]())

}
