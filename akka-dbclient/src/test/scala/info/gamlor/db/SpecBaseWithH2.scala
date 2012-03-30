package info.gamlor.db

import akka.testkit.TestKit
import org.scalatest.Spec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem

/**
 * @author roman.stoffel@gamlor.info
 * @since 29.03.12
 */
object SpecBaseWithH2 {

  val SystemWithH2 = ActorSystem("TestSystem", ConfigFactory.load("embeddedH2"))
}

class SpecBaseWithH2
  extends TestKit(SpecBaseWithH2.SystemWithH2)
  with Spec
  with MustMatchers
  with ShouldMatchers {

  Class.forName("org.h2.Driver")

}
