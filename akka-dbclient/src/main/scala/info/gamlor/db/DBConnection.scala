package info.gamlor.db

import scala.Predef._
import akka.dispatch.{ExecutionContext, Future}
import org.adbcj.{PreparedStatement, Result, Connection, ResultSet}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection:Connection)(implicit context:ExecutionContext):DBConnection = new DBConnection(connection, context)

}

class DBConnection(val connection:Connection, implicit val context:ExecutionContext) extends FutureConversions{
  def prepareStatement(sql: String) : Future[DBPreparedStatement] ={
    completeWithAkkaFuture[PreparedStatement,DBPreparedStatement](()=>connection.prepareStatement(sql),ps=>new DBPreparedStatement(ps,context))
  }


  def executeQuery(sql:String) : Future[DBResultList] = {
    completeWithAkkaFuture[ResultSet,DBResultList](()=>connection.executeQuery(sql),rs=>new DBResultList(rs))
  }

  def executeUpdate(sql: String) :Future[Result] ={
    completeWithAkkaFuture[Result,Result](()=>connection.executeUpdate(sql),rs=>rs)
  }

  def close():Future[Unit] =completeWithAkkaFuture[Void,Unit](()=>connection.close(),_=>())

  def isClosed = connection.isClosed
}

class DBPreparedStatement(statement:PreparedStatement, implicit val context:ExecutionContext) extends FutureConversions{
  def execute(args:Any*):Future[DBResultList] ={
    val boxed = args.map(v=>v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[ResultSet,DBResultList](()=>statement.executeQuery(boxed:_*),rs=>new DBResultList(rs))
  }
}