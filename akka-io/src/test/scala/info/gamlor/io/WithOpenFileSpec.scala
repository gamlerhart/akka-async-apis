package info.gamlor.io

import java.nio.channels.ClosedChannelException


/**
 * @author roman.stoffel@gamlor.info
 * @since 16.03.12
 */

class WithOpenFileSpec extends SpecBase{

  describe("withFileOpen"){
    it("closes channe"){
      val file = FileIO.withFile(TestFiles.inTestFolder("helloWorld.txt")){
        f=>f
      }
      intercept[ClosedChannelException](file.size())
    }
  }

}
