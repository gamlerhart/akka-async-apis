package info.gamlor.db

import scala.Predef._
import org.adbcj._
import java.util.concurrent.atomic.AtomicInteger
import akka.dispatch.{Future, Promise, ExecutionContext}
import info.gamlor.db.DBResults._
import scala.PartialFunction

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DBConnection {

  def apply(connection: Connection)(implicit context: ExecutionContext): DBConnection = new DBConnection(connection, context)

}

/**
 * Represents a connection to the database.
 *
 *
 * @param connection the underlying connection
 * @param context execution context which is used for dispatching results
 */
class DBConnection(val connection: Connection, implicit val context: ExecutionContext) extends FutureConversions {
  private val runnintInTransactions = new AtomicInteger()

  /**
   * Runs a transaction with this connection. The transaction is committed when the future which the given operation
   * returns with a result. If the future carries an error, then the transaction is rolled back.
   * You can manually roll the transaction back by calling .rollback()
   *
   * If a transaction is already running, it will be reused.
   *
   *
   * @param operation the database operations to run
   * @tparam T return type
   */
  def withTransaction[T](operation: DBConnection => Future[T]): Future[T] = {
    if (!connection.isInTransaction) {
      connection.beginTransaction()
    }
    runnintInTransactions.incrementAndGet()
    val operationResult = try {
      operation(this)
    } catch {
      case anyError: Throwable => Promise.failed(anyError)
    }
    operationResult.flatMap(finishTransaction)
      .recoverWith[T]({
      case anyError: Throwable => failTransaction(anyError)
    })
  }


  private def finishTransaction[T](valueToReturn: T): Future[T] = {
    runnintInTransactions.decrementAndGet()
    if (connection.isInTransaction && runnintInTransactions.get() == 0) {
      commit().map(u => valueToReturn)
    } else {
      Promise.successful(valueToReturn)
    }
  }

  private def failTransaction[T](error: Throwable): Future[T] = {
    runnintInTransactions.decrementAndGet()
    if (connection.isInTransaction && runnintInTransactions.get() == 0) {
      rollback().flatMap(u => Promise.failed(error))
    } else {
      Promise.failed(error)
    }
  }

  def commit(): Future[Unit] = {
    completeWithAkkaFuture[Void, Unit](
      () => connection.commit(), ps => ())
  }

  def beginTransaction(): Future[Unit] = {
    connection.beginTransaction()
    Promise.successful[Unit]()
  }

  def rollback(): Future[Unit] = {
    completeWithAkkaFuture[Void, Unit](
      () => connection.rollback(), ps => ())
  }

  def isInTransaction(): Boolean = connection.isInTransaction

  def prepareQuery(sql: String): Future[DBPreparedQuery] = {
    completeWithAkkaFuture[PreparedQuery, DBPreparedQuery](
      () => connection.prepareQuery(sql), ps => new DBPreparedQuery(ps, context))
  }

  def prepareUpdate(sql: String): Future[DBPreparedUpdate] = {
    completeWithAkkaFuture[PreparedUpdate, DBPreparedUpdate](
      () => connection.prepareUpdate(sql), ps => new DBPreparedUpdate(ps, context))
  }


  def executeQuery(sql: String): Future[DBResultList] = {
    completeWithAkkaFuture[ResultSet, DBResultList](() => connection.executeQuery(sql), rs => new DBResultList(rs))
  }

  def executeQuery[T](sql: String,
                      eventHandler: ResultHandler[T],
                      accumulator: T): Future[T] = {
    completeWithAkkaFuture[T, T](() => connection.executeQuery(sql, eventHandler, accumulator), rs => rs)
  }

  def executeQuery[T](sql: String, initialValue: T)(eventHandler: PartialFunction[DBResultEvents[T], T]): Future[T] = {
    completeWithAkkaFuture[MutableRef[T], T](() => connection.executeQuery(sql,
      new PatternMatchResultHandler(eventHandler), new MutableRef(initialValue)), rs => rs.value)
  }

  def executeUpdate(sql: String): Future[DBResult] = {
    completeWithAkkaFuture[Result, DBResult](() => connection.executeUpdate(sql), rs => new DBResult(rs))
  }

  def close(): Future[Unit] = completeWithAkkaFuture[Void, Unit](() => connection.close(), _ => ())

  def isClosed = connection.isClosed

  def isOpen = connection.isOpen

}

private[db] class MutableRef[T](var value: T)

private[db] class PatternMatchResultHandler[T](pf: PartialFunction[DBResultEvents[T], T]) extends ResultHandler[MutableRef[T]] {
  def exception(t: Throwable, accumulator: MutableRef[T]) {
    processMessage(Error(t, accumulator.value), accumulator)
  }

  def startRow(accumulator: MutableRef[T]) {
    processMessage(StartRow(accumulator.value), accumulator)
  }

  def startFields(accumulator: MutableRef[T]) {
    processMessage(StartFields(accumulator.value), accumulator)
  }

  def endResults(accumulator: MutableRef[T]) {
    processMessage(EndResults(accumulator.value), accumulator)
  }

  def endFields(accumulator: MutableRef[T]) {
    processMessage(EndFields(accumulator.value), accumulator)
  }

  def value(value: Value, accumulator: MutableRef[T]) {
    processMessage(AValue(value, accumulator.value), accumulator)
  }

  def field(field: Field, accumulator: MutableRef[T]) {
    processMessage(AField(field, accumulator.value), accumulator)
  }

  def startResults(accumulator: MutableRef[T]) {
    processMessage(StartResults(accumulator.value), accumulator)
  }

  def endRow(accumulator: MutableRef[T]) {
    processMessage(EndRow(accumulator.value), accumulator)
  }


  private def processMessage(eventInfo: DBResults.DBResultEvents[T], accumulator: MutableRef[T]) {
    if (pf.isDefinedAt(eventInfo)) {
      accumulator.value = pf(eventInfo);
    }
  }
}

class DBPreparedQuery(statement: PreparedQuery, implicit val context: ExecutionContext) extends FutureConversions {
  def execute(params: Any*): Future[DBResultList] = {
    val boxed = params.map(v => v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[ResultSet, DBResultList](() => statement.execute(boxed: _*), rs => new DBResultList(rs))
  }

  def executeWithCallbackObject[T](eventHandler: ResultHandler[T],
                             accumulator: T, params: Any*): Future[T] = {
    val boxed = params.map(v => v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[T, T](() => statement.executeWithCallback(eventHandler, accumulator, boxed: _*), rs => rs)
  }

  def executeWithCallback[T](initialValue: T, params: Any*) (eventHandler: PartialFunction[DBResultEvents[T], T]) = {
    val boxed = params.map(v => v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[MutableRef[T], T](() => statement.executeWithCallback(new PatternMatchResultHandler[T](eventHandler),
      new MutableRef(initialValue), boxed: _*), rs => rs.value)
  }

  def close(): Future[Unit] = completeWithAkkaFuture[Void, Unit](() => statement.close(), _ => ())
}

class DBPreparedUpdate(statement: PreparedUpdate, implicit val context: ExecutionContext) extends FutureConversions {
  def execute(args: Any*): Future[DBResult] = {
    val boxed = args.map(v => v.asInstanceOf[AnyRef])
    completeWithAkkaFuture[Result, DBResult](() => statement.execute(boxed: _*), rs => new DBResult(rs))
  }

  def close(): Future[Unit] = completeWithAkkaFuture[Void, Unit](() => statement.close(), _ => ())
}