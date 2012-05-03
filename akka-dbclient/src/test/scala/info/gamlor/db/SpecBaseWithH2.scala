package info.gamlor.db

import akka.testkit.TestKit
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.dispatch.Await
import org.scalatest.{BeforeAndAfter, Spec}
import akka.util.duration._

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */
object SpecBaseWithDB {

  val SystemWithH2 = ActorSystem("TestSystem", ConfigFactory.load("embeddedH2"))
}

class SpecBaseWithDB
  extends TestKit(SpecBaseWithDB.SystemWithH2)
  with Spec
  with BeforeAndAfter
  with MustMatchers
  with ShouldMatchers {

  Class.forName("org.h2.Driver")

  before {
      val createdSchema = for {
        connection <- Database(system).connect()
        create <- connection.executeUpdate("CREATE TABLE IF NOT EXISTS testTable " +
          "(id INT IDENTITY PRIMARY KEY, firstname VARCHAR(255), name VARCHAR(255) , bornInYear INT)")
        insert <- connection.executeUpdate("INSERT INTO testTable(firstname,name,bornInYear)" +
          " VALUES('Roman','Stoffel',1986)," +
          "('Joe','Average',1990)," +
          "('Jim','Fun',1984)," +
          "('Joanna','von Anwesome',1980)")
        closed <- connection.close()
      } yield closed
      Await.ready(createdSchema, 5 seconds)
    }

    after{
      val truncateTable = for {
        connection <- Database(system).connect()
        truncated <- connection.executeUpdate("TRUNCATE TABLE testTable")
        closed <- connection.close()
      } yield closed
      Await.ready(truncateTable, 5 seconds)
    }

}
