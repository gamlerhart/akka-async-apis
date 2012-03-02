package info.gamlor.io

import akka.actor.IO
import akka.util.ByteString

/**
 * @author roman.stoffel@gamlor.info
 * @since 28.02.12
 */

object LetsDoIO extends App {
  main()

  def main() {
    val path = TestFiles.inTestFolder("helloWorld.txt")
    println(path)
    val SP = ByteString(" ")
    val readRequestLine =
      for {
        meth ← IO takeUntil SP
      } yield meth

    val ftr = readRequestLine.apply(IO.Chunk(ByteString("Tes")))._2
    println(ftr)
    val after1 = readRequestLine.apply(IO.Chunk(ByteString("Tes")))._1
    val after2 = after1.apply(IO.Chunk(ByteString("t est")))
    val restOfIO = after2._2
    val after3 = readRequestLine.apply(restOfIO)._1
    val after4 = after3.apply(IO.Chunk(ByteString(" ")))._1


    println(after2._1.get.utf8String)
    println(after4.get.utf8String)

  }


}
