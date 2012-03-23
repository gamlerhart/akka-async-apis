package info.gamlor.io

import akka.util.{Duration, ByteString}
import java.util.concurrent.TimeUnit
import java.nio.file.{StandardOpenOption, OpenOption, Path}
import akka.actor.{Props, Actor, ActorRefFactory, ActorRef}


/**
 * @author roman.stoffel@gamlor.info
 * @since 23.03.12
 */

object IOActors {

  def createForFile(fileName: Path,
                    autoCloseAfter: Duration = Duration(5, TimeUnit.SECONDS),
                    openOptions: Set[OpenOption] = Set(StandardOpenOption.READ))(implicit actorFactory: ActorRefFactory): ActorRef = {
    actorFactory.actorOf(Props(new IOActor(fileName, autoCloseAfter, openOptions)))
  }

  trait IOActorRequest

  /**
   * Request the file length information. The file lenght will be reported back with [[info.gamlor.io.IOActors.FileSizeResponse]]
   *
   */
  case object FileSize extends IOActorRequest

  /**
   * Reads a sequence of bytes from this file, starting at the given file position. Finally it returns the read data as a byte string.
   *
   * On a successfull read the respons is delivered as a [[info.gamlor.io.IOActors.ReadResponse]] message.
   * In case of a failure the IOActor chrashes bye default and lets the the Akka failure handling kick in.
   * @param startPoint start point of the read operation, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @return future which will complete with the read data or exception.
   */
  case class Read(startPoint: Long, amountToRead: Int)  extends IOActorRequest

  case class FileSizeResponse(size: Long)

  case class ReadResponse(data: ByteString, startPoint: Long, amountToRead: Int)



}

class IOActor(fileName: Path,
              autoCloseAfter: Duration = Duration(5, TimeUnit.SECONDS),
              openOptions: Set[OpenOption] = Set(StandardOpenOption.READ)) extends Actor {

  import info.gamlor.io.IOActors._

  private implicit val executionContext = context.dispatcher
  private var openChannel: Option[FileChannelIO] = None
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
      file.read(start, amount).onSuccess {
        case bytes: ByteString => currentSender ! ReadResponse(bytes, start, amount)
        case unexpected => self ! UnexpectedResult(unexpected)
      }.onFailure {
        case ex: Exception => self ! ExceptionOccured(ex)
      }
    }
    case CheckIfIsInUse =>{
      if(channelInUse){
        channelInUse = false;
        context.system.scheduler.scheduleOnce(autoCloseAfter,self,CheckIfIsInUse)
      } else{
        openChannel.foreach(c=>c.close())
        openChannel = None
      }
    }
    case ExceptionOccured(ex)=>throw ex
    case UnexpectedResult(ex)=>throw new Error("Completly unexpected value within the actor"+ex)

  }

  private def fileChannel() = {
    openChannel match{
      case Some(channel) => channel
      case None =>{
        val channel = FileIO.open(fileName, openOptions)
        openChannel = Some(channel)
        channel
      }
    }
  }
  private def inUsed() = {
    channelInUse = true
    context.system.scheduler.scheduleOnce(autoCloseAfter,self,CheckIfIsInUse)
  }


  override def postStop() {
    channelInUse = false
    openChannel.foreach(c=>c.close())
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    channelInUse = false
    openChannel.foreach(c=>c.close())
  }

  def isChannelClosed = openChannel == None

  private case class UnexpectedResult(result: Any)

  private case class ExceptionOccured(result: Exception)

  private case object CheckIfIsInUse

}
