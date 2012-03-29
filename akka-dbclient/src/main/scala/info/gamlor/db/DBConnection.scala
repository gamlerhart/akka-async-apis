package info.gamlor.db

import org.adbcj.{Connection, ResultSet}
import akka.dispatch.{ExecutionContext, Future}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection:Connection)(implicit context:ExecutionContext):DBConnection = new DBConnection(connection, context)

}

class DBConnection(val connection:Connection, implicit val context:ExecutionContext) extends FutureConversions{

  def executeQuery(sql:String) : Future[ResultSet] = {
    completeWithAkkaFuture[ResultSet,ResultSet](()=>connection.executeQuery(sql),rs=>rs)
  }

}
