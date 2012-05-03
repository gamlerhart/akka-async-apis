package info.gamlor.db

import org.scalatest.BeforeAndAfter
import akka.dispatch.Await
import akka.util.duration._
import org.adbcj.DbException

/**
 * @author roman.stoffel@gamlor.info
 * @since 03.05.12
 */

class PreparedQueriesSpec  extends SpecBaseWithDB with BeforeAndAfter{

  describe("Prepared queries"){
    it("can run simple select"){
      var future=for{
        connection <- Database(system).connect()
        statement <-connection.prepareStatement("SELECT * FROM testTable WHERE bornInYear>?  ORDER BY bornInYear DESC")
        result <-statement.execute(1984)
        _ <-statement.close()
        _ <-connection.close()
      } yield result

      val result = Await.result(future, 5 seconds)

      result.size must be(2)
      result.get(0).get("bornInYear").getString must be("1990")
      result.get(1).get("bornInYear").getString must be("1986")

    }
    it("returns error on closed statements"){
      var future=for{
        connection <- Database(system).connect()
        statement <-connection.prepareStatement("SELECT * FROM testTable WHERE bornInYear>?  ORDER BY bornInYear DESC")
        _ <-statement.close()
        result <-statement.execute(1984)
        _ <-connection.close()
      } yield result

      intercept[DbException](Await.result(future, 5 seconds))

    }
  }


}
