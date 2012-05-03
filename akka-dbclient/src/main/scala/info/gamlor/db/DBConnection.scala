package info.gamlor.db

import akka.dispatch.{ExecutionContext, Future}
import org.adbcj.{Result, Connection, ResultSet}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection:Connection)(implicit context:ExecutionContext):DBConnection = new DBConnection(connection, context)

}

class DBConnection(val connection:Connection, implicit val context:ExecutionContext) extends FutureConversions{

  def executeQuery(sql:String) : Future[DBResultList] = {
    completeWithAkkaFuture[ResultSet,DBResultList](()=>connection.executeQuery(sql),rs=>new DBResultList(rs))
  }

  def executeUpdate(sql: String) :Future[Result] ={
    completeWithAkkaFuture[Result,Result](()=>connection.executeUpdate(sql),rs=>rs)
  }

  def close():Future[Unit] =completeWithAkkaFuture[Void,Unit](()=>connection.close(),_=>())

  def isClosed = connection.isClosed
}
