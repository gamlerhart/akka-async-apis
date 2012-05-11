package info.gamlor.db

import scala.Predef._
import org.adbcj._
import akka.dispatch.{Promise, ExecutionContext, Future}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection:Connection)(implicit context:ExecutionContext):DBConnection = new DBConnection(connection, context)

}

class DBConnection(val connection:Connection, implicit val context:ExecutionContext) extends FutureConversions{
  def commit() : Future[Unit] ={
      connection.commit()
      Promise.successful[Unit]()
    }

  def beginTransaction() : Future[Unit] ={
    connection.beginTransaction()
    Promise.successful[Unit]()
  }

  def rollback() : Future[Unit] ={
    connection.rollback()
    Promise.successful[Unit]()
  }

  def isInTransaction() : Boolean = connection.isInTransaction

  def prepareQuery(sql: String) : Future[DBPreparedQuery] ={
    completeWithAkkaFuture[PreparedQuery,DBPreparedQuery](
      ()=>connection.prepareQuery(sql),ps=>new DBPreparedQuery(ps,context))
  }
  def prepareUpdate(sql: String) : Future[DBPreparedUpdate] ={
    completeWithAkkaFuture[PreparedUpdate,DBPreparedUpdate](
      ()=>connection.prepareUpdate(sql),ps=>new DBPreparedUpdate(ps,context))
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

class DBPreparedQuery(statement:PreparedQuery, implicit val context:ExecutionContext) extends FutureConversions{
  def execute(args:Any*):Future[DBResultList] ={
    val boxed = args.map(v=>v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[ResultSet,DBResultList](()=>statement.execute(boxed:_*),rs=>new DBResultList(rs))
  }

  def close():Future[Unit] =completeWithAkkaFuture[Void,Unit](()=>statement.close(),_=>())
}
class DBPreparedUpdate(statement:PreparedUpdate, implicit val context:ExecutionContext) extends FutureConversions{
  def execute(args:Any*):Future[DBResult] ={
    val boxed = args.map(v=>v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[Result,DBResult](()=>statement.execute(boxed:_*),rs=>new DBResult(rs))
  }

  def close():Future[Unit] =completeWithAkkaFuture[Void,Unit](()=>statement.close(),_=>())
}