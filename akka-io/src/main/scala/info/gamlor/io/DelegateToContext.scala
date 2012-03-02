package info.gamlor.io

import akka.dispatch.{ExecutionContextExecutorService, ExecutionContext}
import java.util.concurrent.{Callable, TimeUnit}

/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

class DelegateToContext(private val context:ExecutionContext) extends ExecutionContextExecutorService{
  def reportFailure(t: Throwable) {
    context.reportFailure(t)

  }

  def execute(command: Runnable) {
    context.execute(command)
  }

  def shutdown() {notSupported()}

  def shutdownNow()=notSupported[java.util.List[Runnable]]()

  def isShutdown = false

  def isTerminated = false

  def awaitTermination(timeout: Long, unit: TimeUnit) = {notSupported()}

  def submit[T](task: Callable[T]) = {notSupported()}

  def submit[T](task: Runnable, result: T) = {notSupported()}

  def submit(task: Runnable) = {notSupported()}

  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]) = {notSupported()}

  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit) = {notSupported()}

  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]) = {notSupported()}

  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit) = {notSupported()}

  private def notSupported[T]():T = {
    throw new UnsupportedOperationException("This operation is not allowed")
  }
}
