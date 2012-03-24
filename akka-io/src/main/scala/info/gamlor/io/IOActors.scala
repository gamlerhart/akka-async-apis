package info.gamlor.io

import akka.util.{Duration, ByteString}
import java.util.concurrent.TimeUnit
import java.nio.file.{StandardOpenOption, OpenOption, Path}
import akka.dispatch.ExecutionContext
import akka.actor._


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
                    openOptions: Set[OpenOption] = Set(StandardOpenOption.READ),
                    autoCloseAfter: Option[Duration] = Some(Duration(5, TimeUnit.SECONDS)))(implicit actorFactory: ActorRefFactory): ActorRef = {
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
   * On a successful read the response is delivered as a [[info.gamlor.io.IOActors.ReadResponse]] message.
   * In case of a failure the IOActor crashes and the Akka failure handling kicks in. So this actor is intended to be supervised
   * @param startPoint start point of the read operation, from 0, in bytes. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @return future which will complete with the read data or exception.
   */
  case class Read(startPoint: Long, amountToRead: Int)

  /**
   * Writes the given data at the given point in the file. Then it return the amount of written bytes
   *
   * On a successful write the response is delivered as a [[info.gamlor.io.IOActors.WriteResponse]] message.
   * In case of a failure the IOActor crashes and the Akka failure handling kicks in. So this actor is intended to be supervised
   * @param dataToWrite the data to write
   * @param startPoint start point of the read operation, from 0, in bytes. If the start point is outside the file size, a empty result is returned
   */
  case class Write(dataToWrite: ByteString, startPoint:Long)

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

  /**
   * Response for [[info.gamlor.io.IOActors.Write]]
   * @param amountOfWrittenData
   */
  case class WriteResponse(amountOfWrittenData:Int)



  /**
   * Reads a sequence of bytes from this file, starting at the given file position. Instead of returning
   * all the requested data the data will be delivered in blocks, by sending back. This is especially usefull
   * when you don't want to keep the whole file in memory.
   *
   * Finally it returns the read data as a byte string.
   *
   * On a successful read the response is delivered as a [[info.gamlor.io.IOActors.ReadResponse]] message.
   * In case of a failure the IOActor crashes and the Akka failure handling kicks in. So this actor is intended to be supervised
   * @param startPoint start point of the read operation, from 0, in bytes. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @param identification a optional identification value for this request. Will be a part of all resulting [[info.gamlor.io.IOActors.ReadInChunksResponse]]
   * @return future which will complete with the read data or exception.
   */
  case class ReadInChunks(startPoint: Long, amountToRead: Long, identification:Any = null)

  /**
   * A part of the result from a [[info.gamlor.io.IOActors.ReadInChunks]] request. Mutliple
   * read chunks will be returned to the sender, until the read process is done.
   *
   *
   * @param data data in a [[akka.actor.IO.Chunk]] or [[akka.actor.IO.EOF]] when finished
   * @param identification the identification token passed to the [[info.gamlor.io.IOActors.ReadInChunks]]
   */
  case class ReadInChunksResponse(data:IO.Input,identification:Any = null)

}

class IOActor(private val fileHandleFactory:ExecutionContext=>FileIO,
              private val autoCloseAfter: Option[Duration] = Some(Duration(5, TimeUnit.SECONDS))) extends Actor {

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
    case ReadInChunks(start, amount, identifier) => {
      inUsed()
      val file = fileChannel()
      val currentSender = sender
      val currentSelf = self
      file.readChunked[Unit](start, amount){
        case c:IO.Input => currentSender ! ReadInChunksResponse(c,identifier)
      }.onFailure {
        case ex: Exception => currentSelf ! ExceptionOccured(ex)
      }
    }
    case Write(data, startPos) => {
      inUsed()
      val file = fileChannel()
      val currentSender = sender
      val currentSelf = self
      file.write(data, startPos).onSuccess {
        case amountBytesWritten: Int => currentSender ! WriteResponse(amountBytesWritten)
        case unexpected => currentSelf ! UnexpectedResult(unexpected)
      }.onFailure {
        case ex: Exception => currentSelf ! ExceptionOccured(ex)
      }
    }
    case CheckIfIsInUse =>{
      if(channelInUse){
        channelInUse = false;
        autoCloseAfter.foreach(timeout =>context.system.scheduler.scheduleOnce(timeout,self,CheckIfIsInUse))
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
    autoCloseAfter.foreach(timeout =>context.system.scheduler.scheduleOnce(timeout,self,CheckIfIsInUse))
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
