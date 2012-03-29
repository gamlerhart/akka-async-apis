package info.gamlor.db

import akka.dispatch.Await
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

class SimpleSelectTestCase extends SpecBaseWithH2{


  describe("Basic DB operations") {
    it("can get connection "){

      val connection = Database(system).connect()
      connection must not be(null)

      val result = Await.result(connection, 5 seconds)
      connection must not be(result)
    }
  }



}
