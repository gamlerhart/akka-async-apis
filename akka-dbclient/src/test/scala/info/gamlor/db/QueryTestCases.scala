package info.gamlor.db

import org.scalatest.BeforeAndAfter
import akka.dispatch.Await
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class QueryTestCases extends SpecBaseWithH2 with BeforeAndAfter {

  before {
    val createdSchema = for {
      connection <- Database(system).connect()
      create <- connection.executeUpdate("CREATE TABLE IF NOT EXISTS testTable " +
        "(id INT IDENTITY PRIMARY KEY, firstname VARCHAR(255), name VARCHAR(255) , bornInYear INT)")
      insert <- connection.executeUpdate("INSERT INTO testTable(firstname,name,bornInYear)" +
        " VALUES('Roman','Stoffel',1986)," +
        "('Joe','Average',1990)," +
        "('Jim','Fun',1984)," +
        "('Joanna','von Anwesome',1980)")
      closed <- connection.close()
    } yield closed
    Await.ready(createdSchema, 5 seconds)
  }

  after{
    val truncateTable = for {
      connection <- Database(system).connect()
      truncated <- connection.executeUpdate("TRUNCATE TABLE testTable")
      closed <- connection.close()
    } yield closed
    Await.ready(truncateTable, 5 seconds)

  }

  def selectAll() = for {
      connection <- Database(system).connect()
      result <- connection.executeQuery("SELECT * FROM testTable ORDER BY bornInYear DESC")
      closed <- connection.close()
    } yield result

  describe("Query Support") {
    it("can access by indexes"){
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(4)
      result(0)(1).getString must be("Joe")
      result(0)(2).getString must be("Average")
      result(0)(3).getLong must be(1990)
      result(1,1).getString must be("Roman")
      result(2,1).getString must be("Jim")
      result(3,1).getString must be("Joanna")
    }
    it("can access by row name"){
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(4)
      result(0)("firstname").getString must be("Joe")
      result(0)("name").getString must be("Average")
      result(0)("bornInYear").getLong must be(1990)
      result(1)("firstname").getLong must be("Roman")
      result(2)("firstname").getLong must be("Jim")
      result(2)("firstname").getLong must be("Joanna")
    }
    it("projection"){
      val resultFuture = for {
        connection <- Database(system).connect()
        result <- connection.executeQuery("SELECT name FROM testTable ORDER BY bornInYear DESC LIMIT 1")
        closed <- connection.close()
      } yield result

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(1)
      result(0)("name").getString must be("Average")
    }
    it("iterate over result is possible"){
        val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      var iteratedThroughResult = for{row <- result} yield row.get(1).getString

      assert(iteratedThroughResult.contains("Joe"))
      assert(iteratedThroughResult.contains("Roman"))
      assert(iteratedThroughResult.contains("Jim"))
      assert(iteratedThroughResult.contains("Joanna"))
    }
    it("iterate row"){
      val resultFuture = for {
        connection <- Database(system).connect()
        result <- connection.executeQuery("SELECT name, firstname, bornInYear FROM testTable ORDER BY bornInYear DESC LIMIT 1")
        closed <- connection.close()
      } yield result

      val result = Await.result(resultFuture, 5 seconds)
      var row = result.get(0)
      var iteratedThroughColums = for{column<-row} yield column.getString

      assert(iteratedThroughColums.contains("Joe"))
      assert(iteratedThroughColums.contains("Average"))
      assert(iteratedThroughColums.contains("1990"))
    }

  }

}
