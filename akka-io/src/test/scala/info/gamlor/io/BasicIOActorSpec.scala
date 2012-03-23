package info.gamlor.io

import akka.pattern._
import info.gamlor.io.IOActors._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Timeout
import akka.testkit.TestActorRef
import java.nio.file.StandardOpenOption
import java.io.IOException
import akka.actor._
import java.util.concurrent.{TimeUnit, CountDownLatch}

/**
 * @author roman.stoffel@gamlor.info
 * @since 23.03.12
 */

class BasicIOActorSpec extends SpecBase {

  implicit val timeout = Timeout(5 seconds)

  describe("Actor IO") {

    it("allows to read a file") {

      val actor = IOActors.createForFile(TestFiles.inTestFolder("helloWorld.txt"))

      val readFuture = for {
        fileSize <- (actor ? FileSize).mapTo[FileSizeResponse]
        dataRead <- (actor ? Read(0, fileSize.size.toInt)).mapTo[ReadResponse]
      } yield dataRead


      val content = Await.result(readFuture, 5 seconds)
      content.data.utf8String must be("Hello World")
      content.startPoint must be(0)
      content.amountToRead must be(11)

    }
    it("can have multiple actors") {
      val smallFileReader = IOActors.createForFile(TestFiles.inTestFolder("helloWorld.txt"))
      val largeFileReader = IOActors.createForFile(TestFiles.inTestFolder("largerTestFile.txt"))


      val sizes = for {
        smallSize <- (smallFileReader ? FileSize).mapTo[FileSizeResponse].map(f => f.size)
        largeSize <- (largeFileReader ? FileSize).mapTo[FileSizeResponse].map(f => f.size)
      } yield (smallSize, largeSize)


      val (smallSize, largeSize) = Await.result(sizes, 5 seconds)
      smallSize should be < (largeSize)
    }
    it("closes resource after timeout") {
      val testRef = TestActorRef(new IOActor(TestFiles.inTestFolder("helloWorld.txt"), 1 milliseconds, Set(StandardOpenOption.READ)))

      // send request to open channel
      val sizeRequest = (testRef ? FileSize)
      Await.ready(sizeRequest, 5 seconds)

      Thread.sleep(200)

      testRef.underlyingActor.isChannelClosed must be(true)
    }

    it("crashes on io issue") {
      val supervisor = TestActorRef(new TestSupervisor())

      val failingFileSizeRequest = for{
        fileHandlingActor <- (supervisor ? "non-existing-file.txt").mapTo[ActorRef]
        fileSize <- fileHandlingActor ? FileSize
      } yield fileSize

      supervisor.underlyingActor.waitForFailure.await(1000,TimeUnit.SECONDS)

      supervisor.underlyingActor.ioExceptionCounter must be(1)
    }
  }

  class TestSupervisor extends Actor {
    var ioExceptionCounter = 0;
    val waitForFailure = new CountDownLatch(1)

    override val supervisorStrategy = OneForOneStrategy(5, 5 seconds) {
      case _: IOException => {
        ioExceptionCounter += 1
        waitForFailure.countDown()
        SupervisorStrategy.Stop
      }
    }

    protected def receive = {
      case fileName: String => sender ! IOActors.createForFile(TestFiles.inTestFolder(fileName))(context)
      case CrashCount => sender ! ioExceptionCounter
    }


  }
  case object CrashCount

}
