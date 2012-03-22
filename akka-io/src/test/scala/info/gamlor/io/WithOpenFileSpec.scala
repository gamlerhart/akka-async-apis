package info.gamlor.io

import java.nio.channels.ClosedChannelException
import akka.util.duration._
import akka.dispatch.{Promise, Await}


/**
 * @author roman.stoffel@gamlor.info
 * @since 16.03.12
 */

class WithOpenFileSpec extends SpecBase{

  describe("withFileOpen"){
    it("closes channel"){
      val fileFuture = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>Promise.successful(f)
      }
      val awaitCompleteHandler = Promise[Unit]();
      fileFuture.onComplete({ignored=>
        awaitCompleteHandler.success()
      })
      Await.ready(awaitCompleteHandler, 5 seconds)
      val file = Await.result(fileFuture, 5 seconds)
      intercept[ClosedChannelException](file.size())
    }
    it("can still read everything"){
      val data = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>{
          f.read(0,100)
          f.read(0,100)
          for{
            readALineOnce <- f.read(0,100)
            readALineTwice <- f.read(0,100)
          } yield readALineOnce.++(readALineTwice)
        }
      }
      val result = Await.result(data, 5 seconds)
      result.utf8String must be ("Hello WorldHello World")
    }
    it("can still read text"){
      val data = FileIO.withTextFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>{
          for{
            splittedLines <- f.readSplitBy(" ")
          } yield splittedLines
        }
      }
      val result = Await.result(data, 5 seconds)
      result must be (Seq("Hello","World"))
    }
  }



}
