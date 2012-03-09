package info.gamlor.io

import java.util.concurrent.Executors
import akka.dispatch.{ExecutionContext, Promise, Future}
import util.continuations._


/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

object FlowExperiments extends App {

  implicit val ctx = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  main()

  def main() {

    val genA = for {

      x <- 0 until 3

      b <- 5 until 10

    } yield (x, b)

    for (a <- this) {
      println(a)
    }



    //    Future.flow {
    //      var state = 0
    //      var ints = List(1, 2, 3, 42)
    //      for (r2 <- CpsConversions.cpsIterable(ints).cps) {
    //        val aFuture = r2;
    //        println(r2)
    //        println("State " + state)
    //        state = state + 1
    //      }
    //      println("Done")
    //    }
  }

  def data() = Seq[Future[String]](Promise.successful("Data"), Promise.successful("Data2"), Promise.successful("End"))


  def foreach(aFunction: String => Unit) {
    reset {
      val v = shift {
        restOfLoop: (String => Unit) => {
          triggerOperation(restOfLoop)
        }
      }
      aFunction(v)
    }
  }


  private def triggerOperation(restOfLoop: (String => Unit)) {
    Promise.successful("t ").onComplete {
      case Right(result) => {
        triggerOperation(restOfLoop)
        restOfLoop(result)
      }
      case _ => {}
    }
  }

}
