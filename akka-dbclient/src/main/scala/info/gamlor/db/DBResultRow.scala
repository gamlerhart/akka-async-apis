package info.gamlor.db

import org.adbcj.{Value, Row}


/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class DBResultRow(val row: Row, resultList:DBResultList) {
  def get(index: Int):Value = row.get(index)
  def get(columnName: String):Value = row.get(columnName)
  def apply(columnName: String):Value = row.get(resultList.fieldByName(columnName.toLowerCase())
    .getOrElse(throw new IllegalArgumentException("The field with the name "+columnName+" does not exist")))
  def apply(index: Int):Value = row.get(index)

  def iterator = null

}
