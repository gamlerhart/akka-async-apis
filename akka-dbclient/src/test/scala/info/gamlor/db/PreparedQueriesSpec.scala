package info.gamlor.db

import org.scalatest.BeforeAndAfter
import akka.dispatch.Await
import akka.util.duration._
import org.adbcj.DbException

/**
 * @author roman.stoffel@gamlor.info
 * @since 03.05.12
 */

class PreparedQueriesSpec extends SpecBaseWithDB with BeforeAndAfter {

  describe("Prepared queries") {
    it("can run simple select") {
      var future = for {
        connection <- Database(system).connect()
        statement <- connection.prepareQuery("SELECT * FROM testTable WHERE bornInYear>?  ORDER BY bornInYear DESC")
        result <- statement.execute(1984)
        _ <- statement.close()
        _ <- connection.close()
      } yield result

      val result = Await.result(future, 5 seconds)

      result.size must be(2)
      result.get(0).get("bornInYear").getString must be("1990")
      result.get(1).get("bornInYear").getString must be("1986")

    }
    it("can insert, update and delete") {
      val dbOperationResult= for {
        connection <- Database(system).connect()
        insertStatment <- connection.prepareUpdate("INSERT INTO insertTable(data) VALUES(?)")
        updateStatment <- connection.prepareUpdate("UPDATE insertTable SET data=? WHERE id = ?")
        deleteStatement <- connection.prepareUpdate("DELETE FROM insertTable where id = ?")
        insertResult <- insertStatment.execute("newValue")
        updateResult <- updateStatment.execute("updatedValue", insertResult.generatedKeys(0, 0).getInt)
        selectBeforeDelete <- connection.executeQuery("SELECT data FROM insertTable")
        deleteResult <- deleteStatement.execute(insertResult.generatedKeys(0, 0).getInt)
        selectAfterDelete <- connection.executeQuery("SELECT data FROM insertTable")
        _ <- connection.close()
      } yield (insertResult.affectedRows,
          updateResult.affectedRows,
          deleteResult.affectedRows,
          selectBeforeDelete,
          selectAfterDelete)

      val (insertAmount, updateAmount, deleteAmount, selectBeforeDelete, selectAfterDelete)
        = Await.result(dbOperationResult, 5 seconds)

      insertAmount must be(1)
      updateAmount must be(1)
      deleteAmount must be(1)
      selectBeforeDelete(0,"data").getString must be("updatedValue")
      selectAfterDelete.size must be(0)

    }
    it("returns error on closed statements") {
      val future = for {
        connection <- Database(system).connect()
        statement <- connection.prepareQuery("SELECT * FROM testTable WHERE bornInYear>?  ORDER BY bornInYear DESC")
        _ <- statement.close()
        result <- statement.execute(1984)
        _ <- connection.close()
      } yield result

      intercept[DbException](Await.result(future, 5 seconds))

    }
  }


}
