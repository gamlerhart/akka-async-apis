package info.gamlor.io

import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.file._
import akka.dispatch.{Future, ExecutionContext}
import scala.collection.JavaConversions._
import collection.mutable.Buffer
import akka.actor.IO.{Iteratee, Input}
import java.nio.channels.AsynchronousFileChannel
import akka.actor.IO


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object FileIO {
  private val defaultOpenOptions: java.util.Set[OpenOption] = java.util.Collections.singleton(StandardOpenOption.READ)

  /**
   * Opens a the specified file for the given path.
   *
   * This file will be opened in read only mode
   * @param fileName a valid path to a file
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def open(fileName: Path)(implicit context: ExecutionContext) = {
    val fileChannel = AsynchronousFileChannel.open(fileName, defaultOpenOptions, new DelegateToContext(context))
    new FileChannelIO(fileChannel, context)
  }

  /**
   * Opens a the specified file for the given path.
   *
   * @param fileName a valid path to a file
   * @param openOptions the open options for the file. See [[java.nio.file.OpenOption]]
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def open(fileName: Path, openOptions: OpenOption*)(implicit context: ExecutionContext): FileChannelIO
  = open(fileName, openOptions.toSet, context)
  /**
   * Opens a the specified file for the given path.
   *
   * @param fileName a valid path to a file
   * @param openOptions the open options for the file. See [[java.nio.file.OpenOption]]
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def open(fileName: Path, openOptions: Set[OpenOption] = Set(StandardOpenOption.READ))(implicit context: ExecutionContext): FileChannelIO
  = open(fileName, openOptions, context)

  /**
   * Opens a the specified file for the given path.
   *
   * This file will be opened in read only mode
   * @param fileName a valid path to a file
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def open(fileName: String)(implicit context: ExecutionContext): FileIO = {
    open(Paths.get(fileName))
  }

  /**
   * Opens a the specified text-file for the given path.
   *
   * This file will be opened in read only mode. The encoding will be UTF8
   * @param fileName a valid path to a file
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def openText(fileName: String)(implicit context: ExecutionContext) = {
    TextFileIO(open(fileName)(context))
  }

  /**
   * Opens a the specified text-file for the given path.
   *
   * @param fileName a valid path to a file
   * @param encoding the text encoding for this file
   * @param openOptions the open options for the file. See [[java.nio.file.OpenOption]]
   * @param context context on which the IO completion operations are executed
   * @return
   */
  def openText(fileName: Path,
               encoding: String = "UTF-8",
               openOptions: Set[OpenOption] = Set(StandardOpenOption.READ))
              (implicit context: ExecutionContext) = {
    TextFileIO(open(fileName, openOptions.toSet, context), encoding)
  }

  /**
   * Opens the specified file and runs the code of the given closure. It will close the file
   * when the future which the closure returns finishes.
   *
   * This is indended for doing multiple read operations and then close the file: for example:
   * <pre>val data = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        file=>{
          for{
            readALineOnce <-file.read(0,100)
            readALineTwice <- file.read(0,100)
            readSomethingElse <- file.read(500,100)
          } yield readALineOnce.++(readALineTwice).++(readSomethingElse)
        }
      }</pre>
   *
   * @param fileName path the file
   * @param openOptions the open options for the file. See [[java.nio.file.OpenOption]]
   * @param toRun the closure to execute
   * @param context context on which the IO completion operations are executed
   * @tparam A type of the result
   * @return the future which the closure produced
   */
  def withFile[A](fileName: Path, openOptions: OpenOption*)(toRun: FileIO => Future[A])
                 (implicit context: ExecutionContext): Future[A] = {
    val fileChannel = open(fileName, openOptions.toSet, context)
    toRun(fileChannel).onComplete(_=>{
      fileChannel.close()
    })
  }


  def withTextFile[A](fileName: Path,
               encoding: String = "UTF-8",
               openOptions: Set[OpenOption] = Set(StandardOpenOption.READ))
              (toRun: TextFileIO => Future[A])
              (implicit context: ExecutionContext) = {
    val textChannel = openText(fileName,encoding, openOptions)(context)
    toRun(textChannel).onComplete(_=>{
      textChannel.close()
    })
  }

  private def open(fileName: Path, openOptions: java.util.Set[OpenOption], context: ExecutionContext): FileChannelIO = {
    val fileChannel = AsynchronousFileChannel.open(fileName, openOptions,
      new DelegateToContext(context))
    new FileChannelIO(fileChannel, context)
  }


}

trait FileIO {
  /**
   * @see [[java.nio.channels.AsynchronousFileChannel# s i z e]]
   */
  def size(): Long

  /**
   * @see [[java.nio.channels.AsynchronousFileChannel# f o r c e]]
   */
  def force(metaData: Boolean = true): Unit

  /**
   * Reads a sequence of bytes from this file, starting at the given file position. Finally it returns the read data as a byte string.
   * @param startPoint start point of the read operation, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes. A byte buffer of this size will be allocated.
   * @return future which will complete with the read data or exception.
   */
  def read(startPoint: Long, amountToRead: Int): Future[ByteString];

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
  def readAll[A](parser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[A];

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
  def readSegments[A](segmentParser: Iteratee[A], startPos: Long = 0, amountToRead: Long = -1): Future[Seq[A]];

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
  def write(dataToWrite: ByteString, startPostion: Long): Future[Int] = {
    val buffer = dataToWrite.asByteBuffer
    write(buffer, startPostion)
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
  def write(dataToWrite: Array[Byte], startPostion: Long): Future[Int] = {
    write(ByteBuffer.wrap(dataToWrite), startPostion)
  }


  /**
   * @see [[java.nio.channels.AsynchronousFileChannel# w r i t e]]
   */
  def write(writeBuffer: ByteBuffer, startPostion: Long): Future[Int]

  /**
   * Closes this file and the underlying channel.
   *
   * Any outstanding asynchronous operations upon this channel will complete with the exception AsynchronousCloseException.
   * After a channel is closed, further attempts to initiate asynchronous I/O operations complete immediately with cause ClosedChannelException.
   */
  def close()


}

trait AccumulationReadingBase extends FileIO{

  override def read(startPoint: Long, amountToRead: Int): Future[ByteString] = {
    readAndAccumulate(Accumulators.byteStringBuilder(), startPoint, amountToRead)
  }


  override def readAll[A](parser: Iteratee[A], startPos: Long, amountToRead: Long): Future[A]
  = readAndAccumulate(Accumulators.parseWhole(parser), startPos, amountToRead)

  override def readSegments[A](segmentParser: Iteratee[A], startPos: Long, amountToRead: Long): Future[Seq[A]]
  = readAndAccumulate(Accumulators.parseSegments(segmentParser), startPos, amountToRead)


  /**
   * Used by the default implementation of the read methods to read the file.
   *
   * The implementation has to read from the given position the given amount of bytes. Every chunk of
   * data read then in passed to the accumulator by calling [[info.gamlor.io.AccumulationReadingBase.Accumulator#apply]].
   * If it return true it can continue to read the data. If false is returned, it can stop readion further.
   * When reading has finished, [[info.gamlor.io.AccumulationReadingBase.Accumulator#finishedValue]] should be called. That
   * result then is the result of the returned Future.
   *
   * The accumulator is a mutable instance, with no synchonisation. The implementation has to
   * ensure the the accumulator is accessed in a synchronized manner.
   * @param accumulator the accumular which builds the end result during the read process
   * @param startPos start point of the read, from 0. If the start point is outside the file size, a empty result is returned
   * @param amountToRead the amount to read in bytes.
   * @tparam A
   * @return
   */
  protected def readAndAccumulate[A](accumulator: Accumulator[A], startPos: Long = 0, amountToRead: Long = -1): Future[A]

  /**
   * Accumelates the read data during a read request.
   * Therefore it is mutable. A acummolater is accessed by the completion handlers.
   * It relies on the fact that not multiple requests are issued with the same accumulator
   * @tparam A
   */
  trait Accumulator[A] {
    def apply(input: IO.Input): Boolean

    def finishedValue(): A
  }

  object Accumulators {

    def parseWhole[A](parser: Iteratee[A]) = new Accumulator[A] {
      private val mutableItaree = IO.IterateeRef.sync(parser)

      def apply(input: Input): Boolean = {
        mutableItaree(input)
        val isDone = mutableItaree.value._1.isInstanceOf[IO.Done[A]]
        return !isDone
      }

      def finishedValue(): A = mutableItaree.value._1.get
    }

    def byteStringBuilder() = new Accumulator[ByteString] {
      private val builder = ByteString.newBuilder

      def apply(input: Input): Boolean = {
        input match {
          case IO.Chunk(bytes) => {
            builder ++= bytes
          }
          case _ => {}
        }
        true
      }

      def finishedValue() = builder.result()
    }

    def parseSegments[A](parser: Iteratee[A]) = new Accumulator[Seq[A]] {
      private val buffer = Buffer[A]();
      private var currentIteratee: Iteratee[A] = parser;

      def apply(input: Input): Boolean = {
        if (input.isInstanceOf[IO.EOF]) {
          buffer.add(currentIteratee(input)._1.get);
        } else {
          var (parsedValue, rest) = currentIteratee(input)
          while (parsedValue.isInstanceOf[IO.Done[A]]) {
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

}


/**
 * Accumelates the read data during a read request.
 * Therefore it is mutable. A acummolater is accessed by the completion handlers.
 * It relies on the fact that not multiple requests are issued with the same accumulator
 * @tparam A
 */
trait Accumulator[A] {
  def apply(input: IO.Input): Boolean

  def finishedValue(): A
}


