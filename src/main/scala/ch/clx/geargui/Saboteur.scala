package ch.clx.geargui

/**
 * Created by IntelliJ IDEA.
 * User: pmei
 * Date: 12.02.2010
 * Time: 10:32:20
 * Package: ch.clx.geargui
 * Class: Saboteur
 */

import collection.mutable.ListBuffer
import se.scalablesolutions.akka.actor.{ActorRef, Actor}

class Saboteur extends Actor {
  def receive = {
    case Sabotage(nGears: List[ActorRef]) => {
      nGears.foreach {
        e =>
          e ! Interrupt(scala.util.Random.nextInt(1000))
      }
    }
    case SabotageManual(gear: ActorRef, toSpeed: Int) => {
      gear ! Interrupt(toSpeed)
    }
  }
}