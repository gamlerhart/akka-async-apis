package info.gamlor.io

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import akka.actor.IO.Iteratee
import java.nio.channels.ClosedChannelException

/**
 * @author roman.stoffel@gamlor.info
 * @since 16.03.12
 */

private[io] class CloseChannelGroup(private val implementation: FileIO) extends FileIO {
  private val amountOfOutstandingOperations = new AtomicInteger(0)
  @volatile private var closed = false

  def size() = {
    ensureAlive()
    implementation.size()
  }

  def force(metaData: Boolean) {
    ensureAlive()
    implementation.force(metaData)
  }

  def read(startPoint: Long, amountToRead: Int)
  = {
    ensureAlive()
    amountOfOutstandingOperations.incrementAndGet()
    val future = implementation.read(startPoint, amountToRead)
    future.onComplete {_=>
      eventuallyCloseUnderlayingChannel()
    }
    future
  }

  def readAll[A](parser: Iteratee[A], startPos: Long, amountToRead: Long) = implementation.readAll(parser, startPos, amountToRead)

  def readSegments[A](segmentParser: Iteratee[A], startPos: Long, amountToRead: Long) = implementation.readSegments(segmentParser, startPos, amountToRead)

  def write(writeBuffer: ByteBuffer, startPostion: Long) = implementation.write(writeBuffer, startPostion)


  def close() {
    ensureAlive
    closed = true
    eventuallyCloseUnderlayingChannel()
  }
  private def ensureAlive() {
    if (closed) {
      throw new ClosedChannelException()
    }
  }


  private def eventuallyCloseUnderlayingChannel() {
    if (0 == amountOfOutstandingOperations.decrementAndGet()) {
      implementation.close()
    }
  }


}
