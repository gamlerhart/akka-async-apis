package info.gamlor.db

import akka.actor.{ExtendedActorSystem, Extension, ExtensionIdProvider, ExtensionId}
import akka.dispatch.{Promise, Future, ExecutionContext}
import org.adbcj._

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
      ConnectionManagerProvider.createConnectionManager(config.url, config.userName, config.passWord)
    val client = new DatabaseAccess(connectionManager, system.dispatcher)
    client
  }

}

class DatabaseAccess(val connectionManager: ConnectionManager,
                     private implicit val context: ExecutionContext) extends Extension {


  /**
   * Connects to the database and returns the connection in a future
   *
   * In case of a failure the closure finishes with a [[org.adbcj.DbException]]
   * @return future which completes with the connection or a [[org.adbcj.DbException]]
   */
  def connect(): Future[DBConnection] = {
    completeWithAkkaFuture[Connection,DBConnection](() => connectionManager.connect(), c => DBConnection(c))
  }

  private def completeWithAkkaFuture[TOrignalData,TResult]
  (futureProducingOperation: () => DbFuture[TOrignalData], transformation: TOrignalData => TResult) = {
    val akkaPromise = Promise[TResult]
    futureProducingOperation().addListener(new DbListener[TOrignalData] {
      def onCompletion(future: DbFuture[TOrignalData]) {
        try {
          akkaPromise.success(transformation(future.get()))
        } catch {
          case ex: Throwable => akkaPromise.failure(ex)
        }
      }
    })
    akkaPromise
  }

}
