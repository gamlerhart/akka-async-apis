package info.gamlor.db

import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.{ConfigException, ConfigFactory}

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

class DatabaseSettingsTestCase extends Spec with ShouldMatchers {

  describe("Database settings"){
    it("throws when not specified"){
      intercept[ConfigException.Missing]{
        DatabaseSettings(ConfigFactory.load())
      }
    }
    it("loads the configuration"){
      val cgf = DatabaseSettings(ConfigFactory.load("configReaderTest"))
      cgf.url should be ("url")
      cgf.userName should be ("username")
      cgf.passWord should be ("password")
    }
    it("adds to properties"){
      val cgf = DatabaseSettings(ConfigFactory.load("configReaderTest"))
      cgf.properties.get("pool.maxConnections") should be ("10")
      cgf.properties.get("otherSetting.turbo") should be ("ofCourse")
    }
  }

}
