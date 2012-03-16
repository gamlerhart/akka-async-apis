package info.gamlor.io

import akka.dispatch.{ExecutionContext, Future}
import java.nio.file.Path
import java.io.IOException
import akka.util.ByteString

/**
 * @author roman.stoffel@gamlor.info
 * @since 15.03.12
 */

trait TextIO {
  def fileIO(): FileIO

  def readAllLines(): Future[Seq[String]] = {
    fileIO().readSegments(OneLine)
  }

  def readWholeFile(): Future[String] = {
    if (fileIO().size() > Int.MaxValue) {
      throw new IOException("Cannot read files larger than " + Int.MaxValue + " to be read as string")
    }
    fileIO().read(0, fileIO().size().toInt).map(bytes => bytes.decodeString(encoding))
  }

  def readSplitBy(delimiters: String*): Future[Seq[String]] = {
    val delimtersAsBytes = delimiters.map(s => ByteString(s, encoding))
    val parser = for {
      line <- AdditonalIO.takeUntil(delimtersAsBytes)
    } yield line.decodeString(encoding)
    fileIO().readSegments(parser)
  }

  def appendToEnd(textToWrite: String): Future[Int] ={
    val startPoint = fileIO().size()
    val textAsBytes = ByteString(textToWrite,encoding)
    fileIO().write(textAsBytes,startPoint)
  }



  def encoding = "UTF-8"


  private val LineDelimiters = {
    val CRLF: ByteString = ByteString("\r\n", encoding)
    val CR: ByteString = ByteString("\r", encoding)
    val LF: ByteString = ByteString("\n", encoding)
    Seq(CRLF, CR, LF)
  }

  private val OneLine = for {
    line <- AdditonalIO.takeUntil(LineDelimiters)
  } yield line.decodeString(encoding)
}


object TextFileIO {
  def apply(fileIO: FileIO, encoding:String="UTF-8") = new TextFileIO(fileIO, encoding)


  def openText(fileName: Path, encoding:String="UTF-8")(implicit context: ExecutionContext)
  = FileIO.openText(fileName,encoding)(context)

  def openText(fileName: String)(implicit context: ExecutionContext)
  = FileIO.openText(fileName)(context)


  class TextFileIO(val fileIO: FileIO, override val encoding:String = "UTF-8") extends TextIO {

  }

}
