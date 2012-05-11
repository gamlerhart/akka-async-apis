package info.gamlor.db

import akka.util.duration._
import akka.dispatch.{Promise, Await}

/**
 * @author roman.stoffel@gamlor.info
 * @since 11.05.12
 */

class TransactionSpec extends SpecBaseWithDB{

  describe("Trasaction Support") {
    it("can rollback transcation"){
      val dbOperationResult= for {
        connection <- Database(system).connect()
        txBeforeBeginTx <- Promise.successful(connection.isInTransaction)
        _ <- connection.beginTransaction()
        txAfterBeginTx <-  Promise.successful(connection.isInTransaction)
        _ <- connection.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsRollback')")
        _ <- connection.rollback()
        dataAfterRollback <- connection.executeQuery("SELECT * FROM insertTable WHERE data LIKE 'transactionsRollback'")
        _ <- connection.close()
      } yield (txBeforeBeginTx,txAfterBeginTx,dataAfterRollback)

      val (txBeforeBeginTx,txAfterBeginTx,dataAfterRollback) = Await.result(dbOperationResult,5 seconds)


      txBeforeBeginTx must be(false)
      txAfterBeginTx must be(true)
      dataAfterRollback.size must be(0)
    }
    it("can commit transcation"){
      val dbOperationResult= for {
        connection <- Database(system).connect()
        _ <- connection.beginTransaction()
        _ <- connection.executeUpdate("INSERT INTO insertTable(data) VALUES('transactionsRollback')")
        _ <- connection.commit()
        dataAfterRollback <- connection.executeQuery("SELECT * FROM insertTable WHERE data LIKE 'transactionsRollback'")
        _ <- connection.executeUpdate("DELETE FROM insertTable WHERE data LIKE 'transactionsRollback'")
        _ <- connection.close()
      } yield (dataAfterRollback)

      val dataAfterCommit = Await.result(dbOperationResult,5 seconds)


      dataAfterCommit.size must be(1)
    }
  }

}
