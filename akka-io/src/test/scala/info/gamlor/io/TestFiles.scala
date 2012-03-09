package info.gamlor.io

import java.nio.file.{Paths, Path}
import java.io.File


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object TestFiles {
  val TestDataLocation = Paths.get("./akka-io/testdata")

  def inTestFolder(fileName:String)=TestDataLocation.resolve(fileName)

  def tempFile() = {
    val tmp = File.createTempFile("TestFiles","tmp")
    tmp.deleteOnExit()
    tmp.toPath
  }
}
