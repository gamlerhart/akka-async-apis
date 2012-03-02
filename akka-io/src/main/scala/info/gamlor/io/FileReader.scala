package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.{CompletionHandler, AsynchronousFileChannel}
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext, Promise}
import scala.collection.JavaConversions._


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object FileReader {
  private val defaultOpenOptions:java.util.Set[OpenOption] = java.util.Collections.singleton(StandardOpenOption.READ)

  def open(fileName:String)(implicit context:ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName),defaultOpenOptions,new DelegateToContext(context))
    new FileReader(fileChannel,context)
  }
  def open(fileName:String, openOptions: OpenOption* )(implicit context:ExecutionContext) = {

    val fileChannel = AsynchronousFileChannel.open(Paths.get(fileName),openOptions.toSet,new DelegateToContext(context))
    new FileReader(fileChannel,context)
  }



}

class FileReader(val channel : AsynchronousFileChannel,private implicit val context:ExecutionContext) {


  def size() = channel.size()
  def read(startPoint:Long, amountToRead:Int):Future[ByteString] ={
    val readBuffer = ByteBuffer.allocate(amountToRead)
    val promise = Promise[ByteString]
    channel.read[Any](readBuffer,startPoint,null,new CompletionHandler[java.lang.Integer, Any] {
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

  def write(startPostion:Long,dataToWrite:ByteString):Future[Unit]={
    val writeBuffer = dataToWrite.asByteBuffer
    val promise = Promise[Unit]
    channel.write(writeBuffer,startPostion,null,new CompletionHandler[java.lang.Integer, Any] {
      def completed(result: java.lang.Integer, attachment: Any) {
        promise.success()
      }

      def failed(exc: Throwable, attachment: Any) {
        promise.failure(exc)
      }
    })
    promise
  }

  def close() = channel.close()

}
