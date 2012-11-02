package info.gamlor.db

import com.typesafe.config.Config
import java.util
import scala.collection.JavaConversions._

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */

object DatabaseSettings {
  def apply(akkaConf:Config) = new DatabaseSettings(akkaConf)


  private def convertToMap(config: Config): util.Map[String, String] ={
    val adbcjConfig=config.getConfig("async-jdbc")
    val map = new util.HashMap[String,String]()
    for (entry<-adbcjConfig.entrySet()){
      map.put(entry.getKey,entry.getValue.unwrapped().toString)
    }
    java.util.Collections.unmodifiableMap(map)
  }
}

class DatabaseSettings(val url:String,
                       val userName:String,
                       val passWord:String,
                       val properties:util.Map[String,String]) {


  def this(akkaConf:Config) = this(
    akkaConf.getString("async-jdbc.url"),
    akkaConf.getString("async-jdbc.username"),
    akkaConf.getString("async-jdbc.password"),DatabaseSettings.convertToMap(akkaConf))




}
