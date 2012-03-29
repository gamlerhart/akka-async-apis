package info.gamlor.db

import org.adbcj.{DbFuture, DbListener, Connection, ResultSet}
import akka.dispatch.{ExecutionContext, Promise, Future}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection:Connection)(implicit context:ExecutionContext):DBConnection = new DBConnection(connection, context)

}

class DBConnection(val connection:Connection, private implicit val context:ExecutionContext) {

  def executeQuery(sql:String) : Future[ResultSet] = {
    val resultPromise = Promise[ResultSet]
    connection.executeQuery(sql).addListener(new DbListener[ResultSet] {
      def onCompletion(future: DbFuture[ResultSet]) {
        resultPromise.success(future.get())
      }
    })
    resultPromise
  }

}
