package info.gamlor.db

import org.adbcj.{FutureState, DbListener, DbFuture}
import akka.dispatch.{ExecutionContext, Promise}
import java.util.concurrent.CancellationException

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
        future.getState match {
          case FutureState.SUCCESS =>{
            akkaPromise.success(transformation(future.getResult))
          }
          case FutureState.FAILURE =>{
            akkaPromise.failure(future.getException)
          }
          case FutureState.CANCELLED =>{
            akkaPromise.failure(new CancellationException("Operation was cancelled"))
          }
        }
      }
    })
    akkaPromise
  }

}
