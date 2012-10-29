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

/**
 * Directly provides the configured database access. You can directly use it like this:
 * <pre>
 *   val akkaSystem = ... // Your akka system
 *   // example
 *   Database(akkaSystem).withConnection{
 *      connection =>{
 *        // do things
 *      }
 *   }
 *   // get connection more permanently
 *   val connection = Database(akkaSystem)
 * </pre>
 *
 * In case you don't want to use the Actor System's global database,
 * you can create a fresh instance by yourself:
 * <pre>
 * val connectionManager =
 *    ConnectionManagerProvider.createConnectionManager(config.url, config.userName, config.passWord)
 * implicit val executionContext = ... // Your akka / futures execution context
 *
 * val dbInstance = Database.createDatabaseAccessObject(connectionManager);
 *
 * </pre>
 *
 *
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
        java.util.Collections.emptyMap())
    val client = new DatabaseAccess(connectionManager, system.dispatcher)
    client
  }

  /**
   * Create a complete new instance for database access,
   * with the given connectionManager and context
   * @param connectionManager source for new connections
   * @param context  dispatcher context for the returned futures
   * @return database access
   */
  def createDatabaseAccessObject(connectionManager: ConnectionManager)(implicit context: ExecutionContext)={
    new DatabaseAccess(connectionManager,context)
  }

}

/**
 * Start interface for working with the database.
 *
 * Use withConnection() to run a few operations and close that connection
 * when the returned future is completed.
 *
 * Use connect() to get a regular database connection
 * @param connectionManager source for new connections
 * @param context  dispatcher context for the returned futures
 */
class DatabaseAccess(val connectionManager: ConnectionManager,
                     implicit val context: ExecutionContext
                      ) extends Extension with FutureConversions {
/**
 * Opens a connection to the database and runs the code of the given closure with it. It will close the connection
 * when the future which the closure returns finishes.
 *
 * This is intended for doing multiple read operations and then close the file: for example:
 * <pre>Database(akkaSystem).withConnection{
 *      connection=>{
 *       connection.executeQuery("SELECT first_name,last_name,hire_date FROM employees")
 *        }
 *     }
 *    }</pre>
 * @param operation the closure to execute
 */
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
