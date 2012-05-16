package info.gamlor.db

import org.adbcj.{Field, Value}


/**
 * @author roman.stoffel@gamlor.info
 * @since 16.05.12
 */

object DBResults {

  sealed trait DBResultEvents[T]

  case class StartFields[T](prevousValue:T) extends DBResultEvents[T]
  case class AField[T](fieldInfo: Field,prevousValue:T) extends DBResultEvents[T]
  case class EndFields[T](prevousValue:T) extends DBResultEvents[T]
  case class StartResults[T](prevousValue:T) extends DBResultEvents[T]
  case class StartRow[T](prevousValue:T) extends DBResultEvents[T]
  case class AValue[T](value:Value,prevousValue:T) extends DBResultEvents[T]
  case class EndRow[T](prevousValue:T) extends DBResultEvents[T]
  case class EndResults[T](prevousValue:T) extends DBResultEvents[T]
  case class Error[T](exception:Throwable,prevousValue:T) extends DBResultEvents[T]

}
