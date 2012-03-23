package info.gamlor.io

import akka.util.{Duration, ByteString}
import java.util.concurrent.TimeUnit
import java.nio.file.{StandardOpenOption, OpenOption, Path}
import akka.actor.{Props, Actor, ActorRefFactory, ActorRef}
import akka.dispatch.ExecutionContext


/**
 * @author roman.stoffel@gamlor.info
 * @since 23.03.12
 */

object IOActors {

  /**
   * Creates an actor which does asynchronous file operations for you
   * @param fileName a valid path to a file
   * @param autoCloseAfter timeout after which the file channel is closed when not used
   * @param openOptions the open options for the file. See [[java.nio.file.OpenOption]]
   * @param actorFactory the context to create new actors
   * @return
   */
  def createForFile(fileName: Path,
                    autoCloseAfter: Duration = Duration(5, TimeUnit.SECONDS),
                    openOptions: Set[OpenOption] = Set(StandardOpenOption.READ))(implicit actorFactory: ActorRefFactory): ActorRef = {
    actorFactory.actorOf(Props(new IOActor((ctx)=>FileIO.open(fileName,openOptions)(ctx), autoCloseAfter)))
  }


  /**
   * Request the file length information. The file length will be reported back with [[info.gamlor.io.IOActors.FileSizeResponse]]
   *
   */
  case object FileSize

  /**
   * Close the channel to the file. Note that any further operation reopens the channel.
   *
   * No response is sent back for this request.
   */
  case object CloseChannel

  /**
   * Reads a sequence of bytes from this file, starting at the given file position. Finally it returns the read data as a byte string.
   *
   * On a successfull read the respons is delivered as a [[info.gamlor.io.IOActors.ReadResponse]] message.
   * In case of a failure the IOActor chrashes bye default and lets the the Akka failure handling kick in.
   * @param startPoint start point of the read operation, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @return future which will complete with the read data or exception.
   */
  case class Read(startPoint: Long, amountToRead: Int)

  /**
   * Respons for [[info.gamlor.io.IOActors.FileSizeResponse]]
   * @param size the size of the file
   */
  case class FileSizeResponse(size: Long)

  /**
   * Response for [[info.gamlor.io.IOActors.Read]].
   * @param data the read data
   * @param startPoint the position from which this read occurred
   * @param amountToRead the amount to read which was requested. The amount read is the data length.
   */
  case class ReadResponse(data: ByteString, startPoint: Long, amountToRead: Int)




}

class IOActor(fileHandleFactory:ExecutionContext=>FileIO,
              autoCloseAfter: Duration = Duration(5, TimeUnit.SECONDS)) extends Actor {

  import info.gamlor.io.IOActors._

  private implicit val executionContext = context.dispatcher
  private var openChannel: Option[FileIO] = None
  private var channelInUse = false

  protected def receive = {
    case FileSize => {
      inUsed()
      val file = fileChannel()
      sender ! FileSizeResponse(file.size())
    }
    case Read(start, amount) => {
      inUsed()
      val file = fileChannel()
      val currentSender = sender
      val currentSelf = self
      file.read(start, amount).onSuccess {
        case bytes: ByteString => currentSender ! ReadResponse(bytes, start, amount)
        case unexpected => currentSelf ! UnexpectedResult(unexpected)
      }.onFailure {
        case ex: Exception => currentSelf ! ExceptionOccured(ex)

      }
    }
    case CheckIfIsInUse =>{
      if(channelInUse){
        channelInUse = false;
        context.system.scheduler.scheduleOnce(autoCloseAfter,self,CheckIfIsInUse)
      } else{
        closeChannel()
      }
    }
    case CloseChannel =>{
      closeChannel()
    }
    case ExceptionOccured(ex)=>throw ex
    case UnexpectedResult(result)=>throw new Error("Completly unexpected value within the actor"+result)

  }

  private def fileChannel() = {
    openChannel match{
      case Some(channel) => channel
      case None =>{
        val channel = fileHandleFactory(context.dispatcher)
        openChannel = Some(channel)
        channel
      }
    }
  }
  private def inUsed() = {
    channelInUse = true
    context.system.scheduler.scheduleOnce(autoCloseAfter,self,CheckIfIsInUse)
  }


  private def closeChannel() {
    channelInUse = false
    openChannel.foreach(c => c.close())
    openChannel = None
  }

  override def postStop() {
    closeChannel()
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    closeChannel()
  }

  def isChannelClosed = openChannel == None

  private case class UnexpectedResult(result: Any)

  private case class ExceptionOccured(exception: Exception)

  private case object CheckIfIsInUse
}
