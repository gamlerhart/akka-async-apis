package info.gamlor.db

import akka.util.duration._
import akka.dispatch.{Promise, Await}

/**
 * @author roman.stoffel@gamlor.info
 * @since 11.05.12
 */

class TransactionSpec extends SpecBaseWithDB {

  describe("Trasaction Support") {
    it("can rollback transcation") {
      val dbOperationResult = for {
        connection <- Database(system).connect()
        txBeforeBeginTx <- Promise.successful(connection.isInTransaction)
        _ <- connection.beginTransaction()
        txAfterBeginTx <- Promise.successful(connection.isInTransaction)
        _ <- connection.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsRollback')")
        _ <- connection.rollback()
        dataAfterRollback <- connection.executeQuery("SELECT * FROM insertTable WHERE data LIKE 'transactionsRollback'")
        _ <- connection.close()
      } yield (txBeforeBeginTx, txAfterBeginTx, dataAfterRollback)

      val (txBeforeBeginTx, txAfterBeginTx, dataAfterRollback) = Await.result(dbOperationResult, 5 seconds)


      txBeforeBeginTx must be(false)
      txAfterBeginTx must be(true)
      dataAfterRollback.size must be(0)
    }
    it("can commit transcation") {
      val dbOperationResult = for {
        connection <- Database(system).connect()
        _ <- connection.beginTransaction()
        _ <- connection.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")
        _ <- connection.commit()
        data <- connection.executeQuery("SELECT * FROM insertTable WHERE data LIKE 'transactionsCommit';")
        _ <- connection.close()
      } yield (data)

      val (dataAfterCommit) = Await.result(dbOperationResult, 5 seconds)


      dataAfterCommit.size must be(1)
    }

  }
  describe("The withTransaction operations") {
    it("commits transaction") {
      val con = Await.result(Database(system).connect(), 5 seconds)
      val dataFuture =
        con.withTransaction {
          tx =>
            val selectedData = for {
              _ <- tx.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")
              data <- tx.executeQuery("SELECT * FROM insertTable WHERE data LIKE 'transactionsCommit';")

            } yield data
            selectedData
        }
      val data = Await.result(dataFuture, 5 seconds)

      data.size must be(1)
      con.isInTransaction() must be(false)

      val hasCommitted =  Await.result(con.executeQuery("SELECT * FROM insertTable" +
        " WHERE data LIKE 'transactionsCommit';"), 5 seconds)


      hasCommitted.size must be(1)
    }
    it("rollbacks transaction") {
      val con = Await.result(Database(system).connect(), 5 seconds)
      val dataFuture =
        con.withTransaction {
          tx =>
            val selectedData = for {
              _ <- tx.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")
              _ <- tx.rollback()

            } yield ""
            selectedData
        }
      Await.result(dataFuture, 5 seconds)
      con.isInTransaction() must be(false)

      val hasNotCommitted =  Await.result(con.executeQuery("SELECT * FROM insertTable" +
        " WHERE data LIKE 'transactionsCommit';"), 5 seconds)

      hasNotCommitted.size must be(0)
    }
    it("can nest transaction") {
      val con = Await.result(Database(system).connect(), 5 seconds)
      val dataFuture =
        con.withTransaction {
          tx =>
            val stillInTransaction = for {
              _ <- tx.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")
              _ <- nestedInsert(tx)
              stillInTransaction <- Promise.successful(tx.isInTransaction())
              _ <- tx.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")

            } yield stillInTransaction
            stillInTransaction
        }
      val stillInTransaction = Await.result(dataFuture, 5 seconds)
      stillInTransaction should be (true)
      val hasCommitted =  Await.result(con.executeQuery("SELECT * FROM insertTable" +
        " WHERE data LIKE 'transactionsCommit';"), 5 seconds)
      hasCommitted.size must be(3)

    }

  }


  private def nestedInsert(connection:DBConnection) = connection.withTransaction {
    tx =>tx.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsCommit')")
  }


}
