package info.gamlor.io

import akka.testkit.TestKit
import org.scalatest.Spec
import org.scalatest.matchers.{ShouldMatchers, MustMatchers}

/**
 * @author roman.stoffel@gamlor.info
 * @since 02.03.12
 */

abstract class SpecBase
   extends TestKit(TestActorSystem.DefaultSystem)
   with Spec
   with MustMatchers
   with ShouldMatchers{

}
