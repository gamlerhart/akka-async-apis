package info.gamlor.web

import info.gamlor.io.TestActorSystem
import akka.dispatch.Await
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

object PlayAround extends App{
  main()
  def main(){
    val system = TestActorSystem.DefaultSystem

    val time = System.currentTimeMillis()

    val gr = WebClient(system).prepareGet("http://www.google.com/").execute()
//    Await.ready(gr, 5 seconds)
    val mr = WebClient(system).prepareGet("http://www.microsoft.com/").execute()
//    Await.ready(mr, 5 seconds)
    val fr = WebClient(system).prepareGet("http://www.facebook.com/").execute()
//    Await.ready(fr, 5 seconds)

    val f3 = for {
      a ← gr
      b ← mr
      c ← fr
    } yield (a,b,c)
    val result = Await.result(f3, 5 seconds)
    println(result._1.getStatusCode)
    println(result._2.getStatusCode)
    println(result._3.getStatusCode)

    println("Used time "+ (System.currentTimeMillis()-time))

    system.shutdown()


  }
}
