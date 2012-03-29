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
    val client = new DatabaseAccess(connectionManager,system.dispatcher)
    client
  }

}

class DatabaseAccess(val connectionManager: ConnectionManager,
                     private implicit val context: ExecutionContext) extends Extension {
  def connect(): Future[DBConnection] = {
    val akkaPromise = Promise[DBConnection]
    connectionManager.connect().addListener(new DbListener[Connection] {
      def onCompletion(future: DbFuture[Connection]) {
        akkaPromise.success(DBConnection(future.get()))
      }
    })
    akkaPromise
  }

}
