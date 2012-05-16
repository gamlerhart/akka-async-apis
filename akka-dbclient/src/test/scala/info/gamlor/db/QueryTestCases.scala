package info.gamlor.db

import akka.dispatch.Await
import akka.util.duration._
import org.adbcj.{Value, AbstractResultHandler}
import info.gamlor.db.DBResults._

/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class QueryTestCases extends SpecBaseWithDB {


  def selectAll() = for {
    connection <- Database(system).connect()
    result <- connection.executeQuery("SELECT * FROM testTable ORDER BY bornInYear DESC")
    closed <- connection.close()
  } yield result

  describe("Query Support") {
    it("can access by indexes") {
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(4)
      result(0)(1).getString must be("Joe")
      result(0)(2).getString must be("Average")
      result(0)(3).getLong must be(1990)
      result(1, 1).getString must be("Roman")
      result(2, 1).getString must be("Jim")
      result(3, 1).getString must be("Joanna")
    }
    it("can access by row name") {
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(4)
      result(0)("firstname").getString must be("Joe")
      result(0)("name").getString must be("Average")
      result(0)("bornInYear").getLong must be(1990)
      result(1)("firstname").getString must be("Roman")
      result(2)("firstname").getString must be("Jim")
      result(3)("firstname").getString must be("Joanna")
      result(3, "firstname").getString must be("Joanna")
    }
    it("projection") {
      val resultFuture = for {
        connection <- Database(system).connect()
        result <- connection.executeQuery("SELECT name FROM testTable ORDER BY bornInYear DESC LIMIT 1")
        closed <- connection.close()
      } yield result

      val result = Await.result(resultFuture, 5 seconds)
      result.size must be(1)
      result(0)("name").getString must be("Average")
    }
    it("iterate over result is possible") {
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      var iteratedThroughResult = for {row <- result} yield row.get(1).getString

      assert(iteratedThroughResult.contains("Joe"))
      assert(iteratedThroughResult.contains("Roman"))
      assert(iteratedThroughResult.contains("Jim"))
      assert(iteratedThroughResult.contains("Joanna"))
    }
    it("iterate row") {
      val resultFuture = for {
        connection <- Database(system).connect()
        result <- connection.executeQuery("SELECT name, firstname, bornInYear FROM testTable ORDER BY bornInYear DESC LIMIT 1")
        closed <- connection.close()
      } yield result

      val result = Await.result(resultFuture, 5 seconds)
      var row = result.get(0)
      var iteratedThroughColumns = for {column <- row} yield column.getString

      assert(iteratedThroughColumns.contains("Joe"))
      assert(iteratedThroughColumns.contains("Average"))
      assert(iteratedThroughColumns.contains("1990"))
    }

    it("get fields") {
      val resultFuture = selectAll()

      val result = Await.result(resultFuture, 5 seconds)
      result.fields.size must be(4)



      result.get(0)(result.fields(1)).getString must be("Joe")
      result.get(0)(result.fields(2)).getString must be("Average")
    }
    it("can query for spefic data") {
      val resultFuture = for {connection <- Database(system).connect()
           result <- connection.executeQuery("SELECT * FROM testTable " +
             "WHERE bornInYear>1984  ORDER BY bornInYear DESC")
           closed <- connection.close()
      } yield result
      val result = Await.result(resultFuture, 5 seconds)

      result.size must be(2)
      result.get(0).get("bornInYear").getString must be("1990")
      result.get(1).get("bornInYear").getString must be("1986")

    }
    it("can use java like callback class") {
      val resultFuture = for {connection <- Database(system).connect()
           result <- connection.executeQuery("SELECT firstname FROM testTable WHERE firstname LIKE 'Roman'",
             new AbstractResultHandler[StringBuilder] {
             override def value(value: Value, accumulator: StringBuilder) {
               accumulator.append(value.getString)
             }
           }, new StringBuilder())
           closed <- connection.close()
      } yield result
      val result = Await.result(resultFuture, 5 seconds)

      result.toString must be("Roman")

    }
    it("can use callback with pattern matching") {
      val resultFuture = for {connection <- Database(system).connect()
           result <- connection.executeQuery("SELECT firstname FROM testTable WHERE firstname LIKE 'Roman'",""){
             case StartFields(data) =>data + "StartFields-"
             case AField(fieldInfo,data) =>data +"AField(" + fieldInfo.getColumnLabel.toLowerCase + ")-"
             case EndFields(data) =>data + "EndField-"
             case StartResults(data) =>data + "StartResults-"
             case StartRow(data) =>data + "StartRow-"
             case AValue(value,data) =>data +"AValue(" + value.getString + ")-"
             case EndRow(data) =>data +"EndRow-"
             case EndResults(data) =>data +"EndResults"
           }
           closed <- connection.close()
      } yield result
      val result = Await.result(resultFuture, 5 seconds)

      result.toString must be("StartFields-AField(firstname)-EndField-StartResults-StartRow-AValue(Roman)-EndRow-EndResults")

    }

  }


}
