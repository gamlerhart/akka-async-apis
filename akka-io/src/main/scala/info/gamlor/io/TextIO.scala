package info.gamlor.io

import akka.dispatch.{ExecutionContext, Future}
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Paths, Path}

/**
 * @author roman.stoffel@gamlor.info
 * @since 15.03.12
 */

trait TextIO {
  def fileIO():FileIO
  def readAllLines() : Future[Seq[String]] = null
  def readWholeFile() : Future[String]= null
  def readSplitBy(delimiters:String*) : Future[Seq[String]] = null

  def encoding = "UTF-8"

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
