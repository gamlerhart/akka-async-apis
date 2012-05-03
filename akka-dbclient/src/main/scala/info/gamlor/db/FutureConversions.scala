package info.gamlor.db

import org.adbcj.{DbListener, DbFuture}
import akka.dispatch.{ExecutionContext, Promise}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

trait FutureConversions {

  implicit def context:ExecutionContext

  protected def completeWithAkkaFuture[TOrignalData, TResult]
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
