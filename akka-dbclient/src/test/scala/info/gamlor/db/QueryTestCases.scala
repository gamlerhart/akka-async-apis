package info.gamlor.db

import org.scalatest.BeforeAndAfter

/**
 * @author roman.stoffel@gamlor.info
 * @since 30.03.12
 */

class QueryTestCases extends SpecBaseWithH2 with BeforeAndAfter {

  before {
    for {
      connection <- Database(system).connect()
      create <- connection.executeUpdate("CREATE TABLE IF NOT EXISTS testTable " +
        "(id INT IDENTITY PRIMARY KEY, name VARCHAR(255), firstname VARCHAR(255), bornInYear INT)")
      closed <- connection.close()
    };
  }

  describe("") {

  }

}
