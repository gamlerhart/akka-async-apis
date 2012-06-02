package info.gamlor.io

import java.util.concurrent.{Callable, TimeUnit}
import java.util.Collections
import akka.dispatch._
import akka.util.duration._
import akka.util.Duration

/**
 * @author roman.stoffel@gamlor.info
 * @since 01.03.12
 */

private[gamlor] class DelegateToContext(private val context:ExecutionContext) extends ExecutionContextExecutorService{
  @volatile private var closed = false;
  if(null==context){
    throw new IllegalArgumentException("A execution context is required")
  }

  def reportFailure(t: Throwable) {
    context.reportFailure(t)
  }

  def execute(command: Runnable) {
    context.execute(command)
  }

  def shutdown() {
    closed =true
  }

  def shutdownNow()={
    closed =true
    Collections.emptyList()
  }

  def isShutdown = closed

  def isTerminated = closed

  def awaitTermination(timeout: Long, unit: TimeUnit) = {notSupported()}

  def submit[T](task: Callable[T])  = {
    val future = Future[T]{
      task.call();
    }(context)
    convertToJavaFuture(future)
  }

  def submit[T](task: Runnable, result: T) = {notSupported()}

  def submit(task: Runnable) = {
    val future = Future[Unit]{
      task.run();
    }(context)
    convertToJavaFuture(future)
  }


  def convertToJavaFuture[T](future: Future[T]): java.util.concurrent.Future[T] =
    new java.util.concurrent.Future[T] {
      def cancel(mayInterruptIfRunning: Boolean) = false

      def isCancelled = false

      def isDone = future.isCompleted

      def get() = Await.result(future, 30 seconds)

      def get(timeout: Long, unit: TimeUnit) = Await.result(future, Duration(timeout, unit))
    }

  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]) = {notSupported()}

  def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit) = {notSupported()}

  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]) = {notSupported()}

  def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit) = {notSupported()}

  private def notSupported[T]():T = {
    throw new UnsupportedOperationException("This operation is not allowed")
  }
}
