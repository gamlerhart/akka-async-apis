package info.gamlor.bench

import akka.actor.ActorSystem

/**
 * @author roman.stoffel@gamlor.info
 */

trait RequestFullfiller {

  def system:ActorSystem

}
