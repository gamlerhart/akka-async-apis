package info.gamlor.db

import akka.actor.{ExtendedActorSystem, Extension, ExtensionIdProvider, ExtensionId}
import org.adbcj._
import akka.dispatch.{Promise, Future, ExecutionContext}
import collection.immutable.HashMap
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections
import java.util.concurrent.Executors

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object Database
  extends ExtensionId[DatabaseAccess]
  with ExtensionIdProvider {
  override def lookup = this

  override def createExtension(system: ExtendedActorSystem) = {
    val config = DatabaseSettings(system.settings.config)
    val connectionManager =
      ConnectionManagerProvider.createConnectionManager(config.url,
        config.userName,
        config.passWord,
        java.util.Collections.emptyMap(),
        new info.gamlor.io.DelegateToContext(system.dispatcher))
    val client = new DatabaseAccess(connectionManager, system.dispatcher)
    client
  }

}

class DatabaseAccess(val connectionManager: ConnectionManager,
                     implicit val context: ExecutionContext
                      ) extends Extension with FutureConversions {
  def withConnection[T](operation: DBConnection => Future[T]): Future[T] = {
    connect().flatMap(conn => {
      val operationFuture = try {
        operation(conn)
      } catch {
        case error: Throwable => {
          conn.close().flatMap(u => Promise.failed(error))
        }
      }
      operationFuture
        .flatMap(result => conn.close().map(_ => result))
        .recoverWith({
        case error: Throwable => {
          conn.close().flatMap(u => Promise.failed(error))
        }
      })
    }

    )
  }


  /**
   * Connects to the database and returns the connection in a future
   *
   * In case of a failure the closure finishes with a [[org.adbcj.DbException]]
   * @return future which completes with the connection or a [[org.adbcj.DbException]]
   */
  def connect(): Future[DBConnection] = {
    completeWithAkkaFuture[Connection, DBConnection](() => connectionManager.connect(), c => DBConnection(c))
  }

}
