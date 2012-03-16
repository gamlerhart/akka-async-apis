package info.gamlor.io

import akka.util.duration._
import akka.dispatch.{Future, Await}
import akka.util.ByteString
import java.nio.channels.ClosedChannelException

/**
 * @author roman.stoffel@gamlor.info
 * @since 09.03.12
 */

class SimpleDelegateFunctionaliySpec extends SpecBase{

  describe("File Read"){
    it("can get size"){
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      file.size must be(11)
      file.force()
      file.close()
    }
    it("can flush"){
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      file.force()
      file.close()
    }
    it("can be closed"){
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      file.close()
      val cannotReadAnymore: Future[ByteString] = file.read(0, 1)
      Await.ready(cannotReadAnymore, 5 seconds)

      cannotReadAnymore.value.get.left.get.isInstanceOf[ClosedChannelException] must be(true)
    }
  }

}
