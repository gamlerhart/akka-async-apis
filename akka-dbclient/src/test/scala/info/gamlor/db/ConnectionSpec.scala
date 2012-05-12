package info.gamlor.db

import akka.dispatch.{Await, Promise}
import akka.util.duration._
import java.util.concurrent.atomic.AtomicReference


/**
 * @author roman.stoffel@gamlor.info
 * @since 12.05.12
 */

class ConnectionSpec extends SpecBaseWithDB{

  describe("Trasaction Support") {
    it("closes connection"){
      val resultFuture = Database(system).withConnection{
        conn=>{
          Promise.successful((conn.isOpen,conn))
        }
      }
      val (wasConnectionOpen,conn) = Await.result(resultFuture,5 seconds)

      wasConnectionOpen should be(true)
      conn.isClosed should be(true)
    }
    it("closes on error in future"){
      val connectionExtraction = new AtomicReference[DBConnection]
      val resultFuture = Database(system).withConnection{
        conn=>{
          connectionExtraction.set(conn)
          Promise.failed(new SimulatedErrorException("Simulated error"))
        }
      }
      intercept[SimulatedErrorException](Await.result(resultFuture,5 seconds))
      connectionExtraction.get().isClosed should be(true)
    }
    it("closes on error in composing fuction"){
      val connectionExtraction = new AtomicReference[DBConnection]
      val resultFuture = Database(system).withConnection{
        conn=>{
          connectionExtraction.set(conn)
          throw new SimulatedErrorException("Simulated error")
        }
      }
      intercept[SimulatedErrorException](Await.result(resultFuture,5 seconds))
      connectionExtraction.get().isClosed should be(true)
    }
  }

}
