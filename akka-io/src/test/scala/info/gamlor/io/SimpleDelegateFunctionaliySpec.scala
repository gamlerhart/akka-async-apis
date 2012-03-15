package info.gamlor.io

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
    }
    it("can flush"){
      val file = FileIO.open(TestFiles.inTestFolder("helloWorld.txt").toString)
      file.size must be(11)
      file.force()
    }
  }

}
