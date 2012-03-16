package info.gamlor.io

import akka.dispatch.{ExecutionContext, Future}
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Paths, Path}
import java.io.IOException
import akka.actor.IO
import akka.util.ByteString

/**
 * @author roman.stoffel@gamlor.info
 * @since 15.03.12
 */

trait TextIO {
  def fileIO():FileIO
  def readAllLines() : Future[Seq[String]] = {
    fileIO().readSegments(OneLine)
  }
  def readWholeFile() : Future[String]= {
    if(fileIO().size() > Int.MaxValue){
      throw new IOException("Cannot read files larger than "+Int.MaxValue+" to be read as string")
    }
    fileIO().read(0,fileIO().size().toInt).map(bytes=>bytes.decodeString(encoding))
  }
  def readSplitBy(delimiters:String*) : Future[Seq[String]] = null

  def encoding = "UTF-8"


  private val LineDelimiters = {
    val CRLF: ByteString = ByteString("\r\n", encoding)
    val CR: ByteString = ByteString("\r", encoding)
    val LF: ByteString = ByteString("\n", encoding)
    Seq(CRLF,CR,LF)
  }

  private val OneLine = for{
    line <- AdditonalIO.takeUntil(LineDelimiters)
  } yield line.decodeString(encoding)
}


object TextFileIO{
  def apply(fileIO:FileIO) = new TextFileIO(fileIO)


  def openText(fileName: Path)(implicit context: ExecutionContext)
  = FileIO.openText(fileName)(context)
  def openText(fileName: String)(implicit context: ExecutionContext)
  = FileIO.openText(fileName)(context)



class TextFileIO(val fileIO:FileIO) extends TextIO{

 }
}
