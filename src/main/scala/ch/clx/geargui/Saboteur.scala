package ch.clx.geargui

import akka.actor._

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