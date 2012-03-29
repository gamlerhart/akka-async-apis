package info.gamlor.db

import com.typesafe.config.Config

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DatabaseSettings {
  def apply(akkaConf:Config) = new DatabaseSettings(akkaConf)


}

class DatabaseSettings(val url:String,
                       val userName:String,
                       val passWord:String) {

  def this(akkaConf:Config) = this(
    akkaConf.getString("async-jdbc.url"),
    akkaConf.getString("async-jdbc.username"),
    akkaConf.getString("async-jdbc.password"))


}
