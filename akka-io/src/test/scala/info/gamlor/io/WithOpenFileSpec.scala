package info.gamlor.io

import java.nio.channels.ClosedChannelException
import akka.dispatch.Await
import akka.util.duration._


/**
 * @author roman.stoffel@gamlor.info
 * @since 16.03.12
 */

class WithOpenFileSpec extends SpecBase{

  describe("withFileOpen"){
    it("closes channel"){
      val file = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>f
      }
      intercept[ClosedChannelException](file.size())
    }
    it("can still read everything"){
      val (data,file) = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>{
          val data = for{
            readALineOnce <- f.read(0,100)
            readALineTwice <- f.read(0,100)
          } yield readALineOnce.++(readALineTwice)
          (data,f)
        }
      }
      intercept[ClosedChannelException](file.size())
      val result = Await.result(data, 5 seconds)
      result.utf8String must be ("Hello WorldHello World")
    }
  }

}
