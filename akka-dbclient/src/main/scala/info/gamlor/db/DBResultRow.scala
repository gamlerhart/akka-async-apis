package info.gamlor.db

import org.adbcj.{Value, Row}


/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class DBResultRow(val row: Row) {
  def get(index: Int):Value = row.get(index)
  def apply(index: Int):Value = row.get(index)

  def iterator = null

}
