package ch.clx.geargui

import akka.actor._
import akka.util.Timeout
import concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import scala.language.postfixOps

class Saboteur() extends Actor {

  def receive = {
    case SabotageRandomFrom(gears) => {
      val nOfGears = gears.length
      //TOOD Handle isTerminated
      val sabotageList = (0 until nOfGears).map(i => gears(scala.util.Random.nextInt(nOfGears)))
      sabotageList.foreach { _ ! Interrupt(scala.util.Random.nextInt(1000))
      }
    }
    case SabotageManualFrom(list, ref, toSpeed) => {
      list.find(_.actorRef.path.toString == ref) match {
        //TOOD Handle isTerminated
        case Some(gear) => gear ! Interrupt(toSpeed)
        case _ => ()
      }
    }
  }
}