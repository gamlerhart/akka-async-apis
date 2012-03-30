package info.gamlor.db

import org.adbcj.ResultSet


/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class DBResultList(val resultSet: ResultSet) extends Seq[DBResultRow] {
  def length = resultSet.size()

  def apply(idx: Int) = new DBResultRow(resultSet.get(0))

  def get(index:Int) = new DBResultRow(resultSet.get(0))

  def iterator = {
    val iter = resultSet.iterator()
    new Iterator[DBResultRow] {
      def hasNext = iter.hasNext

      def next() = new DBResultRow(iter.next())
    }
  }
}
