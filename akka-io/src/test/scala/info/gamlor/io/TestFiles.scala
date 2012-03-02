package info.gamlor.io

import java.nio.file.{Paths, Path}


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object TestFiles {
  val TestDataLocation = Paths.get("./akka-io/testdata")

  def inTestFolder(fileName:String)=TestDataLocation.resolve(fileName)
}
